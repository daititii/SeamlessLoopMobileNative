package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Entity(
    tableName = "UserRatings",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["Id"],
            childColumns = ["SongId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserRating(
    @PrimaryKey
    @ColumnInfo(name = "SongId")
    val songId: Long,
    
    @ColumnInfo(name = "Rating")
    val rating: Int,
    
    @ColumnInfo(name = "LastModified")
    val lastModified: Long = System.currentTimeMillis()
)
