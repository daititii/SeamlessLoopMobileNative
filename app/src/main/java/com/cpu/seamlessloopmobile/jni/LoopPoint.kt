package com.cpu.seamlessloopmobile.jni

data class LoopPoint(
    val loopStart: Long,    // 采样点 (相对原始文件)
    val loopEnd: Long,      // 采样点 (相对原始文件)
    val noteDiff: Float,    // 音符距离
    val loudnessDiff: Float,// 响度差异
    val score: Float        // 余弦相似度评分
)
