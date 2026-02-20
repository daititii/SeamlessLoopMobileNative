package com.cpu.seamlessloopmobile.db

import androidx.room.TypeConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateConverter {
    // 兼容电脑端常见的 SQLite 时间格式 (ISO8601 变体)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @TypeConverter
    fun fromTimestamp(value: String?): Long? {
        return value?.let {
            // 尝试解析为 Long (手机端原生数据)
            it.toLongOrNull() ?: try {
                // 解析失败则尝试解析为日期字符串 (电脑端导入数据)
                dateFormat.parse(it)?.time
            } catch (e: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun dateToTimestamp(date: Long?): String? {
        // 统一存为字符串以保持与电脑端格式完全一致？
        // 或者存为 Long？
        // 考虑到要与 LoopData.db (PC) 互通，PC 端是 DATETIME DEFAULT CURRENT_TIMESTAMP，通常是 String。
        // 所以我们写入时最好也写 String，或者 Room 可能会根据 ColumnInfo 自动处理。
        // 但 updateSong 时传入的是 Long。
        
        // 策略：如果为了完全兼容 PC 读取，最好存 ISO String。
        // 但 Room 的 DateConverter 通常是 Long <-> Date。
        // 这里我们的 Song.lastModified 是 Long。
        // 数据库里是 String (假如是 imported PC DB) 或 Long (纯手机创建)。
        
        // 简化策略：
        // 读：String -> Long (兼容两种)
        // 写：Long -> String (为了让 PC 端也能看懂手机改的时间)
        return date?.let {
            dateFormat.format(Date(it))
        }
    }
}
