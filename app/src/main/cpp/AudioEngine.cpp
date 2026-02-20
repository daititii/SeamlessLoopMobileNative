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
    
    // 强制清空缓冲区，让新的循环点立即生效喵！
    resetFifo();
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
        memset(floatData, 0, numFrames * sizeof(float)); // 既然不知道声道，就填 0 静音吧
        return oboe::DataCallbackResult::Continue;
    }

    // 更新当前播放时间（近似值，因为 FIFO 是平滑的）
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
            // 如果 FIFO 满了或者没有加载曲子，就睡一会儿喵
            mFifoCond.wait(lock, [this]() {
                return !mIsDecoding || (mDecoder->getSampleRate() > 0 && mFifoFullCount < kFifoSize - 4096) || mShouldSeek;
            });
        }

        if (!mIsDecoding) break;

        std::lock_guard<std::mutex> decoderLock(mDecoderMutex);
        
        // 处理外部跳转请求
        if (mShouldSeek) {
            mDecoder->seekToFrame(mSeekTarget);
            mShouldSeek = false;
        }

        int32_t channels = mChannelCount.load();
        decodeBuffer.resize(kBlockFrames * channels);

        // 核心：处理循环逻辑喵！
        int64_t currentFrame = mDecoder->getCurrentPosition(); 
        int32_t framesToDecode = kBlockFrames;
        
        if (mIsLooping.load() && currentFrame >= mLoopEndFrame.load()) {
            mDecoder->seekToFrame(mLoopStartFrame.load());
            currentFrame = mLoopStartFrame.load();
        }

        if (mIsLooping) {
            auto remainingFrames = mLoopEndFrame.load() - currentFrame;
            // 如果剩余帧是负数（比如 End 突然变到了 Current 之前），直接设为 0，让下一行的大循环里的 <= 0 逻辑去处理 Seek
            if (remainingFrames < 0) remainingFrames = 0;
            
            framesToDecode = static_cast<int32_t>(std::min(static_cast<int64_t>(kBlockFrames), remainingFrames));
        }

        if (framesToDecode <= 0 && mIsLooping.load()) {
            mDecoder->seekToFrame(mLoopStartFrame.load());
            continue;
        }

        int32_t read = mDecoder->readSamples(decodeBuffer.data(), std::max(0, framesToDecode) * channels);
        
        if (read > 0) {
            writeToFifo(decodeBuffer.data(), read);
        } else {
            if (mIsLooping.load() && mDecoder->isFinished()) {
                mDecoder->seekToFrame(mLoopStartFrame.load());
            } else {
                // 真的没数据了，休息一下防死循环
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
            }
        }
    }
}

bool AudioEngine::writeToFifo(const float* data, int32_t numSamples) {
    std::lock_guard<std::mutex> lock(mFifoMutex);
    for (int32_t i = 0; i < numSamples; i++) {
        if (mFifoFullCount >= kFifoSize) return false; 
        mFifo[mFifoWritePos] = data[i];
        mFifoWritePos = (mFifoWritePos + 1) % kFifoSize;
        mFifoFullCount++;
    }
    return true;
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
