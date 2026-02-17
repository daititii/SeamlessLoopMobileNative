#include "AudioDecoder.h"
#include <android/log.h>
#include <unistd.h>

#define LOG_TAG "AudioDecoder"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioDecoder::AudioDecoder() {}

AudioDecoder::~AudioDecoder() {
    close();
}

bool AudioDecoder::open(int fd, int64_t offset, int64_t length) {
    close();

    mDupFd = dup(fd);
    mExtractor = AMediaExtractor_new();
    media_status_t status = AMediaExtractor_setDataSourceFd(mExtractor, mDupFd, offset, length);
    if (status != AMEDIA_OK) {
        LOGE("Failed to set data source");
        return false;
    }

    int numTracks = AMediaExtractor_getTrackCount(mExtractor);
    for (int i = 0; i < numTracks; i++) {
        AMediaFormat* format = AMediaExtractor_getTrackFormat(mExtractor, i);
        const char* mime;
        AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime);

        if (strncmp(mime, "audio/", 6) == 0) {
            AMediaExtractor_selectTrack(mExtractor, i);
            mCodec = AMediaCodec_createDecoderByType(mime);
            AMediaCodec_configure(mCodec, format, nullptr, nullptr, 0);
            AMediaCodec_start(mCodec);

            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &mSampleRate);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &mChannelCount);
            AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &mDurationUs);
            
            mTotalFrames = (mDurationUs / 1000000.0) * mSampleRate;
            mFormat = format;
            LOGD("Stream opened: %d Hz, %d channels, %lld frames", mSampleRate, mChannelCount, (long long)mTotalFrames);
            return true;
        }
        AMediaFormat_delete(format);
    }

    return false;
}

int32_t AudioDecoder::readSamples(float* targetBuffer, int32_t numSamples) {
    int32_t samplesRead = 0;

    while (samplesRead < numSamples) {
        // 1. 如果内部缓冲区还有存货，先清仓
        if (mInternalBufferIdx < mInternalBuffer.size()) {
            size_t available = mInternalBuffer.size() - mInternalBufferIdx;
            size_t toCopy = std::min((size_t)(numSamples - samplesRead), available);
            memcpy(targetBuffer + samplesRead, &mInternalBuffer[mInternalBufferIdx], toCopy * sizeof(float));
            samplesRead += toCopy;
            mInternalBufferIdx += toCopy;
        } else {
            // Buffer 空了，清掉它准备下一块
            mInternalBuffer.clear();
            mInternalBufferIdx = 0;

            // 2. 驱动解码器解码下一块数据
            if (isFinished()) break; 
            
            // 为了解决 Loop 时的 Gap，我们在 buffer 为空时尝试多次解码（预热）喵！
            bool gotData = false;
            for (int retry = 0; retry < 10; retry++) {
                if (decodeNextBlock()) {
                    gotData = true;
                    break;
                }
                if (isFinished()) break;
            }

            if (!gotData) break; 
        }
    }

    return samplesRead;
}

bool AudioDecoder::decodeNextBlock() {
    if (mSawOutputEOS) return false;

    // A. 填充输入缓冲区
    if (!mSawInputEOS) {
        ssize_t inputBufIdx = AMediaCodec_dequeueInputBuffer(mCodec, 2000);
        if (inputBufIdx >= 0) {
            size_t inputBufSize;
            uint8_t* inputBuf = AMediaCodec_getInputBuffer(mCodec, inputBufIdx, &inputBufSize);
            
            ssize_t sampleSize = AMediaExtractor_readSampleData(mExtractor, inputBuf, inputBufSize);
            if (sampleSize < 0) {
                mSawInputEOS = true;
                AMediaCodec_queueInputBuffer(mCodec, inputBufIdx, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
            } else {
                int64_t presentationTimeUs = AMediaExtractor_getSampleTime(mExtractor);
                AMediaCodec_queueInputBuffer(mCodec, inputBufIdx, 0, sampleSize, presentationTimeUs, 0);
                AMediaExtractor_advance(mExtractor);
            }
        }
    }

    // B. 获取输出缓冲区
    AMediaCodecBufferInfo info;
    ssize_t outputBufIdx = AMediaCodec_dequeueOutputBuffer(mCodec, &info, 2000);
    if (outputBufIdx >= 0) {
        if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            mSawOutputEOS = true;
        }

        size_t outputBufSize;
        uint8_t* outputBuf = AMediaCodec_getOutputBuffer(mCodec, outputBufIdx, &outputBufSize);
        
        // 把 16-bit PCM 转换成 float
        int16_t* pcmData = reinterpret_cast<int16_t*>(outputBuf + info.offset);
        size_t numPoints = info.size / sizeof(int16_t);
        size_t numFrames = numPoints / mChannelCount;
        
        // 计算这个 buffer 的起始帧位置喵
        int64_t bufferStartFrame = (info.presentationTimeUs * mSampleRate) / 1000000LL;
        int64_t skipFrames = 0;

        // 如果我们正在寻找精准跳转点喵！
        if (mSeekTargetFrame >= 0) {
            if (bufferStartFrame + (int64_t)numFrames <= mSeekTargetFrame) {
                // 这一整块都在目标点之前，直接扔掉！
                AMediaCodec_releaseOutputBuffer(mCodec, outputBufIdx, false);
                return decodeNextBlock(); 
            } else if (bufferStartFrame < mSeekTargetFrame) {
                // 目标点就在这一块里，切掉前面的部分！
                skipFrames = mSeekTargetFrame - bufferStartFrame;
                mSeekTargetFrame = -1; // 找到了喵！
            } else {
                // 已经跳过了？说明之前的同步点找得有误差
                mSeekTargetFrame = -1;
            }
        }

        size_t startPoint = skipFrames * mChannelCount;
        if (startPoint < numPoints) {
            size_t validPoints = numPoints - startPoint;
            mInternalBuffer.resize(validPoints);
            for (size_t i = 0; i < validPoints; i++) {
                mInternalBuffer[i] = pcmData[startPoint + i] / 32768.0f;
            }
            AMediaCodec_releaseOutputBuffer(mCodec, outputBufIdx, false);
            return true;
        } else {
            AMediaCodec_releaseOutputBuffer(mCodec, outputBufIdx, false);
            return decodeNextBlock();
        }
    } else if (outputBufIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
        auto format = AMediaCodec_getOutputFormat(mCodec);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &mSampleRate);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &mChannelCount);
        AMediaFormat_delete(format);
        return decodeNextBlock(); // 递归解下一块
    }

    return false;
}

bool AudioDecoder::seekToFrame(int64_t frameIndex) {
    mSeekTargetFrame = frameIndex;
    int64_t seekTimeUs = (frameIndex * 1000000LL) / mSampleRate;
    
    // 使用 PREVIOUS_SYNC，我们要确保跳到目标点之前，然后靠解析丢弃多余帧来达到精准位置喵！
    AMediaExtractor_seekTo(mExtractor, seekTimeUs, AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC);
    AMediaCodec_flush(mCodec);
    
    mInternalBuffer.clear();
    mInternalBufferIdx = 0;
    mSawInputEOS = false;
    mSawOutputEOS = false;
    return true;
}

bool AudioDecoder::isFinished() const {
    return mSawOutputEOS && mInternalBuffer.empty();
}

void AudioDecoder::close() {
    if (mCodec) {
        AMediaCodec_stop(mCodec);
        AMediaCodec_delete(mCodec);
        mCodec = nullptr;
    }
    if (mExtractor) {
        AMediaExtractor_delete(mExtractor);
        mExtractor = nullptr;
    }
    if (mFormat) {
        AMediaFormat_delete(mFormat);
        mFormat = nullptr;
    }
    if (mDupFd != -1) {
        ::close(mDupFd);
        mDupFd = -1;
    }
    mInternalBuffer.clear();
    mSawInputEOS = mSawOutputEOS = false;
}
