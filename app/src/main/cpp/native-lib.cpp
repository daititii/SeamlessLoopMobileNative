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
Java_com_cpu_seamlessloopmobile_MainActivity_pauseAudioEngine(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine != nullptr) {
        audioEngine->pause();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_resumeAudioEngine(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine != nullptr) {
        audioEngine->resume();
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

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_seekTo(
        JNIEnv* env,
        jobject /* this */,
        jlong frame) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine != nullptr) {
        audioEngine->seekTo(frame);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_getCurrentPosition(
        JNIEnv* env,
        jobject /* this */) {
    // 读操作一般不需要加锁，因为 atomic 很安全，但也取决于具体场景
    // 这里为了不阻塞音频线程太久，我们不加锁直接读 atomic
    if (audioEngine != nullptr) {
        return audioEngine->getCurrentPosition();
    }
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_getDuration(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngine != nullptr) {
        return audioEngine->getDuration();
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_getSampleRate(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngine != nullptr) {
        return static_cast<jint>(audioEngine->getSampleRate());
    }
    return 44100;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from Seamless Loop Engine!";
    return env->NewStringUTF(hello.c_str());
}