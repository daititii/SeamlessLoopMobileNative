#ifndef SEAMLESSLOOPMOBILE_AUDIODECODER_H
#define SEAMLESSLOOPMOBILE_AUDIODECODER_H

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <vector>
#include <string>
#include <mutex>

class AudioDecoder {
public:
    AudioDecoder() = default;
    ~AudioDecoder();

    // 准备解码（打开文件，初始化 Codec）
    bool open(int fd, int64_t offset, int64_t length);

    // 从当前位置读取采样数据（流式读取喵！）
    // 返回实际读取到的采样数
    int32_t readSamples(float* targetBuffer, int32_t numSamples);

    // 跳转到指定采样帧位置
    bool seekToFrame(int64_t frameIndex);

    void close();
    
    // 是否解码完毕喵？
    bool isFinished() const;

    // 获取音频元数据
    int32_t getSampleRate() const { return mSampleRate; }
    int32_t getChannelCount() const { return mChannelCount; }
    int64_t getTotalFrames() const { return mTotalFrames; }
    int64_t getCurrentPosition() const { return mCurrentPosition; }

private:
    AMediaExtractor* mExtractor = nullptr;
    AMediaCodec* mCodec = nullptr;
    AMediaFormat* mFormat = nullptr;

    int32_t mSampleRate = 0;
    int32_t mChannelCount = 0;
    int64_t mTotalFrames = 0;
    int64_t mDurationUs = 0;
    int64_t mCurrentPosition = 0; 
    int64_t mSeekTargetFrame = -1; // 新增：记录我们到底想跳到哪喵！

    int mDupFd = -1;

    // 内部缓冲区，暂存从 Codec 吐出来但还没被领走的数据
    std::vector<float> mInternalBuffer;
    size_t mInternalBufferIdx = 0;

    bool mSawInputEOS = false;
    bool mSawOutputEOS = false;

    bool decodeNextBlock(); // 内部函数：驱动解码器工作一小会儿喵
};

#endif //SEAMLESSLOOPMOBILE_AUDIODECODER_H
