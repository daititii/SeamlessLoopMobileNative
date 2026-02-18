package com.cpu.seamlessloopmobile.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cpu.seamlessloopmobile.R
import com.cpu.seamlessloopmobile.model.Folder

class FolderAdapter(
    private var folders: List<Folder>,
    private val onFolderClick: (Folder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txt_folder_name)
        val txtCount: TextView = view.findViewById(R.id.txt_song_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.txtName.text = folder.name
        holder.txtCount.text = "${folder.songCount} 首歌曲"
        
        holder.itemView.setOnClickListener {
            onFolderClick(folder)
        }
    }

    override fun getItemCount() = folders.size

    fun updateFolders(newFolders: List<Folder>) {
        folders = newFolders
        notifyDataSetChanged()
    }
}
