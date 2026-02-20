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

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txt_song_title)
        val artist: TextView = view.findViewById(R.id.txt_song_artist)
        val loopIcon: TextView = view.findViewById(R.id.txt_loop_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.title.text = song.displayName ?: song.fileName
        holder.artist.text = song.artist
        
        // 如果有循环点，就显示无限大符号
        holder.loopIcon.visibility = if (song.loopEnd > 0) View.VISIBLE else View.GONE
        
        holder.itemView.setOnClickListener { onItemClick(song) }
    }

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
