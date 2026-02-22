#include "AudioEngine.h"
#include <cstring>

AudioEngine::AudioEngine() {
    mDecoderA = std::make_unique<AudioDecoder>();
    mDecoderB = std::make_unique<AudioDecoder>();
    mActiveDecoder = mDecoderA.get();
    mNextDecoder = mDecoderB.get();
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
    bool okA = mDecoderA->open(fd, offset, length);
    bool okB = mDecoderB->open(fd, offset, length);
    
    if (okA && okB) {
        mActiveDecoder = mDecoderA.get();
        mNextDecoder = mDecoderB.get();
        mIsNextDecoderReady = false;

        mSampleRate = mActiveDecoder->getSampleRate();
        mChannelCount = mActiveDecoder->getChannelCount();
        mCurrentReadFrame = 0;
        mLoopStartFrame = 0;
        mLoopEndFrame = mActiveDecoder->getTotalFrames();
        mIsLooping = true;
        mIsAbMode = false;
        mAbTransitionDone = false;
        mTotalAbFrames = 0;
        mAbIntroFrames = 0;
        
        resetFifo();
        mFifoCond.notify_all();
        LOGD("loadAudioSource: Dual Async decoders ready.");
    }
}

void AudioEngine::loadAbAudioSource(int fdA, int64_t offsetA, int64_t lengthA, int fdB, int64_t offsetB, int64_t lengthB) {
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    LOGD("loadAbAudioSource: Loading Intro and Loop files...");
    
    bool okA = mDecoderA->open(fdA, offsetA, lengthA);
    bool okB = mDecoderB->open(fdB, offsetB, lengthB);
    
    if (okA && okB) {
        mActiveDecoder = mDecoderA.get();
        mNextDecoder = mDecoderB.get();
        mIsNextDecoderReady = false;

        mSampleRate = mActiveDecoder->getSampleRate();
        mChannelCount = mActiveDecoder->getChannelCount();
        
        mCurrentReadFrame = 0;
        mIsAbMode = true;
        mAbTransitionDone = false;
        mIsLooping = true;
        
        int64_t lenA = mDecoderA->getTotalFrames();
        int64_t lenB = mDecoderB->getTotalFrames();
        mAbIntroFrames = lenA;
        mTotalAbFrames = lenA + lenB;
        
        // 初始用户期望：循环整个 Part B 喵
        mUserLoopStart = lenA;
        mUserLoopEnd = mTotalAbFrames.load();

        // 第一轮：内部解码器正在跑 A，所以只需跑到 A 的物理末尾即可触发换棒
        mLoopStartFrame = 0; 
        mLoopEndFrame = mActiveDecoder->getTotalFrames();
        
        resetFifo();
        mFifoCond.notify_all();
        LOGD("loadAbAudioSource: Intro(A) and Loop(B) sources ready. User loop: [%lld, %lld]", (long long)mUserLoopStart.load(), (long long)mUserLoopEnd.load());
    }
}

void AudioEngine::setLoopPoints(int64_t startFrame, int64_t endFrame) {
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    mUserLoopStart = startFrame;
    mUserLoopEnd = endFrame;

    if (mIsAbMode.load()) {
        if (mAbTransitionDone.load()) {
            // 如果已经在 B 段里了，立刻应用到内部循环点上
            int64_t intro = mAbIntroFrames.load();
            mLoopStartFrame = std::max((int64_t)0, startFrame - intro);
            mLoopEndFrame = std::max(mLoopStartFrame.load(), endFrame - intro);
        } else {
            // 如果还在 A 段（前奏），内部循环点必须保持为 [0, TotalA]，等进去 B 段后再应用
            mLoopStartFrame = 0;
            mLoopEndFrame = mAbIntroFrames.load();
        }
    } else {
        mLoopStartFrame = startFrame;
        mLoopEndFrame = endFrame;
    }

    mIsLooping = (endFrame > startFrame);
    mIsNextDecoderReady = false;
    mFifoCond.notify_all();
}

void AudioEngine::seekTo(int64_t frame) {
    if (mIsAbMode.load()) {
        // AB 模式下的真·魔法跳转！
        mSeekTarget = frame;
        mShouldSeek = true;
        mCurrentReadFrame.store(frame); // <--- 提前预载防闪烁
        resetFifo();
        mFifoCond.notify_all();
        return;
    }
    mSeekTarget = frame;
    mShouldSeek = true;
    mCurrentReadFrame.store(frame); // <--- 提前预载防闪烁
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
    // 确保 mActiveDecoder 存在且已经加载
    if (mActiveDecoder) {
        return mActiveDecoder->getTotalFrames();
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
    if (mIsLooping.load()) {
        int64_t uiLoopStart = mLoopStartFrame.load();
        int64_t uiLoopEnd = mLoopEndFrame.load();

        if (mIsAbMode.load()) {
            // 在合体模式下，进度的终点实际上是用户期望的绝对位置喵！
            uiLoopStart = mUserLoopStart.load();
            uiLoopEnd = mUserLoopEnd.load();
        }

        if (mCurrentReadFrame.load() >= uiLoopEnd) {
             if (uiLoopEnd > uiLoopStart) {
                  mCurrentReadFrame.store(uiLoopStart + (mCurrentReadFrame.load() - uiLoopEnd));
             }
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
            
            if (!mActiveDecoder || mActiveDecoder->getSampleRate() <= 0) {
                // 如果恰好还没真正就绪，直接跳过等一下再试
                continue;
            }

            // 处理外部跳转请求喵！
            if (mShouldSeek) {
                int64_t target = mSeekTarget.load();
                if (mIsAbMode.load()) {
                    // 这是超复杂的 AB 模式时间穿梭喵...
                    int64_t introLength = mAbIntroFrames.load();
                    if (target < introLength) {
                        // 回到过去了，还在前奏区
                        if (mAbTransitionDone.load()) {
                            // 哎呀，已经进入循环区了？现在我们要退回到引子 A！
                            AudioDecoder* temp = mActiveDecoder;
                            mActiveDecoder = mNextDecoder;
                            mNextDecoder = temp; // 换回来！
                            // （此版本暂时没有重载 A 的逻辑，如果用户频繁这样跳可能会异常。
                            // 电脑端实际上通常是把AB看作一体了，或者禁止向A退回。我们尽力一跳）
                            mAbTransitionDone = false;
                            mLoopStartFrame = 0;
                            mLoopEndFrame = introLength;
                        }
                        mActiveDecoder->seekToFrame(target);
                        mCurrentReadFrame.store(target);
                    } else {
                        // 在循环 B 段之内
                        if (!mAbTransitionDone.load()) {
                            // 假装已经经历了前奏
                            AudioDecoder* temp = mActiveDecoder;
                            mActiveDecoder = mNextDecoder;
                            mNextDecoder = temp;
                            mNextDecoder->openFromDecoder(mActiveDecoder); 
                            mAbTransitionDone = true;
                        }
                        // 需要把目标映射到 B 段内部的时间点
                        int64_t bTotal = mActiveDecoder->getTotalFrames();
                        int64_t uStart = mUserLoopStart.load() - introLength;
                        int64_t uEnd = mUserLoopEnd.load() - introLength;

                        // 映射跳转点：用户点的是全局 target，在 B 段里的位置是 target - intro
                        int64_t offsetInB = (target - introLength);
                        
                        // 应用循环限制逻辑：如果用户点到了循环圈外，我们要把它圈回来喵
                        if (uEnd > uStart && uEnd > 0) {
                            offsetInB = uStart + (offsetInB - uStart) % (uEnd - uStart);
                        }

                        mActiveDecoder->seekToFrame(offsetInB);
                        mCurrentReadFrame.store(introLength + offsetInB);
                        
                        mLoopStartFrame = std::max((int64_t)0, uStart);
                        mLoopEndFrame = std::max(mLoopStartFrame.load(), uEnd > 0 ? uEnd : bTotal);
                    }
                } else {
                    mActiveDecoder->seekToFrame(target);
                    mCurrentReadFrame.store(target);
                }
                
                mIsNextDecoderReady = false; // 用户跳转了，替补队员必须重新准备喵！
                mShouldSeek = false;
                
                // 喵！这里非常重要！如果我们刚才在 AB 模式或者其他模式里修改了内部循环点，
                // 必须要刷新一下局部变量，否则接下来的计算会用老的时间点导致立刻鬼畜换棒！
                loopStart = mLoopStartFrame.load();
                loopEnd = mLoopEndFrame.load();
                if (isLooping && loopEnd <= loopStart) isLooping = false;
            }

            int64_t currentFrame = mActiveDecoder->getCurrentPosition();

            // 如果处于循环模式，且接近了终点，替补解码器开始提前跳转到起点待命喵！
            // 这里我们只要不在执行交接任务，就让替补队员去预先 seek 到起点
            if (isLooping && !mIsNextDecoderReady) {
                mNextDecoder->seekToFrame(loopStart);
                mIsNextDecoderReady = true;
                LOGD("DualDecoder: Pre-seeked next decoder to %lld", (long long)loopStart);
            }

            // 计算本轮解码主力能提供多少帧喵
            int32_t framesRequested = kBlockFrames;
            bool hitLoopEnd = false;
            
            if (isLooping) {
                int64_t remainingFrames = loopEnd - currentFrame;
                if (remainingFrames <= kBlockFrames) {
                    framesRequested = std::max(0, static_cast<int32_t>(remainingFrames));
                    hitLoopEnd = true;
                }
            }

            // 先从主力那儿拿数据
            if (framesRequested > 0) {
                samplesReadTotal = mActiveDecoder->readSamples(decodeBuffer.data(), framesRequested * channels);
            }
            
            // 物理 EOF 检查喵！如果循环点设在了歌曲很末尾，
            // 可能会因为没有 encoder padding 导致没达到 loopEnd 就彻底读完了（isFinished）。
            // 此时必须强制换棒，否则会卡死在结尾不循环喵！
            if (isLooping && !hitLoopEnd && samplesReadTotal < framesRequested * channels && mActiveDecoder->isFinished()) {
                hitLoopEnd = true;
                LOGD("DualDecoder: Triggered early handover due to physical EOF.");
            }

            // 【无缝交跑换棒逻辑喵】
            int32_t targetSamples = kBlockFrames * channels;
            if (isLooping && hitLoopEnd) {
                // 主力跑到终点了！直接瞬发换人！
                AudioDecoder* temp = mActiveDecoder;
                mActiveDecoder = mNextDecoder;
                mNextDecoder = temp;

                // 特殊处理 AB 模式第一次由 Intro 切到 Loop 的情况喵！
                if (mIsAbMode.load() && !mAbTransitionDone.load()) {
                    // 现在的 mActiveDecoder 是 Part B 的头，mNextDecoder 是 Part A 的尾。
                    // 我们要把 mNextDecoder 也变成 Part B，这样以后才能无限循环 Part B 喵！
                    mNextDecoder->openFromDecoder(mActiveDecoder);
                    
                    int64_t intro = mAbIntroFrames.load();
                    int64_t uStart = mUserLoopStart.load() - intro;
                    int64_t uEnd = mUserLoopEnd.load() - intro;
                    int64_t bTotal = mActiveDecoder->getTotalFrames();

                    mLoopStartFrame = std::max((int64_t)0, uStart);
                    mLoopEndFrame = std::max(mLoopStartFrame.load(), uEnd > 0 ? uEnd : bTotal);

                    mAbTransitionDone = true;
                    LOGD("DualDecoder: AB Transition completed. Now looping in B: [%lld, %lld]", (long long)mLoopStartFrame.load(), (long long)mLoopEndFrame.load());
                }

                // 检查有没有突发情况（比如替补还没来得及准备就被强迫换上了）
                if (!mIsNextDecoderReady) {
                    LOGD("DualDecoder: Next decoder was NOT ready, forcing emergency seek!");
                    mActiveDecoder->seekToFrame(loopStart);
                }
                
                mIsNextDecoderReady = false; // 刚换下来的那个人需要重新准备喵

                int32_t samplesStillNeeded = targetSamples - samplesReadTotal;
                if (samplesStillNeeded > 0) {
                    // 这个新的主力已经在循环起点准备好数据了，直接拿！这中间因为没有寻轨延迟，是 100% 0 毫秒接缝！
                    int32_t secondRead = mActiveDecoder->readSamples(decodeBuffer.data() + samplesReadTotal, samplesStillNeeded);
                    if (secondRead > 0) samplesReadTotal += secondRead;
                }
                LOGD("DualDecoder: Handover successful at frame %lld", (long long)loopEnd);
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
