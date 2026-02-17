#include "AudioEngine.h"

AudioEngine::AudioEngine() {
    LOGD("AudioEngine created");
}

AudioEngine::~AudioEngine() {
    stop();
    LOGD("AudioEngine destroyed");
}

bool AudioEngine::start() {
    oboe::AudioStreamBuilder builder;
    builder.setFormat(oboe::AudioFormat::Float); // 使用 32-bit Float 数据格式
    builder.setChannelCount(mChannelCount);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency); // 低延迟模式
    builder.setSharingMode(oboe::SharingMode::Shared); // 共享模式（避免独占）
    builder.setCallback(this); // 我们自己处理回调

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open stream. Error: %s", oboe::convertToText(result));
        return false;
    }

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start stream. Error: %s", oboe::convertToText(result));
        mStream->close();
        return false;
    }

    LOGD("Audio stream started successfully");
    return true;
}

void AudioEngine::stop() {
    if (mStream) {
        mStream->close();
        mStream.reset();
    }
}

void AudioEngine::setLoopPoints(int64_t startFrame, int64_t endFrame) {
    mLoopStartFrame = startFrame;
    mLoopEndFrame = endFrame;
    mIsLooping = (endFrame > startFrame); // 设置了有效区间才开启循环
}

// 模拟加载音频数据（这里暂时生成一个简单的正弦波作为测试）
// 真正能解码 MP3/FLAC 的功能需要等之后引入 FFmpeg 或 NdkMediaCodec
void AudioEngine::loadAudioSource(const std::string& filePath) {
    // 获取实际的采样率（如果流已经打开）
    int sampleRate = 44100; // 默认值
    if (mStream) {
        sampleRate = mStream->getSampleRate();
        LOGD("Using actual sample rate: %d Hz", sampleRate);
    } else {
        LOGD("Stream not opened yet, using default sample rate: %d Hz", sampleRate);
    }
    
    int durationSec = 10;
    int totalFrames = sampleRate * durationSec;
    
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

    // 生成一些测试数据填充 mAudioBuffer
    mAudioBuffer.clear();
    mAudioBuffer.resize(totalFrames * mChannelCount);
    
    for (int i = 0; i < totalFrames; i++) {
        // 生成一个简单的正弦波 (440Hz A音)
        float sample = sinf((2 * M_PI * 440.0f * i) / sampleRate);
        for (int c = 0; c < mChannelCount; c++) {
            mAudioBuffer[i * mChannelCount + c] = sample * 0.5f; // 音量减半
        }
    }
    
    // 默认循环整首歌
    setLoopPoints(0, totalFrames);
    mCurrentReadFrame = 0; 
}

// 核心循环逻辑！
oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *oboeStream,
                                                   void *audioData,
                                                   int32_t numFrames) {
    float *floatData = static_cast<float *>(audioData);
    
    // 如果没有数据缓存，输出静音
    if (mAudioBuffer.empty()) {
        memset(floatData, 0, numFrames * mChannelCount * sizeof(float));
        return oboe::DataCallbackResult::Continue;
    }

    for (int frame = 0; frame < numFrames; ++frame) {
        // 循环检测
        if (mIsLooping && mCurrentReadFrame >= mLoopEndFrame) {
            mCurrentReadFrame = mLoopStartFrame.load(); // 显式从原子变量加载值
            LOGD("Loop triggered! Jumped to %ld", (long)mLoopStartFrame.load());
        }
        
        // 边界保护（防止溢出）
        if (mCurrentReadFrame >= mAudioBuffer.size() / mChannelCount) {
             // 播放结束，如果是普通模式就停播，由于我们是循环播放器，这里其实是个异常分支
            for (int c = 0; c < mChannelCount; c++) {
                *floatData++ = 0; // 输出静音
            }
        } else {
            // 复制当前帧的数据到输出缓冲区
            for (int c = 0; c < mChannelCount; c++) {
                *floatData++ = mAudioBuffer[mCurrentReadFrame * mChannelCount + c];
            }
            mCurrentReadFrame++;
        }
    }
    
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Error occurred: %s", oboe::convertToText(error));
    // 这里通常需要尝试重启流
}
