#ifndef SEAMLESSLOOPMOBILE_AUDIODECODER_H
#define SEAMLESSLOOPMOBILE_AUDIODECODER_H

#include <string>
#include <vector>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/log.h>

class AudioDecoder {
public:
    static bool decode(int fd, int64_t offset, int64_t length,
                      std::vector<float>& outData, 
                      int32_t& outSampleRate, 
                      int32_t& outChannelCount);
};

#endif //SEAMLESSLOOPMOBILE_AUDIODECODER_H
