#include <jni.h>
#include <string>
#include <mutex>
#include "AudioEngine.h"

// 全局唯一的音频引擎实例和保护它的锁喵！
static AudioEngine *audioEngine = nullptr;
static std::mutex engineMutex; 

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_startAudioEngine(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jlong offset,
        jlong length) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine == nullptr) {
        audioEngine = new AudioEngine();
    }
    
    // 加载音频数据（通过文件描述符）
    audioEngine->loadAudioSource(fd, offset, length);
    // 启动流
    audioEngine->start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_stopAudioEngine(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine != nullptr) {
        LOGD("Stopping and deleting AudioEngine...");
        audioEngine->stop(); // 这个方法里有 mStream->close()
        delete audioEngine;
        audioEngine = nullptr;
        LOGD("AudioEngine deleted successfully.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_setLoopPoints(
        JNIEnv* env,
        jobject /* this */,
        jlong startFrame,
        jlong endFrame) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine != nullptr) {
        audioEngine->setLoopPoints(startFrame, endFrame);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from Seamless Loop Engine!";
    return env->NewStringUTF(hello.c_str());
}