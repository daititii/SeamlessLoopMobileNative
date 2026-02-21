#include "AudioEngine.h"
#include <cstring>

AudioEngine::AudioEngine() {
    mDecoder = std::make_unique<AudioDecoder>();
    mFifo.resize(kFifoSize);
    mIsDecoding = true;
    mDecodingThread = std::thread(&AudioEngine::decodingLoop, this);
}

AudioEngine::~AudioEngine() {
    mIsDecoding = false;
    mFifoCond.notify_all();
    if (mDecodingThread.joinable()) {
        mDecodingThread.join();
    }
    stop();
}

bool AudioEngine::start() {
    if (mStream) return true;

    oboe::AudioStreamBuilder builder;
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(mChannelCount);
    builder.setSampleRate(mSampleRate);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setCallback(this);

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open Oboe stream: %s", oboe::convertToText(result));
        return false;
    }

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGD("Audio stream started successfully");
    return true;
}

void AudioEngine::stop() {
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }
    // 不要在这里彻底 close decoder，让后台线程处理
}

void AudioEngine::pause() {
    if (mStream) {
        mStream->requestPause();
    }
}

void AudioEngine::resume() {
    if (mStream) {
        mStream->requestStart();
    }
}

void AudioEngine::loadAudioSource(int fd, int64_t offset, int64_t length) {
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    if (mDecoder->open(fd, offset, length)) {
        mSampleRate = mDecoder->getSampleRate();
        mChannelCount = mDecoder->getChannelCount();
        mCurrentReadFrame = 0;
        mLoopStartFrame = 0;
        mLoopEndFrame = mDecoder->getTotalFrames();
        mIsLooping = true;
        
        resetFifo();
        mFifoCond.notify_all();
        LOGD("loadAudioSource: Async decoder ready.");
    }
}

void AudioEngine::setLoopPoints(int64_t startFrame, int64_t endFrame) {
    mLoopStartFrame = startFrame;
    mLoopEndFrame = endFrame;
    mIsLooping = (endFrame > startFrame);
    
    // 不再这里强制重置 FIFO 喵！
    // 这样微调循环点时就不会导致播放位置突然跳跃了。
    // 后台解码线程在下一次填充时会自动应用新的循环点。
    mFifoCond.notify_all();
}

void AudioEngine::seekTo(int64_t frame) {
    mCurrentReadFrame = frame; 
    mSeekTarget = frame;
    mShouldSeek = true;
    resetFifo();
    mFifoCond.notify_all();
}

int64_t AudioEngine::getCurrentPosition() {
    return mCurrentReadFrame.load();
}

int64_t AudioEngine::getDuration() {
    // 确保 mDecoder 存在且已经加载
    if (mDecoder) {
        return mDecoder->getTotalFrames();
    }
    return 0;
}

int32_t AudioEngine::getSampleRate() {
    return mSampleRate.load();
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *oboeStream,
                                                   void *audioData,
                                                   int32_t numFrames) {
    float *floatData = static_cast<float *>(audioData);
    int32_t channels = mChannelCount.load();
    int32_t samplesNeeded = numFrames * channels;

    int32_t read = readFromFifo(floatData, samplesNeeded);
    
    if (read < samplesNeeded) {
        // 如果 FIFO 拿不够，补静音喵
        memset(floatData + read, 0, (samplesNeeded - read) * sizeof(float));
    }

    // 安全检查：防止声道数为 0 导致除零崩溃喵！
    if (channels <= 0) {
        memset(floatData, 0, numFrames * mChannelCount.load() * sizeof(float)); 
        return oboe::DataCallbackResult::Continue;
    }

    // 更新当前播放时间（进度条）
    int32_t framesRead = read / channels;
    mCurrentReadFrame.fetch_add(static_cast<int64_t>(framesRead));
    
    // 如果播放超过了循环点，就在 UI 进度上做个模拟回跳（真正的跳转由后台完成）
    if (mIsLooping.load() && mCurrentReadFrame.load() >= mLoopEndFrame.load()) {
        auto start = mLoopStartFrame.load();
        auto end = mLoopEndFrame.load();
        // 只有当 end 大于 start 时才调整，防止逻辑死循环
        if (end > start) {
             mCurrentReadFrame.store(start + (mCurrentReadFrame.load() - end));
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::decodingLoop() {
    // 这是一个常驻线程喵
    const int32_t kBlockFrames = 512;
    std::vector<float> decodeBuffer;

    while (mIsDecoding) {
        {
            std::unique_lock<std::mutex> lock(mFifoMutex);
            // 如果 FIFO 满了或者没有加载曲子，就睡一会儿喵。这里用原子的 mSampleRate 避免死锁
            mFifoCond.wait(lock, [this]() {
                return !mIsDecoding || (mSampleRate.load() > 0 && mFifoFullCount < kFifoSize - 4096) || mShouldSeek;
            });
        }

        if (!mIsDecoding) break;

        // 1. 安全预检喵
        int32_t channels = mChannelCount.load();
        bool isLooping = mIsLooping.load();
        int64_t loopStart = mLoopStartFrame.load();
        int64_t loopEnd = mLoopEndFrame.load();

        if (isLooping && loopEnd <= loopStart) isLooping = false;

        decodeBuffer.resize(kBlockFrames * channels);
        int32_t samplesReadTotal = 0; // 改叫 samplesReadTotal，明确它是样本数！

        {
            // 在这里获取锁，保护所有解码器操作！
            std::lock_guard<std::mutex> decoderLock(mDecoderMutex);
            
            if (!mDecoder || mDecoder->getSampleRate() <= 0) {
                // 如果恰好还没真正就绪，直接跳过等一下再试
                continue;
            }

            // 处理外部跳转请求
            if (mShouldSeek) {
                mDecoder->seekToFrame(mSeekTarget.load());
                mShouldSeek = false;
            }

            int64_t currentFrame = mDecoder->getCurrentPosition();

            // 跳转逻辑：如果到了终点就飞回去喵
            if (isLooping && currentFrame >= loopEnd) {
                mDecoder->seekToFrame(loopStart);
                currentFrame = loopStart;
            }

            // 计算本轮解码上限制 (以帧为单位计算)
            int32_t framesRequested = kBlockFrames;
            if (isLooping) {
                int64_t remainingFrames = loopEnd - currentFrame;
                framesRequested = (remainingFrames > 0) ? std::min(static_cast<int32_t>(remainingFrames), kBlockFrames) : 0;
            }

            if (framesRequested > 0) {
                // readSamples 返回的是真正的样本数量 (samples)！
                samplesReadTotal = mDecoder->readSamples(decodeBuffer.data(), framesRequested * channels);
            }

            // 无缝接龙逻辑喵
            int32_t targetSamples = kBlockFrames * channels;
            if (isLooping && samplesReadTotal < targetSamples) {
                mDecoder->seekToFrame(loopStart);
                int32_t samplesStillNeeded = targetSamples - samplesReadTotal;
                // 这次算对了指针偏移，之前竟然又乘了一次 channels！
                int32_t secondRead = mDecoder->readSamples(decodeBuffer.data() + samplesReadTotal, samplesStillNeeded);
                if (secondRead > 0) samplesReadTotal += secondRead;
            }
        }

        // 2. 只有拿到肉了才去投喂 FIFO 喵
        if (samplesReadTotal > 0) {
            // 这里绝对不能再乘以 channels 了，samplesReadTotal 本身就是我们要塞入的浮点数量！
            writeToFifo(decodeBuffer.data(), samplesReadTotal);
        } else {
            // 到达非循环的文件末尾，或者恰巧没读出数据，稍微歇会儿防空转
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }
    }
}

bool AudioEngine::writeToFifo(const float* data, int32_t numSamples) {
    if (numSamples <= 0) return true;
    
    std::lock_guard<std::mutex> lock(mFifoMutex);
    
    // 莱芙这次加上最硬的防溢出外壳
    int32_t actualToWrite = numSamples;
    if (mFifoFullCount + numSamples > kFifoSize) {
        // 如果实在是塞不下了，只能丢弃掉这组样本了，但这通常说明后台线程跑太快了
        // 理想情况下通过 condition 保证这里不会溢出喵
        actualToWrite = static_cast<int32_t>(kFifoSize - mFifoFullCount);
    }
    
    if (actualToWrite > 0) {
        for (int32_t i = 0; i < actualToWrite; i++) {
            mFifo[mFifoWritePos] = data[i];
            mFifoWritePos = (mFifoWritePos + 1) % kFifoSize;
            mFifoFullCount++;
        }
    }
    
    return actualToWrite == numSamples;
}

int32_t AudioEngine::readFromFifo(float* data, int32_t numSamples) {
    std::lock_guard<std::mutex> lock(mFifoMutex);
    int32_t read = 0;
    while (read < numSamples && mFifoFullCount > 0) {
        data[read] = mFifo[mFifoReadPos];
        mFifoReadPos = (mFifoReadPos + 1) % kFifoSize;
        mFifoFullCount--;
        read++;
    }
    if (mFifoFullCount < kFifoSize / 2) {
        mFifoCond.notify_one(); // 仓库空出一半了，叫搬运工干活喵！
    }
    return read;
}

void AudioEngine::resetFifo() {
    std::lock_guard<std::mutex> lock(mFifoMutex);
    mFifoReadPos = 0;
    mFifoWritePos = 0;
    mFifoFullCount = 0;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    if (error == oboe::Result::ErrorDisconnected) {
        start();
    }
}
