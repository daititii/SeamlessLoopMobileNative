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

    // 强制解码，直到底部有第一批可用的 PCM 数据，用于接棒预热喵！
    bool prime();

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
    int64_t mCurrentPosition = 0; // PC空间时间，不包含 delay 喵！
    int64_t mSeekTargetAndroidFrame = -1; // 真正的物理帧位置喵！

    int32_t mEncoderDelay = 0;
    int32_t mEncoderPadding = 0;

    int mDupFd = -1;
    int32_t mPcmEncoding = 2; // 默认 PCM_16BIT 喵！

    // 内部缓冲区，暂存从 Codec 吐出来但还没被领走的数据
    std::vector<float> mInternalBuffer;
    size_t mInternalBufferIdx = 0;
    int64_t mLastBufferEndFrame = 0;

    bool mSawInputEOS = false;
    bool mSawOutputEOS = false;
    bool mIsFirstBlockAfterSeek = true; // 新增：标记是否为跳转后的第一块数据喵！
    
    // MP3 专属的大神武器喵！为了不让头文件变得太胖，莱芙用 void* 来伪装它
    bool mIsMp3 = false;
    void* mMp3Decoder = nullptr;

    bool decodeNextBlock(); // 内部函数：驱动解码器工作一小会儿喵
};

#endif //SEAMLESSLOOPMOBILE_AUDIODECODER_H
