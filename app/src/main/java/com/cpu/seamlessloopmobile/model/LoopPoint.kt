package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Entity(
    tableName = "LoopPoints",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["Id"],
            childColumns = ["SongId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LoopPoint(
    @PrimaryKey
    @ColumnInfo(name = "SongId")
    val songId: Long,
    
    @ColumnInfo(name = "LoopStart")
    val loopStart: Long,
    
    @ColumnInfo(name = "LoopEnd")
    val loopEnd: Long
)
