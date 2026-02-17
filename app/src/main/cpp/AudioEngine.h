#ifndef SEAMLESSLOOPMOBILE_AUDIOENGINE_H
#define SEAMLESSLOOPMOBILE_AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <cmath>
#include <atomic>

// 日志宏定义
#define LOG_TAG "SeamlessLoopEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class AudioEngine : public oboe::AudioStreamCallback {
public:
    AudioEngine();
    ~AudioEngine();

    // 启动音频流
    bool start();

    // 暂停音频流
    void stop();

    // 设置循环点（采样级）
    void setLoopPoints(int64_t startFrame, int64_t endFrame);

    // 加载音频数据（这里暂时简化，后续需要接入解码器）
    // 目前仅做结构搭建
    void loadAudioSource(const std::string& filePath);

    // Oboe 回调：当设备需要音频数据时调用
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream,
                                          void *audioData,
                                          int32_t numFrames) override;

    // Oboe 回调：当出现断连等错误时调用
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::atomic<int64_t> mLoopStartFrame {0};
    std::atomic<int64_t> mLoopEndFrame {0};
    std::atomic<int64_t> mCurrentReadFrame {0};
    std::atomic<bool> mIsLooping {false};
    
    // 简单的 PCM 数据缓存（仅作原型演示，实际应使用环形缓冲或流式解码）
    std::vector<float> mAudioBuffer; 
    int32_t mChannelCount = 2; // 默认为立体声
};

#endif //SEAMLESSLOOPMOBILE_AUDIOENGINE_H
