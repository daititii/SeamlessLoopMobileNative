#include "AudioEngine.h"
#include <cstring>

AudioEngine::AudioEngine() {
    mDecoder = std::make_unique<AudioDecoder>();
}

AudioEngine::~AudioEngine() {
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
    if (mDecoder) {
        mDecoder->close();
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
        LOGD("loadAudioSource: Streaming decoder opened. Ready to play.");
    }
}

void AudioEngine::setLoopPoints(int64_t startFrame, int64_t endFrame) {
    mLoopStartFrame = startFrame;
    mLoopEndFrame = endFrame;
    mIsLooping = (endFrame > startFrame);
}

void AudioEngine::seekTo(int64_t frame) {
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    if (mDecoder) {
        mDecoder->seekToFrame(frame);
        mCurrentReadFrame = frame;
    }
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
    std::unique_lock<std::mutex> lock(mDecoderMutex, std::try_to_lock);

    if (!lock.owns_lock() || !mDecoder) {
        memset(audioData, 0, numFrames * mChannelCount * sizeof(float));
        return oboe::DataCallbackResult::Continue;
    }

    int32_t channels = mChannelCount.load();
    int32_t totalSamplesNeeded = numFrames * channels;
    
    // 这里就是我们学习 LoopStream.cs 的核心逻辑喵！
    int32_t totalSamplesRead = 0;
    while (totalSamplesRead < totalSamplesNeeded) {
        int64_t currentFrame = mCurrentReadFrame.load();
        
        // 1. 检查是否需要触发循环跳转
        if (mIsLooping && currentFrame >= mLoopEndFrame) {
            mDecoder->seekToFrame(mLoopStartFrame);
            mCurrentReadFrame = mLoopStartFrame.load();
            currentFrame = mCurrentReadFrame.load();
        }

        // 2. 计算本次最多能读多少（读到 LoopEnd 为止）
        int64_t framesToRead;
        if (mIsLooping) {
            framesToRead = std::min((int64_t)numFrames - (totalSamplesRead / channels), 
                                    (int64_t)(mLoopEndFrame - currentFrame));
        } else {
            framesToRead = numFrames - (totalSamplesRead / channels);
        }

        if (framesToRead <= 0) {
            // 说明到了末尾且由于某种原因没能循环，直接填静音
            int32_t remaining = totalSamplesNeeded - totalSamplesRead;
            memset(floatData + totalSamplesRead, 0, remaining * sizeof(float));
            break;
        }

        int32_t read = mDecoder->readSamples(floatData + totalSamplesRead, framesToRead * channels);
        if (read == 0) {
            // 文件暂时读不出数据了
            if (mIsLooping && mDecoder->isFinished()) {
                // 真的播完了，才 seek 回头喵！
                LOGD("Looping back to %lld", (long long)mLoopStartFrame.load());
                mDecoder->seekToFrame(mLoopStartFrame);
                mCurrentReadFrame = mLoopStartFrame.load();
                continue; // 重新尝试读取
            } else {
                // 只是暂时没数据（Underrun）
                // 如果我们刚做过跳转，不应该直接 memset 0 退出，应该让循环有机会再试或者在此处结束喵
                // 但为了防止死循环，如果连续多次读到 0 且非 EOS，我们才填静音
                int32_t remaining = totalSamplesNeeded - totalSamplesRead;
                memset(floatData + totalSamplesRead, 0, remaining * sizeof(float));
                break;
            }
        }

        totalSamplesRead += read;
        mCurrentReadFrame += (read / channels);
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    if (error == oboe::Result::ErrorDisconnected) {
        start();
    }
}
