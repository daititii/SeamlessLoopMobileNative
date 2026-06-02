package com.cpu.seamlessloopmobile.viewmodel

import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.jni.LoopPoint

/**
 * 全局对话框中台状态喵！
 */
sealed class MusicDialog {
    // 1. 循环点编辑 (迁移 PlayingPanel 的弹窗)
    data class LoopEdit(
        val isStart: Boolean,
        val initialSamples: Long,
        val onConfirm: (Long) -> Unit
    ) : MusicDialog()

    // 2. 创建歌单 (迁移 MainScreen 的弹窗)
    data class CreatePlaylist(val onConfirm: (String) -> Unit) : MusicDialog()

    // 3. 添加到现有歌单
    data class AddToPlaylist(
        val playlists: List<Playlist>, 
        val onAdd: (Playlist) -> Unit, 
        val onCreateNew: () -> Unit
    ) : MusicDialog()

    // 4. 导入文件夹选择
    data class ImportFoldersOptions(
        val count: Int, 
        val onIndividual: () -> Unit, 
        val onMerge: () -> Unit
    ) : MusicDialog()

    // 5. 合并文件夹命名
    data class MergeFoldersName(val onConfirm: (String) -> Unit) : MusicDialog()

    // 6. 确认删除歌单
    data class ConfirmDeletePlaylist(
        val playlist: Playlist, 
        val onConfirm: () -> Unit
    ) : MusicDialog()

    // 7. 自动探测循环点候选列表弹窗
    data class LoopCandidates(
        val song: Song,
        val candidates: List<LoopPoint>,
        val sampleRate: Int,
        val onSelect: (LoopPoint) -> Unit,
        val onReanalyze: () -> Unit
    ) : MusicDialog()

    // 8. 歌曲更多操作选项弹窗
    data class SongMoreOptions(
        val song: Song,
        val playlistId: Int?
    ) : MusicDialog()

    // 9. 歌曲详细信息弹窗
    data class SongInfo(val song: Song) : MusicDialog()

    // 10. 歌曲多选更多操作选项弹窗
    data class BulkMoreOptions(
        val count: Int,
        val playlistId: Int?
    ) : MusicDialog()
}
