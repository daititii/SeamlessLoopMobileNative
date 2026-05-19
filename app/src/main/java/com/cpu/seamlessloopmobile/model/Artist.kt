package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Entity(
    tableName = "Artists",
    indices = [Index(value = ["Name"], unique = true)]
)
data class Artist(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "Id")
    val id: Long = 0,
    
    @ColumnInfo(name = "Name")
    val name: String,
    
    @ColumnInfo(name = "CoverPath")
    val coverPath: String? = null
)
