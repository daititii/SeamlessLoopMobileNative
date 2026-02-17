#include <jni.h>
#include <string>
#include "AudioEngine.h"

// 全局唯一的音频引擎实例
static AudioEngine *audioEngine = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_startAudioEngine(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngine == nullptr) {
        audioEngine = new AudioEngine();
    }
    // 先启动流（这样才能获取实际采样率）
    audioEngine->start();
    // 再加载音频数据
    audioEngine->loadAudioSource("dummy_path");
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_stopAudioEngine(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngine != nullptr) {
        audioEngine->stop();
        delete audioEngine;
        audioEngine = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_MainActivity_setLoopPoints(
        JNIEnv* env,
        jobject /* this */,
        jlong startFrame,
        jlong endFrame) {
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