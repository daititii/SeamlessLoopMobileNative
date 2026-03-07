package com.cpu.seamlessloopmobile.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 播放队列项，用于将动态变更的播放列表持久化到数据库中喵！
 */
@Entity(
    tableName = "PlayQueue",
    indices = [
        Index(value = ["SongId"])
    ]
)
data class PlayQueueItem(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "Id")
    val id: Long = 0,

    @ColumnInfo(name = "SongId")
    val songId: Long,

    @ColumnInfo(name = "SortOrder")
    val sortOrder: Int
)
