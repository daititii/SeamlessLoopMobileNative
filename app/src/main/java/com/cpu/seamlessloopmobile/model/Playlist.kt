package com.cpu.seamlessloopmobile.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 虚拟歌单实体
 * 对齐电脑端 Playlists 表结构
 */
@Entity(tableName = "Playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "Id")
    val id: Int = 0,

    @ColumnInfo(name = "Name")
    val name: String,

    @ColumnInfo(name = "FolderPath")
    val folderPath: String? = null,

    @ColumnInfo(name = "IsFolderLinked")
    val isFolderLinked: Int = 0,

    @ColumnInfo(name = "SortOrder")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "CreatedAt")
    val createdAt: Long = System.currentTimeMillis()
)
