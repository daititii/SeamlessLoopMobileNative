# SeamlessLoopMobile 项目概览

## 1. 项目目标
在安卓平台上实现一个极简、高性能的本地音乐播放器，核心特色是支持从桌面端迁移的**采样级无缝循环播放**功能。

## 2. 核心技术栈
- **应用层**: Kotlin (Android Native) + Jetpack (ViewModel, Room, LiveData)
- **底层音频**: C++ (NDK) + Google Oboe 库 + MiniMP3 (解码)
- **数据管理**: SQLite (Room) + Android ContentResolver
- **UI 设计**: 极简 Material Design (RecyclerView + Bottom Controls)

## 3. 核心组件
项目的代码采用标准的层级化结构：
- `audio`: **[核心]** 播放指挥部。`PlaybackService` (MediaBrowserService), `PlaybackManager` (引擎翻译官), `MediaControlManager` (系统集成)。
- `data`: **[核心]** 数据仓库。`MusicRepository` 统一协调本地数据库与系统媒体库。
- `jni`: 底层桥接。`NativeAudio` 静态对象声明。
- `scanner`: 媒体扫描。`AudioScanner` (全库扫描), 基于 JNI 的精确采样计数。
- `db`: 持久化。`AppDatabase`, `PcDatabaseImporter` (PC 数据同步工具)。
- `viewmodel`: 状态中心。`MainViewModel` 驱动全局 UI。
- `model`: 实体定义。`Song`, `Playlist`, `Folder` 等。

## 4. 核心业务逻辑
1. **采样级循环**: 
   - 由 `PlaybackService` 维持后台生命线，在 C++ 音频回调线程中，根据预设的 `LoopStart` 和 `LoopEnd` 采样点，在缓冲区直接重定向采样指针。
2. **AB 循环模式 (特色)**:
   - 支持逻辑合体 Intro + Loop 两个文件。
   - `NativeAudio` 底层将 A 段和 B 段 PCM 数据流无缝拼接，播放完 A 后自动进入 B 段循环，常用于游戏音乐 BGM。
3. **数据继承与同步**: 
   - 兼容桌面端的 SQLite 数据库结构，可导入桌面端 `.db` 文件。
   - 提供“定向精准扫描”，通过底层解码器获取 100% 准确的 PCM 总采样数。
4. **系统集成**: 
   - 通过 `MediaSession` 对接系统锁屏播放控制、通知栏及蓝牙按键。

## 5. 架构设计
采用基于 **MVVM + Service + Repository** 的分层架构：
- **UI 层**: 处理列表展示、歌单管理及用户交互。
- **Service 层**: `PlaybackService` 维持后台生命周期，管理音频焦点及 MediaSession。
- **Data 层**: `MusicRepository` 屏蔽底层差异，为上层提供统一数据流。
- **Native 层**: `AudioEngine` 处理 PCM 数据流，维护异步解码 FIFO 缓冲区。
