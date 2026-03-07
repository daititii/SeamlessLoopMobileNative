package com.cpu.seamlessloopmobile.audio

/**
 * 播放器状态机枚举喵！
 * 严格遵循 APlayer 式的生命周期，让逻辑不再乱跳。
 */
enum class AudioPlayState {
    /** 初始状态，啥也没干 */
    IDLE,

    /** 正在努力加载文件、初始化 JNI 引擎喵 */
    PREPARING,

    /** 音乐响起来了！正处于无缝循环的极乐世界 */
    PLAYING,

    /** 歇口气，随时准备 resume */
    PAUSED,

    /** 播放结束（如果大人没开循环的话） */
    COMPLETED,

    /** 引擎翻车了喵，需要看看错误消息 */
    ERROR
}
