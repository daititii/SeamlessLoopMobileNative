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

    // 更新当前播放时间（近似值，因为 FIFO 是平滑的）
    int32_t framesRead = read / channels;
    mCurrentReadFrame += framesRead;
    
    // 如果播放超过了循环点，就在 UI 进度上做个模拟回跳（真正的跳转由后台完成）
    if (mIsLooping && mCurrentReadFrame >= mLoopEndFrame) {
        mCurrentReadFrame = mLoopStartFrame + (mCurrentReadFrame - mLoopEndFrame);
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
        
        if (mIsLooping && currentFrame >= mLoopEndFrame) {
            mDecoder->seekToFrame(mLoopStartFrame);
            currentFrame = mLoopStartFrame;
        }

        if (mIsLooping) {
            framesToDecode = (int32_t)std::min((int64_t)kBlockFrames, (int64_t)(mLoopEndFrame - currentFrame));
        }

        if (framesToDecode <= 0 && mIsLooping) {
            mDecoder->seekToFrame(mLoopStartFrame);
            continue;
        }

        int32_t read = mDecoder->readSamples(decodeBuffer.data(), std::max(0, framesToDecode) * channels);
        
        if (read > 0) {
            writeToFifo(decodeBuffer.data(), read);
        } else {
            if (mIsLooping && mDecoder->isFinished()) {
                mDecoder->seekToFrame(mLoopStartFrame);
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
