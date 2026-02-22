package com.cpu.seamlessloopmobile.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.cpu.seamlessloopmobile.R
import com.cpu.seamlessloopmobile.model.Song

class SongAdapter(
    private var songs: List<Song>,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var isSelectionMode = false
    private val selectedSongIds = mutableSetOf<Long>()
    private var onSongLongClick: ((Song) -> Unit)? = null
    private var onSelectionChanged: ((Int) -> Unit)? = null

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txt_song_title)
        val artist: TextView = view.findViewById(R.id.txt_song_artist)
        val loopIcon: TextView = view.findViewById(R.id.txt_loop_status)
        val checkBox: android.widget.CheckBox = view.findViewById(R.id.checkbox_select)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.title.text = song.displayName ?: song.fileName
        holder.artist.text = song.artist
        
        // 勾选框逻辑
        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedSongIds.contains(song.id)
        
        // 如果有循环点，就显示无限大符号
        holder.loopIcon.visibility = if (song.loopEnd > 0) View.VISIBLE else View.GONE
        
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(song.id)
            } else {
                onItemClick(song)
            }
        }

        holder.itemView.setOnLongClickListener {
            onSongLongClick?.invoke(song)
            true
        }
    }

    fun setOnLongClickListener(listener: (Song) -> Unit) {
        this.onSongLongClick = listener
    }

    fun setSelectionMode(enabled: Boolean) {
        if (this.isSelectionMode != enabled) {
            this.isSelectionMode = enabled
            if (!enabled) selectedSongIds.clear()
            notifyDataSetChanged()
        }
    }

    fun toggleSelection(songId: Long) {
        if (selectedSongIds.contains(songId)) {
            selectedSongIds.remove(songId)
        } else {
            selectedSongIds.add(songId)
        }
        onSelectionChanged?.invoke(selectedSongIds.size)
        notifyDataSetChanged()
    }

    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        this.onSelectionChanged = listener
    }

    fun getSelectedSongIds(): List<Long> = selectedSongIds.toList()

    fun getSelectedSongs(): List<Song> = songs.filter { selectedSongIds.contains(it.id) }

    override fun getItemCount(): Int = songs.size

    /**
     * 升级版更新方法：使用 DiffUtil 进行增量刷新
     */
    fun updateSongs(newSongs: List<Song>) {
        val diffCallback = SongDiffCallback(this.songs, newSongs)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        this.songs = newSongs
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * 单独更新某一项（用于修改循环点后刷新状态）
     */
    fun updateSongItem(index: Int, newSong: Song) {
        if (index in songs.indices) {
            val mutableList = songs.toMutableList()
            mutableList[index] = newSong
            songs = mutableList
            notifyItemChanged(index)
        }
    }

    /**
     * DiffUtil 回调类，负责计算列表项差异
     */
    private class SongDiffCallback(
        private val oldList: List<Song>,
        private val newList: List<Song>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        // 判断是否是同一项（这里我们用 ID 或者文件路径作为唯一标识）
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id || 
                   oldList[oldItemPosition].filePath == newList[newItemPosition].filePath
        }

        // 判断内容是否完全一致（利用 data class 自动生成的 equals）
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
