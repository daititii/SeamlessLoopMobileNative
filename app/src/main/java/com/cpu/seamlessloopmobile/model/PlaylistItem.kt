package com.cpu.seamlessloopmobile.model

import androidx.room.*

/**
 * 歌单与歌曲的交叉引用表 (虚拟歌单的核心)
 * 对齐电脑端 PlaylistItems 表结构
 */
@Entity(
    tableName = "PlaylistItems",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["Id"],
            childColumns = ["PlaylistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["Id"],
            childColumns = ["SongId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["PlaylistId"]),
        Index(value = ["SongId"])
    ]
)
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "Id")
    val id: Int = 0,

    @ColumnInfo(name = "PlaylistId")
    val playlistId: Int,

    @ColumnInfo(name = "SongId")
    val songId: Long,

    @ColumnInfo(name = "SortOrder")
    val sortOrder: Int = 0
)
