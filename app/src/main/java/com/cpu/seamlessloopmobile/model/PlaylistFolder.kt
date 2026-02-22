package com.cpu.seamlessloopmobile.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 歌单同步文件夹
 * 对齐电脑端 PlaylistFolders 表结构
 */
@Entity(
    tableName = "PlaylistFolders",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["Id"],
            childColumns = ["PlaylistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["PlaylistId", "FolderPath"], unique = true)
    ]
)
data class PlaylistFolder(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "Id")
    val id: Int = 0,

    @ColumnInfo(name = "PlaylistId")
    val playlistId: Int,

    @ColumnInfo(name = "FolderPath")
    val folderPath: String,

    @ColumnInfo(name = "AddedAt")
    val addedAt: Long = System.currentTimeMillis()
)
