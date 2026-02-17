package com.cpu.seamlessloopmobile.model

/**
 * 歌曲实体类
 * 字段结构同步自桌面端数据库 LoopPoints 表，确保采样级循环数据的一致性
 */
data class Song(
    val id: Long = 0,
    val fileName: String,            // 文件名（带后缀）
    val filePath: String,            // 物理路径
    val totalSamples: Long,          // 总采样数（核心识别指纹）
    val displayName: String? = null, // 显示名称（别名）
    val loopStart: Long = 0,         // 循环起始采样点
    val loopEnd: Long = 0,           // 循环结束采样点
    val loopCandidatesJson: String? = null, // 候选循环点缓存 (JSON)
    val lastModified: Long = System.currentTimeMillis(), // 最后修改时间
    
    // 以下为安卓端 UI 展示需要的额外元数据
    val artist: String? = "Unknown Artist",
    val duration: Long = 0,          // 毫秒级时长（用于列表进度显示）
    val isLoopEnabled: Boolean = true
)
