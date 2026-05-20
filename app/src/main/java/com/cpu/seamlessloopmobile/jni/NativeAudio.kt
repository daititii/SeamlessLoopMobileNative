package com.cpu.seamlessloopmobile.jni

object NativeAudio {
    init {
        System.loadLibrary("seamlessloopmobile")
        System.loadLibrary("loopfinder")
    }

    // 事件常量喵！
    const val EVENT_EOS = 1
    const val EVENT_LOOP_JUMP = 2

    interface NativeEventListener {
        fun onEvent(type: Int)
    }

    private var listener: NativeEventListener? = null

    fun setEventListener(l: NativeEventListener?) {
        listener = l
        setEventListenerNative(l != null)
    }

    // 由 JNI 调用喵！
    @JvmStatic
    private fun onNativeEvent(type: Int) {
        android.util.Log.d("NativeAudio", "📡 收到底层事件: $type")
        listener?.onEvent(type)
    }

    private external fun setEventListenerNative(enabled: Boolean)

    external fun stringFromJNI(): String
    external fun startAudioEngine(fd: Int, offset: Long, length: Long)
    external fun startAbAudioEngine(fdA: Int, offsetA: Long, lengthA: Long, fdB: Int, offsetB: Long, lengthB: Long)
    external fun stopAudioEngine()
    external fun setLoopPoints(start: Long, end: Long)
    external fun setLooping(isLooping: Boolean)
    external fun seekTo(frame: Long)
    external fun getCurrentPosition(): Long
    external fun getDuration(): Long
    external fun getSampleRate(): Int
    external fun pauseAudioEngine()
    external fun resumeAudioEngine()
    external fun isPlaying(): Boolean
    
    // 用于扫描阶段快速获取绝对准确的总采样数喵！
    external fun getAudioFileDuration(fd: Int, offset: Long, length: Long): Long
    // 用于扫描阶段获取文件的实际采样率喵！
    external fun getAudioFileSampleRate(fd: Int, offset: Long, length: Long): Int

    // 自动寻找循环点喵！
    @JvmStatic
    external fun analyzeLoopPoints(filePath: String, topN: Int): Array<LoopPoint>?
}
