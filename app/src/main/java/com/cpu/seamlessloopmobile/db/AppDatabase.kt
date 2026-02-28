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
                // 应急预案 A：优先搬家到“外部公开目录”喵！路径在：Android/data/com.cpu.seamlessloopmobile/files/databases/
                // 如果外部目录不可用（比如没插 SD 卡或系统限制），莱芙会默默退回到内部私有目录喵！
                val externalDir = context.getExternalFilesDir(null)
                val dbFile = if (externalDir != null) {
                    java.io.File(externalDir, "databases/seamless_loop_db")
                } else {
                    java.io.File(context.filesDir, "databases/seamless_loop_db")
                }
                
                if (dbFile.parentFile?.exists() == false) {
                    dbFile.parentFile?.mkdirs()
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbFile.absolutePath
                )
                .fallbackToDestructiveMigration() 
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
