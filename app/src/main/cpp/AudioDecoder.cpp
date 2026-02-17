#include "AudioDecoder.h"
#include <unistd.h>

#define LOG_TAG "AudioDecoder"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#include <unistd.h> // 需要 dup
#include <fcntl.h>

bool AudioDecoder::decode(int fd, int64_t offset, int64_t length,
                         std::vector<float>& outData, 
                         int32_t& outSampleRate, 
                         int32_t& outChannelCount) {
    
    // 复制 FD，防止外部过早关闭
    int dupFd = dup(fd);
    if (dupFd < 0) {
        LOGE("Failed to dup FD: %d", fd);
        return false;
    }
    
    AMediaExtractor* extractor = AMediaExtractor_new();
    media_status_t status = AMediaExtractor_setDataSourceFd(extractor, dupFd, offset, length);
    if (status != AMEDIA_OK) {
        LOGE("Failed to set data source via FD");
        AMediaExtractor_delete(extractor);
        close(dupFd); // 记得关闭复制的 FD
        return false;
    }

    AMediaCodec* codec = nullptr;
    AMediaFormat* format = nullptr;
    int32_t trackCount = AMediaExtractor_getTrackCount(extractor);
    
    for (int i = 0; i < trackCount; i++) {
        format = AMediaExtractor_getTrackFormat(extractor, i);
        const char* mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) continue;

        if (strncmp(mime, "audio/", 6) == 0) {
            AMediaExtractor_selectTrack(extractor, i);
            codec = AMediaCodec_createDecoderByType(mime);
            AMediaCodec_configure(codec, format, nullptr, nullptr, 0);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &outSampleRate);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &outChannelCount);
            break;
        }
        AMediaFormat_delete(format);
        format = nullptr;
    }

    if (!codec) {
        LOGE("No audio track found in FD: %d", fd);
        AMediaExtractor_delete(extractor);
        return false;
    }

    AMediaCodec_start(codec);

    bool sawInputEOS = false;
    bool sawOutputEOS = false;
    const int64_t timeoutUs = 10000;

    outData.clear();
    
    while (!sawOutputEOS) {
        if (!sawInputEOS) {
            ssize_t bufIdx = AMediaCodec_dequeueInputBuffer(codec, timeoutUs);
            if (bufIdx >= 0) {
                size_t bufSize;
                uint8_t* buf = AMediaCodec_getInputBuffer(codec, bufIdx, &bufSize);
                ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, buf, bufSize);
                
                if (sampleSize < 0) {
                    sawInputEOS = true;
                    AMediaCodec_queueInputBuffer(codec, bufIdx, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                } else {
                    int64_t presentationTimeUs = AMediaExtractor_getSampleTime(extractor);
                    AMediaCodec_queueInputBuffer(codec, bufIdx, 0, sampleSize, presentationTimeUs, 0);
                    AMediaExtractor_advance(extractor);
                }
            }
        }

        AMediaCodecBufferInfo info;
        ssize_t status = AMediaCodec_dequeueOutputBuffer(codec, &info, timeoutUs);
        if (status >= 0) {
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                sawOutputEOS = true;
            }

            size_t bufSize;
            uint8_t* buf = AMediaCodec_getOutputBuffer(codec, status, &bufSize);
            
            // 假设解码出来的是 16-bit PCM (MediaCodec 默认)
            int16_t* pcmData = reinterpret_cast<int16_t*>(buf + info.offset);
            size_t sampleCount = info.size / sizeof(int16_t);
            
            for (size_t i = 0; i < sampleCount; i++) {
                // 转换为 Float 并归一化到 [-1.0, 1.0]
                outData.push_back(pcmData[i] / 32768.0f);
            }

            AMediaCodec_releaseOutputBuffer(codec, status, false);
        } else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            auto newFormat = AMediaCodec_getOutputFormat(codec);
            AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &outSampleRate);
            AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &outChannelCount);
            LOGD("Format changed: SampleRate %d, Channels %d", outSampleRate, outChannelCount);
            AMediaFormat_delete(newFormat);
        }
    }

    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaFormat_delete(format);
    AMediaExtractor_delete(extractor);
    close(dupFd); // 释放 dup 的 FD

    LOGD("Decoding finished. Total samples: %zu", outData.size());
    return true;
}
