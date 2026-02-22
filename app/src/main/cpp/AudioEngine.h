#ifndef SEAMLESSLOOPMOBILE_AUDIOENGINE_H
#define SEAMLESSLOOPMOBILE_AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "AudioDecoder.h"
#include <mutex>
#include <atomic>
#include <thread>
#include <memory>
#include <condition_variable>

// 日志宏定义
#define LOG_TAG "SeamlessLoopEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class AudioEngine : public oboe::AudioStreamCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();
    void pause();
    void resume();
    void loadAudioSource(int fd, int64_t offset, int64_t length);
    void loadAbAudioSource(int fdA, int64_t offsetA, int64_t lengthA, int fdB, int64_t offsetB, int64_t lengthB);
    void setLoopPoints(int64_t startFrame, int64_t endFrame);
    void seekTo(int64_t frame);
    int64_t getCurrentPosition();
    int64_t getDuration();
    int32_t getSampleRate();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::unique_ptr<AudioDecoder> mDecoderA;
    std::unique_ptr<AudioDecoder> mDecoderB;
    AudioDecoder* mActiveDecoder = nullptr;
    AudioDecoder* mNextDecoder = nullptr;
    std::mutex mDecoderMutex; // 用于保护解码器操作喵
    std::atomic<bool> mIsNextDecoderReady {false};
    
    std::atomic<int64_t> mLoopStartFrame {0};
    std::atomic<int64_t> mLoopEndFrame {0};
    std::atomic<int64_t> mCurrentReadFrame {0};
    std::atomic<bool> mIsLooping {false};
    std::atomic<bool> mIsAbMode {false};
    std::atomic<bool> mAbTransitionDone {false};
    
    std::atomic<int64_t> mTotalAbFrames {0};
    std::atomic<int64_t> mAbIntroFrames {0};
    
    // 用户设定的绝对循环点（基于完整的时间线，AB 合体时涵盖 A+B）
    std::atomic<int64_t> mUserLoopStart {0};
    std::atomic<int64_t> mUserLoopEnd {0};
    
    // --- 异步解码核心组件喵 ---
    std::thread mDecodingThread;
    std::atomic<bool> mIsDecoding {false};
    std::atomic<bool> mShouldSeek {false};
    std::atomic<int64_t> mSeekTarget {0};
    
    std::mutex mFifoMutex;
    std::condition_variable mFifoCond;
    std::vector<float> mFifo;
    size_t mFifoReadPos {0};
    size_t mFifoWritePos {0};
    size_t mFifoFullCount {0};
    const size_t kFifoSize = 192000; // 约 2 秒的立体声 48kHz 缓冲区
    // -------------------------

    void decodingLoop(); // 后台解码线程喵！
    bool writeToFifo(const float* data, int32_t numSamples);
    int32_t readFromFifo(float* data, int32_t numSamples);
    void resetFifo();

    std::atomic<int32_t> mChannelCount {2};
    std::atomic<int32_t> mSampleRate {44100};
};

#endif //SEAMLESSLOOPMOBILE_AUDIOENGINE_H
