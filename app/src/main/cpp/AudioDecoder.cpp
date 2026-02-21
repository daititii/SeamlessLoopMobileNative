#include "AudioDecoder.h"
#include <android/log.h>
#include <unistd.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "AudioDecoder"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 构造函数已在头文件中默认化
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
            
            // 明确要求输出 16-bit PCM，防止有些设备默认输出 Float 导致杂音喵！
            AMediaFormat_setInt32(format, "pcm-encoding", 2); // 2 = kAudioFormatPcm16Bit
            
            AMediaCodec_configure(mCodec, format, nullptr, nullptr, 0);
            AMediaCodec_start(mCodec);

            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &mSampleRate);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &mChannelCount);
            AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &mDurationUs);
            
            if (!AMediaFormat_getInt32(format, "pcm-encoding", &mPcmEncoding)) {
                mPcmEncoding = 2; // 默认 2 = 16-bit PCM
            }
            
            // 使用 round 保证总帧数万无一失喵！
            mTotalFrames = static_cast<int64_t>(std::round((static_cast<double>(mDurationUs) * mSampleRate) / 1000000.0));
            mFormat = format;
            mCurrentPosition = 0;
            LOGD("Stream opened: %d Hz, %d channels, %lld frames, encoding: %d", mSampleRate, mChannelCount, (long long)mTotalFrames, mPcmEncoding);
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
            auto toCopy = std::min(static_cast<size_t>(numSamples - samplesRead), available);
            memcpy(targetBuffer + samplesRead, &mInternalBuffer[mInternalBufferIdx], toCopy * sizeof(float));
            samplesRead += static_cast<int32_t>(toCopy);
            mInternalBufferIdx += toCopy;
        } else {
            // Buffer 空了，清掉它准备下一块
            mInternalBuffer.clear();
            mInternalBufferIdx = 0;

            // 2. 驱动解码器解码下一块数据
            if (isFinished()) break; 
            
            // 为了解决 Loop 时的 Gap，我们在 buffer 为空时尝试多次解码（预热）喵！
            // 提高到 50 次，应对某些“懒惰”的解码器
            bool gotData = false;
            for (int retry = 0; retry < 50; retry++) {
                if (decodeNextBlock()) {
                    gotData = true;
                    break;
                }
                if (isFinished()) break;
            }

            if (!gotData) break; 
        }
    }

    int32_t finalSamplesRead = samplesRead;
    mCurrentPosition += (finalSamplesRead / mChannelCount);
    return finalSamplesRead;
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
        
        // 兼容处理 16-bit PCM 跟 32-bit Float 喵！
        bool isFloat = (mPcmEncoding == 4); // 4 = ENCODING_PCM_FLOAT
        size_t bytesPerPoint = isFloat ? 4 : 2;
        size_t numPoints = info.size / bytesPerPoint;
        size_t numFrames = numPoints / mChannelCount;
        
        // 使用 round 消除转换抖动，这是接缝完美的关键喵！
        auto bufferStartFrame = static_cast<int64_t>(std::round((static_cast<double>(info.presentationTimeUs) * mSampleRate) / 1000000.0));
        int64_t skipFrames = 0;

        // 精准寻找目标点喵！
        if (mSeekTargetFrame >= 0) {
            if (bufferStartFrame + (int64_t)numFrames <= mSeekTargetFrame) {
                // 这一整块都在目标点之前
                AMediaCodec_releaseOutputBuffer(mCodec, outputBufIdx, false);
                return decodeNextBlock(); 
            } else if (bufferStartFrame < mSeekTargetFrame) {
                // 目标点就在这一块里
                skipFrames = mSeekTargetFrame - bufferStartFrame;
                mSeekTargetFrame = -1;
            } else {
                mSeekTargetFrame = -1;
            }
        }

        size_t startPoint = skipFrames * mChannelCount;
        if (startPoint < numPoints) {
            size_t validPoints = numPoints - startPoint;
            mInternalBuffer.resize(validPoints);
            
            if (isFloat) {
                // 如果是 Float 格式，直接按 4 字节读喵！
                float* pcmData = reinterpret_cast<float*>(outputBuf + info.offset);
                for (size_t i = 0; i < validPoints; i++) {
                    mInternalBuffer[i] = pcmData[startPoint + i];
                }
            } else {
                // 如果是 16 位整数，手动组装字节防爆音喵！
                for (size_t i = 0; i < validPoints; i++) {
                    size_t byteOffset = info.offset + (startPoint + i) * 2;
                    uint8_t b1 = outputBuf[byteOffset];
                    uint8_t b2 = outputBuf[byteOffset + 1];
                    int16_t s = static_cast<int16_t>(b1 | (b2 << 8));
                    mInternalBuffer[i] = static_cast<float>(s) / 32768.0f;
                }
            }
            
            AMediaCodec_releaseOutputBuffer(mCodec, outputBufIdx, false);
            return true;
        } else {
            AMediaCodec_releaseOutputBuffer(mCodec, outputBufIdx, false);
            return decodeNextBlock();
        }
    } else if (outputBufIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
        auto format = AMediaCodec_getOutputFormat(mCodec);
        int32_t sampleRate = 44100, channels = 2, encoding = 2;
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels);
        
        if (AMediaFormat_getInt32(format, "pcm-encoding", &encoding)) {
             mPcmEncoding = encoding;
        } else {
             mPcmEncoding = 2;
        }
        
        LOGD("Decoder format changed: %d Hz, %d channels, encoding: %d", sampleRate, channels, mPcmEncoding);
        
        mSampleRate = sampleRate;
        mChannelCount = channels;
        
        AMediaFormat_delete(format);
        return decodeNextBlock(); 
    }

    return false;
}

bool AudioDecoder::seekToFrame(int64_t frameIndex) {
    mSeekTargetFrame = frameIndex;
    mCurrentPosition = frameIndex;
    // 跳转时间也要用 double 算，精准到极致喵！
    int64_t seekTimeUs = static_cast<int64_t>(std::round((static_cast<double>(frameIndex) * 1000000.0) / mSampleRate));
    
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
