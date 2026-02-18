package com.cpu.seamlessloopmobile.model

/**
 * 文件夹实体类
 * 用于在主页展示文件夹列表
 */
data class Folder(
    val name: String,
    val path: String,
    val songCount: Int,
    val songs: List<Song>
)
