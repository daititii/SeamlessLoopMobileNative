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
#include <functional>

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
    void setLooping(bool isLooping);
    void seekTo(int64_t frame);
    int64_t getCurrentPosition();
    int64_t getDuration();
    int32_t getSampleRate();
    bool isPlaying() const;

    void setEventCallback(std::function<void(int)> callback) { mEventCallback = callback; }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::function<void(int)> mEventCallback;
    std::mutex mStreamMutex;
    std::shared_ptr<oboe::AudioStream> mStream;
    std::unique_ptr<AudioDecoder> mDecoderA;
    std::unique_ptr<AudioDecoder> mDecoderB;
    std::mutex mDecoderMutex; // 用于保护解码器操作喵
    
    std::atomic<int64_t> mLoopStartFrame {0};
    std::atomic<int64_t> mLoopEndFrame {0};
    std::atomic<int64_t> mCurrentReadFrame {0};
    std::atomic<int64_t> mLogicalDecodeFrame {0}; // 后台解码的虚拟逻辑位置喵！
    std::atomic<bool> mIsLooping {false};
    std::atomic<bool> mIsPlaying {false}; // 记录播放状态，防止断线重连后意外自动播放喵！
    std::atomic<bool> mIsAbMode {false};
    
    std::atomic<int64_t> mTotalAbFrames {0};
    std::atomic<int64_t> mAbIntroFrames {0};
    
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
    const size_t kFifoSize = 132300; // 约 1.5 秒的缓冲区，为 Seek 寻轨提供超长平滑保护喵！
    // -------------------------

    void decodingLoop(); // 后台解码线程喵！
    bool writeToFifo(const float* data, int32_t numSamples);
    int32_t readFromFifo(float* data, int32_t numSamples);
    void resetFifo();

    // 逻辑虚拟流读写与跳转助手函数喵！
    int32_t readLogicalSamples(float* target, int32_t numSamples);
    bool seekLogicalToFrame(int64_t frame);
    bool isLogicalFinished();

    std::atomic<int32_t> mChannelCount {2};
    std::atomic<int32_t> mSampleRate {44100};

    std::atomic<bool> mEosSent {false};
};

#endif //SEAMLESSLOOPMOBILE_AUDIOENGINE_H
