#ifndef SEAMLESSLOOPMOBILE_AUDIOENGINE_H
#define SEAMLESSLOOPMOBILE_AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <cmath>
#include <mutex>
#include <atomic>

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
    void setLoopPoints(int64_t startFrame, int64_t endFrame);
    void loadAudioSource(int fd, int64_t offset, int64_t length);

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    
    std::vector<float> mAudioBuffer;
    std::mutex mBufferMutex; // 用于保护缓冲区的锁喵
    
    std::atomic<int64_t> mLoopStartFrame {0};
    std::atomic<int64_t> mLoopEndFrame {0};
    std::atomic<int64_t> mCurrentReadFrame {0};
    std::atomic<bool> mIsLooping {false};
    
    std::atomic<int32_t> mChannelCount {2};
    std::atomic<int32_t> mSampleRate {44100};
};

#endif //SEAMLESSLOOPMOBILE_AUDIOENGINE_H
