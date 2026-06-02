package com.cpu.seamlessloopmobile.ui.components.app

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.cpu.seamlessloopmobile.viewmodel.MusicDialog
import com.cpu.seamlessloopmobile.ui.components.dialogs.*

/**
 * 全局对话框中台托管中心喵！(๑•̀ㅂ•́)و✧
 * 已瘦身，仅保留 Router 分发逻辑，所有具体 UI 已剥离至 dialogs 包下喵！🚀
 */
@Composable
fun CentralizedDialogHost(viewModel: MainViewModel) {
    val currentDialog by viewModel.currentDialog.observeAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedItems by viewModel.selectedItems.observeAsState(emptySet())
    val context = androidx.compose.ui.platform.LocalContext.current

    when (val dialog = currentDialog) {
        is MusicDialog.LoopEdit -> {
            LoopEditDialogWrapper(
                dialog = dialog,
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is MusicDialog.CreatePlaylist -> {
            CreatePlaylistDialog(
                dialog = dialog,
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is MusicDialog.AddToPlaylist -> {
            AddToPlaylistDialog(
                dialog = dialog,
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is MusicDialog.ImportFoldersOptions -> {
            ImportFoldersOptionsDialog(
                dialog = dialog,
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is MusicDialog.MergeFoldersName -> {
            MergeFoldersNameDialog(
                dialog = dialog,
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is MusicDialog.ConfirmDeletePlaylist -> {
            ConfirmDeletePlaylistDialog(
                dialog = dialog,
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is MusicDialog.LoopCandidates -> {
            com.cpu.seamlessloopmobile.ui.components.common.LoopCandidatesDialog(
                candidates = dialog.candidates,
                sampleRate = dialog.sampleRate,
                onSelect = dialog.onSelect,
                onReanalyze = dialog.onReanalyze,
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is MusicDialog.SongMoreOptions -> {
            val allSongs by viewModel.allSongs.collectAsState()
            val latestSong = remember(allSongs, dialog.song) {
                allSongs.find { it.id == dialog.song.id } ?: dialog.song
            }
            SongMoreOptionsBottomSheet(
                song = latestSong,
                onDismissRequest = { viewModel.dismissDialog() },
                onAddToPlaylist = {
                    viewModel.showDialog(
                        MusicDialog.AddToPlaylist(
                            playlists = playlists,
                            onAdd = { p -> 
                                viewModel.addSongToPlaylist(p.id, latestSong)
                            },
                            onCreateNew = {
                                viewModel.showDialog(
                                    MusicDialog.CreatePlaylist { name ->
                                        viewModel.createPlaylistWithSong(name, latestSong)
                                    }
                                )
                            }
                        )
                    )
                },
                onDetectLoop = {
                    viewModel.dismissDialog()
                    viewModel.detectLoopPoints(context, latestSong)
                },
                onToggleFavorite = { viewModel.cycleSongRating(latestSong) },
                onRemoveFromPlaylist = dialog.playlistId?.let { pid ->
                    {
                        viewModel.removeSongFromPlaylist(pid, latestSong)
                        viewModel.dismissDialog()
                    }
                },
                onShowInfo = {
                    viewModel.showDialog(MusicDialog.SongInfo(latestSong))
                }
            )
        }

        is MusicDialog.SongInfo -> {
            SongInfoDialog(
                context = context,
                song = dialog.song,
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is MusicDialog.BulkMoreOptions -> {
            BulkMoreOptionsBottomSheet(
                count = dialog.count,
                onDismissRequest = { viewModel.dismissDialog() },
                onAddToPlaylist = {
                    viewModel.showDialog(
                        MusicDialog.AddToPlaylist(
                            playlists = playlists,
                            onAdd = { p -> 
                                viewModel.addSelectedToPlaylist(p.id)
                            },
                            onCreateNew = {
                                viewModel.showDialog(
                                    MusicDialog.CreatePlaylist { name ->
                                        viewModel.createPlaylistWithSelected(name)
                                    }
                                )
                            }
                        )
                    )
                },
                onToggleFavorite = {
                    if (selectedItems.isNotEmpty()) {
                        viewModel.makeSongsFavorite(selectedItems)
                        viewModel.dismissDialog()
                    }
                },
                onDetectLoop = {
                    viewModel.dismissDialog()
                    if (selectedItems.isNotEmpty()) {
                        viewModel.detectLoopPointsBulk(context, selectedItems)
                    }
                },
                onRemoveFromPlaylist = dialog.playlistId?.let { pid ->
                    {
                        if (selectedItems.isNotEmpty()) {
                            viewModel.removeSongsFromPlaylistBulk(pid, selectedItems)
                            viewModel.dismissDialog()
                        }
                    }
                }
            )
        }

        null -> { /* 无弹窗显示 */ }
    }
}
