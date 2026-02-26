package com.cpu.seamlessloopmobile.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.PlaylistItem
import com.cpu.seamlessloopmobile.model.PlaylistFolder
import com.cpu.seamlessloopmobile.model.PlaylistDao

import androidx.room.TypeConverters

@Database(entities = [Song::class, Playlist::class, PlaylistItem::class, PlaylistFolder::class], version = 3, exportSchema = false)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // 搬家到“外部公开目录”喵！路径在：Android/data/com.cpu.seamlessloopmobile/files/databases/
                // 这样大人插上数据线，在电脑上就能一眼看到它啦
                val dbFile = java.io.File(context.getExternalFilesDir(null), "databases/seamless_loop_db")
                if (dbFile.parentFile?.exists() == false) {
                    dbFile.parentFile?.mkdirs()
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbFile.absolutePath
                )
                .fallbackToDestructiveMigration() // 既然大人不要旧数据，结构要是变了就直接删掉重开喵
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
