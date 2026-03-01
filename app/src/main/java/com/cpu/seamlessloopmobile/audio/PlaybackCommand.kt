package com.cpu.seamlessloopmobile.audio

/**
 * 指挥部：所有播放控制指令的集合中心喵！
 * 避免魔术数字，让代码更易读、更统一。
 */
object PlaybackCommand {
    // 标准播放操作 (与 PlaybackStateCompat 类似对应，但更简单)
    const val PLAY = 1
    const val PAUSE = 2
    const val STOP = 3
    const val SKIP_TO_NEXT = 4
    const val SKIP_TO_PREVIOUS = 5
    const val TOGGLE_PLAY_PAUSE = 6
    const val REWIND = 7
    const val FAST_FORWARD = 8
    
    // 自定义操作 (Custom Actions)
    const val ACTION_SET_PLAY_MODE = "com.cpu.seamlessloopmobile.SET_PLAY_MODE"
    const val EXTRA_PLAY_MODE = "play_mode"

    /**
     * 判断某个命令是否需要在前台状态下执行喵
     */
    fun isForegroundAction(cmd: Int): Boolean {
        return when (cmd) {
            PLAY, SKIP_TO_NEXT, SKIP_TO_PREVIOUS, TOGGLE_PLAY_PAUSE -> true
            else -> false
        }
    }
}
