#include "AudioDecoder.h"
#include <android/log.h>
#include <unistd.h>
#include <cmath>
#include <algorithm>

// 大神降临！我们要在编译时告诉 minimp3 把音频解成 Float，完美对接 Oboe
#define MINIMP3_FLOAT_OUTPUT
#define MINIMP3_IMPLEMENTATION
#include "minimp3_ex.h"

#define LOG_TAG "AudioDecoder"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 用来给大神的库装载系统回调的容器喵！
struct Mp3DecoderContext {
    mp3dec_ex_t dec;
    mp3dec_io_t io;
    int fd;
    int64_t offset;
    int64_t length;
    int64_t position;
};

static size_t mp3dec_read_cb(void *buf, size_t size, void *user_data) {
    Mp3DecoderContext* ctx = (Mp3DecoderContext*)user_data;
    if (ctx->position >= ctx->length) return 0;
    size_t to_read = size;
    if (ctx->position + to_read > ctx->length) {
        to_read = ctx->length - ctx->position;
    }
    ssize_t bytes_read = pread(ctx->fd, buf, to_read, ctx->offset + ctx->position);
    if (bytes_read > 0) {
        ctx->position += bytes_read;
        return bytes_read;
    }
    return 0;
}

static int mp3dec_seek_cb(uint64_t position, void *user_data) {
    Mp3DecoderContext* ctx = (Mp3DecoderContext*)user_data;
    if (position > ctx->length) return -1;
    ctx->position = position;
    return 0;
}

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
            
            // 抓到一只 MP3！莱芙掏出了神器喵！
            if (strncmp(mime, "audio/mpeg", 10) == 0 || strncmp(mime, "audio/mp3", 9) == 0) {
                mIsMp3 = true;
                AMediaFormat_delete(format);
                
                Mp3DecoderContext* ctx = new Mp3DecoderContext();
                ctx->fd = mDupFd;
                ctx->offset = offset;
                ctx->length = length;
                ctx->position = 0;
                ctx->io.read = mp3dec_read_cb;
                ctx->io.read_data = ctx;
                ctx->io.seek = mp3dec_seek_cb;
                ctx->io.seek_data = ctx;
                
                // MP3D_SEEK_TO_SAMPLE 会在打开时快速扫描一遍全曲，建立精准样本索引，自动抵消 padding！
                int res = mp3dec_ex_open_cb(&ctx->dec, &ctx->io, MP3D_SEEK_TO_SAMPLE);
                if (res != 0) {
                    delete ctx;
                    LOGE("minimp3 open failed with code: %d", res);
                    return false;
                }
                mMp3Decoder = ctx;
                
                mSampleRate = ctx->dec.info.hz;
                mChannelCount = ctx->dec.info.channels;
                // dec.samples = 所有的有效样本点（去除了 delay 和 padding）
                mTotalFrames = ctx->dec.samples / mChannelCount;
                mCurrentPosition = 0;
                mSawOutputEOS = false;
                LOGD("minimp3 opened: %d Hz, %d channels, %lld frames (Gapless Native!)", mSampleRate, mChannelCount, (long long)mTotalFrames);
                return true;
            }

            mIsMp3 = false;
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
            
            if (!AMediaFormat_getInt32(format, "encoder-delay", &mEncoderDelay)) mEncoderDelay = 0;
            if (!AMediaFormat_getInt32(format, "encoder-padding", &mEncoderPadding)) mEncoderPadding = 0;
            
            // 莱芙再尝试一下其他可能的 key 名，有些设备不按套路出牌喵！
            int32_t altDelay = 0;
            if (AMediaFormat_getInt32(format, "delay", &altDelay) && mEncoderDelay == 0) mEncoderDelay = altDelay;
            int32_t altPadding = 0;
            if (AMediaFormat_getInt32(format, "padding", &altPadding) && mEncoderPadding == 0) mEncoderPadding = altPadding;

            // 使用 round 保证总帧数万无一失喵！
            mTotalFrames = static_cast<int64_t>(std::round((static_cast<double>(mDurationUs) * mSampleRate) / 1000000.0));
            // 扣除前后多余样本！这非常重要喵！
            mTotalFrames = mTotalFrames - mEncoderDelay - mEncoderPadding;
            if (mTotalFrames < 0) mTotalFrames = 0;

            mFormat = format;
            mCurrentPosition = 0;
            mIsFirstBlockAfterSeek = true;
            mLastBufferEndFrame = 0; 
            // 第一次播放也等于从 0 寻轨，这样会自动扔掉 mEncoderDelay 个前置无用样本喵！
            mSeekTargetAndroidFrame = mEncoderDelay; 
            LOGD("Stream opened: %s, %d Hz, %d ch, %lld frames", mime, mSampleRate, mChannelCount, (long long)mTotalFrames);
            LOGD("Gapless Info: delay=%d, padding=%d (from extractor)", mEncoderDelay, mEncoderPadding);
            return true;
        }
        AMediaFormat_delete(format);
    }

    return false;
}

int32_t AudioDecoder::readSamples(float* targetBuffer, int32_t numSamples) {
    if (mIsMp3 && mMp3Decoder) {
        Mp3DecoderContext* ctx = (Mp3DecoderContext*)mMp3Decoder;
        size_t read = mp3dec_ex_read(&ctx->dec, targetBuffer, numSamples);
        mCurrentPosition += (read / mChannelCount);
        if (read == 0 && numSamples > 0) {
            mSawOutputEOS = true;
        }
        return read;
    }

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
            // 提高到 200 次，应对提前寻轨导致的疯狂丢帧喵
            bool gotData = false;
            for (int retry = 0; retry < 200; retry++) {
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
    while (true) {
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
        
        // 如果是跳转后的第一块，我们必须参考时间戳来对齐。
        // 之后的块我们直接累加，防止 MP3 等格式的时间戳随机抖动导致接缝不准喵！
        int64_t bufferStartFrame;
        
        if (mIsFirstBlockAfterSeek) {
            bufferStartFrame = static_cast<int64_t>(std::round((static_cast<double>(info.presentationTimeUs) * mSampleRate) / 1000000.0));
            mIsFirstBlockAfterSeek = false;
        } else {
            bufferStartFrame = mLastBufferEndFrame;
        }

        int64_t skipFrames = 0;

        // 精准寻找目标点喵！
        if (mSeekTargetAndroidFrame >= 0) {
            if (bufferStartFrame + (int64_t)numFrames <= mSeekTargetAndroidFrame) {
                // 这一整块都在目标点之前
                mLastBufferEndFrame = bufferStartFrame + numFrames;
                AMediaCodec_releaseOutputBuffer(mCodec, outputBufIdx, false);
                continue; 
            } else if (bufferStartFrame < mSeekTargetAndroidFrame) {
                // 目标点就在这一块里
                skipFrames = mSeekTargetAndroidFrame - bufferStartFrame;
                mSeekTargetAndroidFrame = -1;
            } else {
                // 已经跑过了目标点（这通常是因为跳转后第一块给出的 PST 就比目标大喵）
                mSeekTargetAndroidFrame = -1;
            }
        }
        
        mLastBufferEndFrame = bufferStartFrame + numFrames;

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
            continue;
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
        continue; 
    } else if (outputBufIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
        return false;
    }

    return false;
    }
}

bool AudioDecoder::seekToFrame(int64_t frameIndex) {
    if (mIsMp3 && mMp3Decoder) {
        Mp3DecoderContext* ctx = (Mp3DecoderContext*)mMp3Decoder;
        int res = mp3dec_ex_seek(&ctx->dec, frameIndex * mChannelCount);
        mCurrentPosition = frameIndex;
        mSawOutputEOS = false;
        return res == 0;
    }

    mCurrentPosition = frameIndex;
    
    // Android 原生解码器会输出前置空白（encoder delay），
    // 我们的 frameIndex 是 PC 端剪裁后的“纯净”位置。
    // 所以物理跳转目标要加上 encoder delay 喵！
    int64_t physicalTarget = frameIndex + mEncoderDelay;
    mSeekTargetAndroidFrame = physicalTarget;
    
    // 跳转时间也要用 double 算，精准到极致喵！
    int64_t seekTimeUs = static_cast<int64_t>(std::round((static_cast<double>(physicalTarget) * 1000000.0) / mSampleRate));
    
    // DELIBERATE PRE-SEEK: 增加提前量，降低由于 MP3 寻轨不准导致的“落点靠后”问题，我们强制提前 1 秒起跳，
    // 然后用解码时自带的精确丢弃机制跑到完美采样点喵！
    int64_t safeSeekTimeUs = seekTimeUs - 1000000; 
    if (safeSeekTimeUs < 0) safeSeekTimeUs = 0;

    // 使用 PREVIOUS_SYNC，我们要确保跳到目标点之前，然后靠解析丢弃多余帧来达到精准位置喵！
    AMediaExtractor_seekTo(mExtractor, safeSeekTimeUs, AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC);
    AMediaCodec_flush(mCodec);
    
    mInternalBuffer.clear();
    mInternalBufferIdx = 0;
    mLastBufferEndFrame = 0;
    mSawInputEOS = false;
    mSawOutputEOS = false;
    mIsFirstBlockAfterSeek = true;
    
    // 强制解码，直到第一批有效数据进入 mInternalBuffer，实现真正的接棒就绪！
    prime();
    return true;
}

bool AudioDecoder::prime() {
    if (mIsMp3) return true; // minimp3 指哪打哪，不需要预热！

    if (mInternalBufferIdx < mInternalBuffer.size()) return true;
    if (isFinished()) return false;
    
    for (int retry = 0; retry < 200; retry++) {
        if (decodeNextBlock()) return true;
        if (isFinished()) return false;
    }
    return false;
}

bool AudioDecoder::isFinished() const {
    if (mIsMp3) return mSawOutputEOS;
    return mSawOutputEOS && mInternalBuffer.empty();
}

void AudioDecoder::close() {
    if (mIsMp3 && mMp3Decoder) {
        Mp3DecoderContext* ctx = (Mp3DecoderContext*)mMp3Decoder;
        mp3dec_ex_close(&ctx->dec);
        delete ctx;
        mMp3Decoder = nullptr;
    }
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
