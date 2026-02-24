package com.cpu.seamlessloopmobile.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cpu.seamlessloopmobile.R
import com.cpu.seamlessloopmobile.model.Folder
import com.cpu.seamlessloopmobile.model.LibraryItem
import com.cpu.seamlessloopmobile.model.Playlist

class LibraryAdapter(
    private var items: List<LibraryItem>,
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onFolderClick: (Folder) -> Unit,
    private val onQuickActionClick: (String) -> Unit,
    private val onPlaylistLongClick: ((Playlist) -> Unit)? = null,
    private val onFolderLongClick: ((Folder) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var isSelectionMode = false
    private val selectedPlaylistIds = mutableSetOf<Int>()
    private var onSelectionChanged: ((Int) -> Unit)? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PLAYLIST = 1
        private const val TYPE_FOLDER = 2
        private const val TYPE_QUICK_ACTION = 3
    }

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) selectedPlaylistIds.clear()
            notifyDataSetChanged()
        }
    }

    fun toggleSelection(playlistId: Int) {
        if (selectedPlaylistIds.contains(playlistId)) {
            selectedPlaylistIds.remove(playlistId)
        } else {
            selectedPlaylistIds.add(playlistId)
        }
        onSelectionChanged?.invoke(selectedPlaylistIds.size)
        notifyDataSetChanged()
    }

    fun selectAll() {
        val playlistItems = items.filterIsInstance<LibraryItem.PlaylistWrapper>()
        if (selectedPlaylistIds.size == playlistItems.size) {
            selectedPlaylistIds.clear()
        } else {
            playlistItems.forEach { selectedPlaylistIds.add(it.playlist.id) }
        }
        onSelectionChanged?.invoke(selectedPlaylistIds.size)
        notifyDataSetChanged()
    }

    fun isAllSelected(): Boolean {
        val playlistItems = items.filterIsInstance<LibraryItem.PlaylistWrapper>()
        return playlistItems.isNotEmpty() && selectedPlaylistIds.size == playlistItems.size
    }

    fun getSelectedPlaylists(): List<Playlist> {
        return items.mapNotNull { if (it is LibraryItem.PlaylistWrapper && selectedPlaylistIds.contains(it.playlist.id)) it.playlist else null }
    }

    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        onSelectionChanged = listener
    }


    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is LibraryItem.Header -> TYPE_HEADER
            is LibraryItem.PlaylistWrapper -> TYPE_PLAYLIST
            is LibraryItem.FolderWrapper -> TYPE_FOLDER
            is LibraryItem.QuickAction -> TYPE_QUICK_ACTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                HeaderViewHolder(inflater.inflate(R.layout.item_header, parent, false))
            }
            else -> {
                ItemViewHolder(inflater.inflate(R.layout.item_folder, parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LibraryItem.Header -> {
                (holder as HeaderViewHolder).bind(item.title)
            }
            is LibraryItem.PlaylistWrapper -> {
                (holder as ItemViewHolder).bindPlaylist(item.playlist, item.songCount)
                val isSelected = selectedPlaylistIds.contains(item.playlist.id)
                holder.itemView.alpha = if (isSelected) 0.5f else 1.0f
                holder.itemView.setBackgroundColor(if (isSelected) 0x33FFFFFF else 0)
                
                holder.itemView.setOnClickListener { 
                    if (isSelectionMode) {
                        toggleSelection(item.playlist.id)
                    } else {
                        onPlaylistClick(item.playlist) 
                    }
                }
                holder.itemView.setOnLongClickListener {
                    if (!isSelectionMode) {
                        onPlaylistLongClick?.invoke(item.playlist)
                    } else {
                        toggleSelection(item.playlist.id)
                    }
                    true // 拦截长按
                }
            }
            is LibraryItem.FolderWrapper -> {
                (holder as ItemViewHolder).bindFolder(item.folder)
                holder.itemView.alpha = 1.0f
                holder.itemView.setBackgroundColor(0)
                holder.itemView.setOnClickListener { onFolderClick(item.folder) }
                holder.itemView.setOnLongClickListener {
                    onFolderLongClick?.invoke(item.folder)
                    true
                }
            }
            is LibraryItem.QuickAction -> {
                (holder as ItemViewHolder).bindQuickAction(item)
                holder.itemView.setOnClickListener { onQuickActionClick(item.title) }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<LibraryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtTitle: TextView = view.findViewById(R.id.txt_header_title)
        fun bind(title: String) { txtTitle.text = title }
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_folder_name)
        private val txtCount: TextView = view.findViewById(R.id.txt_song_count)
        private val imgIcon: ImageView = view.findViewById(R.id.img_folder)

        fun bindPlaylist(playlist: Playlist, count: Int) {
            txtName.text = playlist.name
            txtCount.text = "$count 首歌曲"
            imgIcon.setImageResource(android.R.drawable.ic_menu_agenda) // 歌单用记事本图标喵
        }

        fun bindFolder(folder: Folder) {
            txtName.text = folder.name
            txtCount.text = "${folder.songCount} 首歌曲"
            imgIcon.setImageResource(android.R.drawable.ic_menu_save) // 文件夹用原本的图标喵
        }

        fun bindQuickAction(action: LibraryItem.QuickAction) {
            txtName.text = action.title
            txtCount.text = "${action.count} 首歌曲"
            imgIcon.setImageResource(action.iconRes)
        }
    }
}
