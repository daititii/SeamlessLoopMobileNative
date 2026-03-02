package com.cpu.seamlessloopmobile.jni

object NativeAudio {
    init {
        System.loadLibrary("seamlessloopmobile")
    }

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
}
