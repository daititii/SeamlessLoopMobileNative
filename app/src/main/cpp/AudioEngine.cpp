#include "AudioEngine.h"
#include <cstring>
#include <chrono>

AudioEngine::AudioEngine() {
    mDecoderA = std::make_unique<AudioDecoder>();
    mDecoderB = std::make_unique<AudioDecoder>();
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
    std::lock_guard<std::mutex> lock(mStreamMutex);
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

    if (mIsPlaying.load()) {
        result = mStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start Oboe stream: %s", oboe::convertToText(result));
            return false;
        }
    }

    LOGD("Audio stream started successfully");
    return true;
}

void AudioEngine::stop() {
    mIsPlaying = false;
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }
}

void AudioEngine::pause() {
    mIsPlaying = false;
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mStream) {
        mStream->requestPause();
    }
}

void AudioEngine::resume() {
    mIsPlaying = true;
    std::unique_lock<std::mutex> lock(mStreamMutex);
    
    if (mStream) {
        oboe::Result result = mStream->requestStart();
        if (result == oboe::Result::OK) {
            LOGD("resume: Stream started successfully");
            return;
        }
        
        LOGE("resume: Failed to start stream: %s, recreating...", oboe::convertToText(result));
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }
    
    lock.unlock(); 
    if (!start()) {
        LOGE("resume: Failed to recreate stream");
        mIsPlaying = false; 
    }
}

void AudioEngine::loadAudioSource(int fd, int64_t offset, int64_t length) {
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    bool okA = mDecoderA->open(fd, offset, length);
    
    if (okA) {
        mSampleRate = mDecoderA->getSampleRate();
        mChannelCount = mDecoderA->getChannelCount();
        mCurrentReadFrame = 0;
        mLogicalDecodeFrame = 0;
        
        mLoopStartFrame = 0;
        mLoopEndFrame = mDecoderA->getTotalFrames();
        
        mIsLooping = true;
        mIsPlaying = false; 
        mIsAbMode = false;
        mTotalAbFrames = 0;
        mAbIntroFrames = 0;
        
        resetFifo();
        mEosSent = false;
        mFifoCond.notify_all();
        LOGD("loadAudioSource: Single stream decoder ready. Total frames: %lld", (long long)mLoopEndFrame.load());
    }
}

void AudioEngine::loadAbAudioSource(int fdA, int64_t offsetA, int64_t lengthA, int fdB, int64_t offsetB, int64_t lengthB, bool isFeatureLoopEnabled) {
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    LOGD("loadAbAudioSource: Loading Intro and Loop files... isFeatureLoopEnabled=%d", isFeatureLoopEnabled);
    
    bool okA = mDecoderA->open(fdA, offsetA, lengthA);
    bool okB = mDecoderB->open(fdB, offsetB, lengthB);
    
    if (okA && okB) {
        mSampleRate = mDecoderA->getSampleRate();
        mChannelCount = mDecoderA->getChannelCount();
        
        mCurrentReadFrame = 0;
        mLogicalDecodeFrame = 0;
        mIsAbMode = true;
        mIsLooping = true;
        
        int64_t lenA = mDecoderA->getTotalFrames();
        int64_t lenB = mDecoderB->getTotalFrames();
        mAbIntroFrames = lenA;
        mTotalAbFrames = lenA + lenB;
        
        // 🍓 核心重构：若开启了前奏过滤（特色循环），则回到 B段开头（lenA）；
        // 否则回绕到整首歌的开头（0采样点），形成全曲拼接大回绕循环喵！
        mLoopStartFrame = isFeatureLoopEnabled ? lenA : 0;
        mLoopEndFrame = mTotalAbFrames.load();
        mIsPlaying = false; 
        
        resetFifo();
        mEosSent = false;
        mFifoCond.notify_all();
        LOGD("loadAbAudioSource: Intro(A) and Loop(B) sources ready. Loop range on virtual timeline: [%lld, %lld]", 
             (long long)mLoopStartFrame.load(), (long long)mLoopEndFrame.load());
    }
}

void AudioEngine::setLoopPoints(int64_t startFrame, int64_t endFrame) {
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    mLoopStartFrame = startFrame;
    mLoopEndFrame = endFrame;
    mFifoCond.notify_all();
}

void AudioEngine::setLooping(bool isLooping) {
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    if (isLooping) {
        mIsLooping = (mLoopEndFrame.load() > mLoopStartFrame.load());
    } else {
        mIsLooping = false;
    }
    mFifoCond.notify_all();
}

void AudioEngine::seekTo(int64_t frame) {
    LOGD("seekTo: Requesting jump to frame %lld (ABMode=%d)", (long long)frame, mIsAbMode.load());
    mSeekTarget = frame;
    mShouldSeek = true;
    mCurrentReadFrame.store(frame); 
    resetFifo();
    mFifoCond.notify_all();
}

int64_t AudioEngine::getCurrentPosition() {
    return mCurrentReadFrame.load();
}

int64_t AudioEngine::getDuration() {
    if (mIsAbMode.load()) {
        return mTotalAbFrames.load();
    }
    if (mDecoderA) {
        return mDecoderA->getTotalFrames();
    }
    return 0;
}

int32_t AudioEngine::getSampleRate() {
    return mSampleRate.load();
}

bool AudioEngine::isPlaying() const {
    return mIsPlaying.load();
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *oboeStream,
                                                   void *audioData,
                                                   int32_t numFrames) {
    auto *floatData = static_cast<float *>(audioData);
    int32_t channels = mChannelCount.load();
    int32_t samplesNeeded = numFrames * channels;

    int32_t read = readFromFifo(floatData, samplesNeeded);
    
    if (read < samplesNeeded) {
        memset(floatData + read, 0, (samplesNeeded - read) * sizeof(float));
    }

    if (channels <= 0) {
        memset(floatData, 0, numFrames * mChannelCount.load() * sizeof(float)); 
        return oboe::DataCallbackResult::Continue;
    }

    int32_t framesRead = read / channels;
    int64_t currentPos = mCurrentReadFrame.load();
    int64_t nextPos = currentPos + framesRead;
    
    if (mIsLooping.load()) {
        int64_t loopStart = mLoopStartFrame.load();
        int64_t loopEnd = mLoopEndFrame.load();
        if (loopEnd > loopStart && nextPos >= loopEnd) {
            nextPos = loopStart + (nextPos - loopEnd);
        }
    }
    mCurrentReadFrame.store(nextPos);

    if (!mIsLooping.load() && read < samplesNeeded && isLogicalFinished()) {
        if (!mEosSent.exchange(true)) {
            LOGD("AudioEngine: Precise EOS detected in Oboe callback.");
            if (mEventCallback) mEventCallback(1); 
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::decodingLoop() {
    const int32_t kBlockFrames = 512;
    std::vector<float> decodeBuffer;

    while (mIsDecoding) {
        {
            std::unique_lock<std::mutex> lock(mFifoMutex);
            mFifoCond.wait(lock, [this]() {
                return !mIsDecoding || (mSampleRate.load() > 0 && mFifoFullCount < kFifoSize - 4096) || mShouldSeek;
            });
        }

        if (!mIsDecoding) break;

        int32_t channels = mChannelCount.load();
        bool isLooping = mIsLooping.load();
        int64_t loopStart = mLoopStartFrame.load();
        int64_t loopEnd = mLoopEndFrame.load();

        if (isLooping && loopEnd <= loopStart) isLooping = false;

        decodeBuffer.resize(kBlockFrames * channels);
        int32_t samplesReadTotal = 0;

        {
            std::lock_guard<std::mutex> decoderLock(mDecoderMutex);
            
            if (!mDecoderA || mDecoderA->getSampleRate() <= 0) {
                continue;
            }

            if (mShouldSeek) {
                int64_t target = mSeekTarget.load();
                LOGD("decodingLoop: Processing logical seek to %lld", (long long)target);
                seekLogicalToFrame(target);
                mShouldSeek = false;
                
                isLooping = mIsLooping.load();
                loopStart = mLoopStartFrame.load();
                loopEnd = mLoopEndFrame.load();
                if (isLooping && loopEnd <= loopStart) isLooping = false;
            }

            int64_t currentFrame = mLogicalDecodeFrame.load();
            int32_t framesRequested = kBlockFrames;
            bool hitLoopEnd = false;

            if (isLooping) {
                int64_t remainingFrames = loopEnd - currentFrame;
                if (remainingFrames <= kBlockFrames) {
                    framesRequested = std::max(0, static_cast<int32_t>(remainingFrames));
                    hitLoopEnd = true;
                }
            }

            if (framesRequested > 0) {
                samplesReadTotal = readLogicalSamples(decodeBuffer.data(), framesRequested * channels);
            }

            if (isLooping && !hitLoopEnd && samplesReadTotal < framesRequested * channels && isLogicalFinished()) {
                hitLoopEnd = true;
                LOGD("decodingLoop: Triggered early loop jump due to logical EOF.");
            }

            if (isLooping && hitLoopEnd) {
                LOGD("decodingLoop: Reached loopEnd %lld. Instant logical seek to loopStart %lld", (long long)loopEnd, (long long)loopStart);
                seekLogicalToFrame(loopStart);
                
                int32_t samplesStillNeeded = (kBlockFrames * channels) - samplesReadTotal;
                if (samplesStillNeeded > 0) {
                    int32_t secondRead = readLogicalSamples(decodeBuffer.data() + samplesReadTotal, samplesStillNeeded);
                    if (secondRead > 0) {
                        samplesReadTotal += secondRead;
                    }
                }
                
                if (mEventCallback) mEventCallback(2); 
            }
        }

        if (samplesReadTotal > 0) {
            writeToFifo(decodeBuffer.data(), samplesReadTotal);
        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }
    }
}

bool AudioEngine::writeToFifo(const float* data, int32_t numSamples) {
    if (numSamples <= 0) return true;

    std::lock_guard<std::mutex> lock(mFifoMutex);

    int32_t actualToWrite = numSamples;
    if (mFifoFullCount + numSamples > kFifoSize) {
        actualToWrite = static_cast<int32_t>(kFifoSize - mFifoFullCount);
    }

    if (actualToWrite > 0) {
        size_t firstPart = std::min(static_cast<size_t>(actualToWrite), kFifoSize - mFifoWritePos);
        std::memcpy(&mFifo[mFifoWritePos], data, firstPart * sizeof(float));
        if (firstPart < static_cast<size_t>(actualToWrite)) {
            std::memcpy(&mFifo[0], data + firstPart,
                        (actualToWrite - static_cast<int32_t>(firstPart)) * sizeof(float));
        }
        mFifoWritePos = (mFifoWritePos + actualToWrite) % kFifoSize;
        mFifoFullCount += actualToWrite;
    }

    return actualToWrite == numSamples;
}

int32_t AudioEngine::readFromFifo(float* data, int32_t numSamples) {
    std::lock_guard<std::mutex> lock(mFifoMutex);

    int32_t actualToRead = std::min(numSamples, static_cast<int32_t>(mFifoFullCount));

    if (actualToRead > 0) {
        size_t firstPart = std::min(static_cast<size_t>(actualToRead), kFifoSize - mFifoReadPos);
        std::memcpy(data, &mFifo[mFifoReadPos], firstPart * sizeof(float));
        if (firstPart < static_cast<size_t>(actualToRead)) {
            std::memcpy(data + firstPart, &mFifo[0],
                        (actualToRead - static_cast<int32_t>(firstPart)) * sizeof(float));
        }
        mFifoReadPos = (mFifoReadPos + actualToRead) % kFifoSize;
        mFifoFullCount -= actualToRead;
    }

    if (mFifoFullCount < kFifoSize / 2) {
        mFifoCond.notify_one(); 
    }
    return actualToRead;
}

void AudioEngine::resetFifo() {
    std::lock_guard<std::mutex> lock(mFifoMutex);
    mFifoReadPos = 0;
    mFifoWritePos = 0;
    mFifoFullCount = 0;
}

int32_t AudioEngine::readLogicalSamples(float* target, int32_t numSamples) {
    int32_t samplesRead = 0;
    int32_t channels = mChannelCount.load();
    
    while (samplesRead < numSamples) {
        int32_t samplesNeeded = numSamples - samplesRead;
        int64_t currentFrame = mLogicalDecodeFrame.load();
        
        if (!mIsAbMode.load() || currentFrame < mAbIntroFrames.load()) {
            int32_t read = mDecoderA->readSamples(target + samplesRead, samplesNeeded);
            if (read > 0) {
                samplesRead += read;
                mLogicalDecodeFrame.store(currentFrame + (read / channels));
            } else {
                break; 
            }
        } else {
            int32_t read = mDecoderB->readSamples(target + samplesRead, samplesNeeded);
            if (read > 0) {
                samplesRead += read;
                mLogicalDecodeFrame.store(currentFrame + (read / channels));
            } else {
                break; 
            }
        }
    }
    return samplesRead;
}

bool AudioEngine::seekLogicalToFrame(int64_t frame) {
    mLogicalDecodeFrame.store(frame);
    if (!mIsAbMode.load() || frame < mAbIntroFrames.load()) {
        return mDecoderA->seekToFrame(frame);
    } else {
        int64_t offsetInB = frame - mAbIntroFrames.load();
        return mDecoderB->seekToFrame(offsetInB);
    }
}

bool AudioEngine::isLogicalFinished() {
    int64_t currentFrame = mLogicalDecodeFrame.load();
    if (!mIsAbMode.load() || currentFrame < mAbIntroFrames.load()) {
        return mDecoderA->isFinished();
    } else {
        return mDecoderB->isFinished();
    }
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("onErrorAfterClose: %s", oboe::convertToText(error));
    
    if (error == oboe::Result::ErrorDisconnected) {
        if (mIsPlaying.load()) {
            LOGD("Stream disconnected. Attempting to auto-restart in background...");
            std::thread([this]() {
                std::this_thread::sleep_for(std::chrono::milliseconds(150));
                if (mIsPlaying.load()) {
                    LOGD("Auto-resuming audio stream to fix Disconnected error!");
                    resume();
                }
            }).detach();
        }
    }
}
