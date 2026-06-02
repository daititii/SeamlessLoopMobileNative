#include <jni.h>
#include <string>
#include <mutex>
#include "AudioEngine.h"

// 全局唯一的音频引擎实例和保护它的锁喵！
static AudioEngine *audioEngine = nullptr;
static std::mutex engineMutex; 

// JNI 回调相关的全局引用喵！
static JavaVM* g_jvm = nullptr;
static jobject g_nativeAudioObj = nullptr;
static jmethodID g_onNativeEventMethod = nullptr;
static std::mutex g_callbackMutex;

// JNI_OnLoad 让我们拿到 JavaVM 喵！
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// 跨线程发送 JNI 事件的工具函数喵！
void sendNativeEvent(int type) {
    std::lock_guard<std::mutex> lock(g_callbackMutex);
    if (g_jvm == nullptr || g_nativeAudioObj == nullptr || g_onNativeEventMethod == nullptr) return;

    JNIEnv* env;
    bool attached = false;
    int res = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    
    if (res == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, NULL) != 0) {
            LOGE("Failed to attach current thread to JVM");
            return;
        }
        attached = true;
    } else if (res != JNI_OK) {
        LOGE("Failed to get JNIEnv from JVM");
        return;
    }

    env->CallStaticVoidMethod((jclass)env->GetObjectClass(g_nativeAudioObj), g_onNativeEventMethod, (jint)type);

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_setEventListenerNative(
        JNIEnv* env,
        jobject thiz,
        jboolean enabled) {
    std::lock_guard<std::mutex> lock(g_callbackMutex);
    
    if (enabled) {
        if (g_nativeAudioObj != nullptr) {
            env->DeleteGlobalRef(g_nativeAudioObj);
        }
        g_nativeAudioObj = env->NewGlobalRef(thiz);
        jclass clazz = env->GetObjectClass(g_nativeAudioObj);
        g_onNativeEventMethod = env->GetStaticMethodID(clazz, "onNativeEvent", "(I)V");
        if (g_onNativeEventMethod == nullptr) {
            LOGE("Failed to find onNativeEvent method");
        }
    } else {
        if (g_nativeAudioObj != nullptr) {
            env->DeleteGlobalRef(g_nativeAudioObj);
            g_nativeAudioObj = nullptr;
        }
        g_onNativeEventMethod = nullptr;
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_startAudioEngine(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jlong offset,
        jlong length) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine == nullptr) {
        audioEngine = new AudioEngine();
        audioEngine->setEventCallback([](int type) {
            sendNativeEvent(type);
        });
    }
    
    // 加载音频数据（通过文件描述符）
    audioEngine->loadAudioSource(fd, offset, length);
    // 启动流
    audioEngine->start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_startAbAudioEngine(
        JNIEnv* env,
        jobject /* this */,
        jint fdA,
        jlong offsetA,
        jlong lengthA,
        jint fdB,
        jlong offsetB,
        jlong lengthB,
        jboolean isFeatureLoopEnabled) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine == nullptr) {
        audioEngine = new AudioEngine();
        audioEngine->setEventCallback([](int type) {
            sendNativeEvent(type);
        });
    }
    
    audioEngine->loadAbAudioSource(fdA, offsetA, lengthA, fdB, offsetB, lengthB, (bool)isFeatureLoopEnabled);
    audioEngine->start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_stopAudioEngine(
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
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_pauseAudioEngine(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine != nullptr) {
        audioEngine->pause();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_resumeAudioEngine(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine != nullptr) {
        audioEngine->resume();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_setLoopPoints(
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
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_setLooping(
        JNIEnv* env,
        jobject /* this */,
        jboolean isLooping) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine != nullptr) {
        audioEngine->setLooping(isLooping);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_seekTo(
        JNIEnv* env,
        jobject /* this */,
        jlong frame) {
    std::lock_guard<std::mutex> lock(engineMutex);
    
    if (audioEngine != nullptr) {
        audioEngine->seekTo(frame);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_getCurrentPosition(
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
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_getDuration(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngine != nullptr) {
        return audioEngine->getDuration();
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_getSampleRate(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngine != nullptr) {
        return static_cast<jint>(audioEngine->getSampleRate());
    }
    return 44100;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_isPlaying(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngine != nullptr) {
        return audioEngine->isPlaying();
    }
    return false;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_getAudioFileDuration(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jlong offset,
        jlong length) {
    AudioDecoder scanner;
    if (scanner.open(fd, offset, length)) {
        scanner.prime(); // 强制解码第一批数据以更新 mTotalFrames 喵！
        jlong frames = scanner.getTotalFrames();
        scanner.close();
        return frames;
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_getAudioFileSampleRate(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jlong offset,
        jlong length) {
    AudioDecoder scanner;
    if (scanner.open(fd, offset, length)) {
        scanner.prime(); // 强制解码第一批数据以触发 format changed 并获取正确采样率 喵！
        jint sampleRate = scanner.getSampleRate();
        scanner.close();
        return sampleRate;
    }
    return 44100;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cpu_seamlessloopmobile_jni_NativeAudio_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from Seamless Loop Engine!";
    return env->NewStringUTF(hello.c_str());
}