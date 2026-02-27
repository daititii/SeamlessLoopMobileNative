# SeamlessLoopMobile 项目概览

## 1. 项目目标
在安卓平台上实现一个极简、高性能的本地音乐播放器，核心特色是支持从桌面端迁移的**采样级无缝循环播放**功能。

## 2. 核心技术栈
- **应用层**: Kotlin (Android Native) + Jetpack (ViewModel, Room, LiveData)
- **底层音频**: C++ (NDK) + Google Oboe 库 + MiniMP3 (解码)
- **数据管理**: SQLite (Room) + Android ContentResolver
- **UI 设计**: 极简 Material Design (RecyclerView + Bottom Controls)

## 3. 核心组件
项目的 Kotlin 代码采用功能模块化结构：
- `audio`: 播放核心。`PlaybackService` (前台服务), `PlaybackManager` (音频调度), `MediaControlManager` (系统媒体控制)。
- `jni`: Native 层桥接。`NativeAudio` 对象声明。
- `scanner`: 媒体扫描。`AudioScanner` (全库扫描), 基于 JNI 的精确采样计数。
- `db`: 数据持久化。`AppDatabase`, `PcDatabaseImporter` (PC 数据同步工具)。
- `viewmodel`: 状态中心。`MainViewModel` 驱动全局 UI。
- `model`: 实体定义。`Song`, `Playlist`, `Folder`, `PlaylistItem` 等。

## 4. 核心业务逻辑
1. **采样级循环**: 
   - 在 C++ 音频回调线程中，根据预设的 `LoopStart` 和 `LoopEnd` 采样点，在缓冲区直接重定向采样指针。
2. **AB 循环模式 (特色)**:
   - 支持将两个文件 (Intro + Loop) 逻辑合体。
   - `NativeAudio` 底层将 A 段和 B 段 PCM 数据流无缝拼接，播放完 A 后自动进入 B 段循环，常用于游戏音乐 BGM。
3. **数据继承与同步**: 
   - 兼容桌面端的 SQLite 数据库结构，可导入桌面端 `.db` 文件自动同步循环点。
   - 提供“定向精准扫描”，通过底层解码器获取 100% 准确的 PCM 总采样数以匹配 PC 端数据。
4. **系统集成**: 
   - 通过 `MediaSession` 对接系统锁屏播放控制、通知栏及蓝牙按键。

## 5. 架构设计
采用分层架构（Layered Architecture）：
- **UI 层**: 处理列表展示、歌单管理及用户交互。
- **Service 层**: `PlaybackService` 维持后台生命周期，管理音频焦点。
- **Native 层**: `AudioEngine` 处理 PCM 数据流，维护异步解码 FIFO 缓冲区，执行精准跳转。
