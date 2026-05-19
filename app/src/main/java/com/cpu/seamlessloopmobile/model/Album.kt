package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Entity(
    tableName = "Albums",
    indices = [Index(value = ["Name"], unique = true)]
)
data class Album(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "Id")
    val id: Long = 0,
    
    @ColumnInfo(name = "Name")
    val name: String,
    
    @ColumnInfo(name = "CoverPath")
    val coverPath: String? = null
)
