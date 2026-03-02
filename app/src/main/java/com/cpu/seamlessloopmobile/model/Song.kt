package com.cpu.seamlessloopmobile.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 歌曲实体类
 * 字段结构同步自桌面端数据库 LoopPoints 表，确保采样级循环数据的一致性
 */
@Entity(
    tableName = "LoopPoints", // 电脑端表名
    indices = [
        Index(value = ["FilePath"]), // 移除 unique=true，允许同步时的空路径共存喵
        Index(value = ["FileName", "duration"], unique = true) // 用文件名+时长作为手机端核心指纹
    ]
)
data class Song(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "Id")
    val id: Long = 0,
    
    // 安卓专用字段，电脑端没有，Room 默认会创建这些列，不影响兼容性
    var mediaId: Long = 0,

    @ColumnInfo(name = "FileName")
    val fileName: String,

    @ColumnInfo(name = "FilePath")
    val filePath: String,

    @ColumnInfo(name = "TotalSamples")
    val totalSamples: Long,

    @ColumnInfo(name = "DisplayName")
    val displayName: String? = null,

    @ColumnInfo(name = "LoopStart")
    val loopStart: Long = 0,

    @ColumnInfo(name = "LoopEnd")
    val loopEnd: Long = 0,
    
    @ColumnInfo(name = "LoopCandidatesJson")
    val loopCandidatesJson: String? = null,
    
    @ColumnInfo(name = "LastModified")
    val lastModified: Long = System.currentTimeMillis(), // 注意：电脑端是 DateTime String，如果直接导入 DB 文件需要 TypeConverter，暂且保持 Long 自身逻辑

    
    // 以下为安卓端 UI 展示需要的额外元数据
    @ColumnInfo(name = "Artist")
    val artist: String? = "Unknown Artist",
    
    @ColumnInfo(name = "Album")
    val album: String? = "Unknown Album",
    
    @ColumnInfo(name = "AlbumArtist")
    val albumArtist: String? = "Unknown Artist",

    val duration: Long = 0,          // 毫秒级时长（用于列表进度显示）
    val isLoopEnabled: Boolean = true
)
