#include "AudioEngine.h"
#include "AudioDecoder.h"

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
    builder.setSampleRate(mSampleRate); // 强制匹配音频文件的采样率喵！
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
        mStream->requestStop(); // 显式请求停止喵！
        mStream->close();
        mStream.reset();
    }
}

void AudioEngine::setLoopPoints(int64_t startFrame, int64_t endFrame) {
    mLoopStartFrame = startFrame;
    mLoopEndFrame = endFrame;
    mIsLooping = (endFrame > startFrame); // 设置了有效区间才开启循环
}

// 使用 AudioDecoder 加载真实音频文件
void AudioEngine::loadAudioSource(int fd, int64_t offset, int64_t length) {
    int32_t sampleRate = 0;
    int32_t channelCount = 0;
    std::vector<float> decodedData;

    LOGD("Attempting to load real audio via FD: %d", fd);
    
    if (AudioDecoder::decode(fd, offset, length, decodedData, sampleRate, channelCount)) {
        std::lock_guard<std::mutex> lock(mBufferMutex); // 加锁确保写入安全喵！
        mAudioBuffer = std::move(decodedData);
        mChannelCount = channelCount;
        mSampleRate = sampleRate; 
        
        int64_t totalFrames = mAudioBuffer.size() / mChannelCount;
        mLoopStartFrame = 0;
        mLoopEndFrame = totalFrames;
        mIsLooping = true;
        mCurrentReadFrame = 0;
        LOGD("Successfully loaded real audio via lock. Total frames: %ld", (long)totalFrames);
    } else {
        LOGE("Failed to decode audio file. Falling back to silence.");
        mAudioBuffer.clear();
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *oboeStream,
                                                   void *audioData,
                                                   int32_t numFrames) {
    float *floatData = static_cast<float *>(audioData);
    
    // 尝试加锁。如果是切换歌曲时的锁占用，我们宁愿输出这一帧静音，也不要崩溃或阻塞喵！
    std::unique_lock<std::mutex> lock(mBufferMutex, std::try_to_lock);
    
    if (!lock.owns_lock() || mAudioBuffer.empty()) {
        memset(floatData, 0, numFrames * mChannelCount * sizeof(float));
        return oboe::DataCallbackResult::Continue;
    }

    int32_t channels = mChannelCount.load();
    int64_t bufferFrames = mAudioBuffer.size() / channels;

    for (int frame = 0; frame < numFrames; ++frame) {
        int64_t readIdx = mCurrentReadFrame.load();

        if (mIsLooping && readIdx >= mLoopEndFrame) {
            readIdx = mLoopStartFrame.load();
            mCurrentReadFrame = readIdx;
        }
        
        if (readIdx >= bufferFrames) {
            for (int c = 0; c < channels; c++) {
                *floatData++ = 0;
            }
        } else {
            for (int c = 0; c < channels; c++) {
                *floatData++ = mAudioBuffer[readIdx * channels + c];
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
