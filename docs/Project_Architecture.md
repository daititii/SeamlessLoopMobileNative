# SeamlessLoopMobile 项目结构参考文档

本项目是一个高精度的无缝循环音频播放器，基于 Android 平台开发，核心处理逻辑由 C++ 实现。

## 1. 整体架构
项目采用 **MVVM + Service + Repository** 的现代 Android 混合开发架构，UI 层全面升级为 Jetpack Compose 声明式界面。

- **UI 层 (ui/screen)**：基于 Jetpack Compose + Material3 的声明式界面，通过 `StateFlow`/`LiveData` 响应状态。采用自定义密封类 `MusicUiState` 导航，配合 `AnimatedContent` 实现页面切换动画。
- **服务层 (audio)**：核心枢纽。`PlaybackService` (MediaBrowserService) 维持后台生命周期，`MediaControlManager` 统一管理媒体会话状态流 (`StateFlow`/`SharedFlow`)。通过 `Playback` 接口解耦，`PlaybackManager` 负责音频调度。
- **仓库层 (data/db)**：数据真相来源。`MusicRepository` 作为外观 (Facade) 聚合细分子仓库 (`SongRepository`, `PlaylistRepository`, `MusicScannerRepository`)，统一管理 Room 及系统 MediaStore。新增 `SettingsManager` 单例管理播放偏好。
- **Native 层 (C++)**：高性能音频中心。实现基于 Oboe 的 PCM 流式输出及采样点级无缝跳转，支持 AB 循环模式。

## 2. 详细文件说明

### 2.1 Native 核心层 (app/src/main/cpp/)
| 文件 | 说明 |
| :--- | :--- |
| `AudioEngine.h/cpp` | 音频引擎核心。管理 Oboe 流，实现双缓冲区(FIFO)生产消费模式。 |
| `AudioDecoder.h/cpp` | 基于系统 NDK 媒解器的解码组件。支持精确采样点寻道。 |
| `native-lib.cpp` | JNI 桥接层。暴露给 Android 端的底层接口实现。 |

### 2.2 Android 业务层 (app/src/main/java/com/cpu/seamlessloopmobile/)
| 目录/文件 | 说明 |
| :--- | :--- |
| `MainActivity.kt` | 程序主入口，精简为权限申请、媒体连接及 Compose 启动器。 |
| `audio/` | **[核心]** 包含 `PlaybackService` (MediaBrowserService 后台服务), `Playback` (播放接口), `PlaybackManager` (音频调度), `AudioFocusManager` (音频焦点管理), `Notify/NotifyImpl` (通知栏接口及实现), `MediaControlManager` (媒体会话控制中心), `PlaybackCommand` (播放命令模型)。 |
| `data/` | **[核心]** 包含 `MusicRepository` (外观模式，聚合 `SongRepository`, `PlaylistRepository`, `MusicScannerRepository`), `SettingsManager` (偏好设置持久化单例)。 |
| `db/` | 数据库定义。`AppDatabase` 存储歌曲、歌单及循环配置，`PcDatabaseImporter` (PC 数据同步工具)。 |
| `model/` | 持久化实体类 (`Song`, `Playlist`, `Folder`) 与 DAO 接口。 |
| `viewmodel/` | `MainViewModel` 全局状态中心，通过 `MediaControlManager` 与 Service 通信，管理 UI 导航状态 (`MusicUiState`)。 |
| `scanner/` | `AudioScanner` 负责媒体库扫描及采样点精确计数。 |
| `jni/` | `NativeAudio` 定义，作为 JNI 函数的 Java 侧入口。 |
| `dialogs/` | 包含 `LoopSettingsDialog` 处理循环点微调 UI（基于传统 View）。 |
| `ui/` | **[Jetpack Compose]** 包含 `screen/` (各功能屏幕), `components/` (可复用组件如 `MiniPlayer`), `SelectionController` (列表多选逻辑)。 |
| `utils/` | 工具类，如 `TimeUtils` 等。 |

### 2.3 界面与导航
| 类别 | 说明 |
| :--- | :--- |
| **UI 框架** | 全面采用 Jetpack Compose + Material3 声明式 UI，弃用传统 View 系统。 |
| **导航系统** | 自定义密封类 `MusicUiState` (Home, CategoryFolders, SongList) 配合 `AnimatedContent` 实现页面切换动画，未使用 Navigation 组件。 |
| **主要屏幕** | `MainScreen` (根容器), `HomeScreen` (主页), `CategoryScreen` (分类), `SongListScreen` (歌曲列表), `PlayingPanel` (播放详情)。 |
| **传统布局** | `res/layout/` 仅保留对话框等辅助界面 (`dialog_loop_controls.xml`, `item_*.xml`)。 |

## 3. 核心流程

### 3.1 数据持久化流程
1. `AudioScanner` 扫描媒体库 → JNI 获取精确采样总数。
2. 通过 `MusicScannerRepository` 存入数据库，经由 `MusicRepository` 协调管理。
3. Compose UI 通过 ViewModel 订阅 Repository 的 `LiveData`/`Flow` 自动更新界面状态。

### 3.2 UI 导航与状态管理
1. **状态定义**：`MainViewModel` 维护 `MusicUiState` 密封类实例（Home, CategoryFolders, SongList），驱动界面切换。
2. **导航响应**：`MainScreen` 使用 `AnimatedContent` 监听 `uiState` 变化，动态渲染对应屏幕（`HomeScreen`, `CategoryScreen`, `SongListScreen`）。
3. **数据订阅**：Compose 组件通过 `collectAsState`/`observeAsState` 订阅 `ViewModel` 的 `LiveData` 和 `MediaControlManager` 的 `StateFlow`。
4. **播放控制**：用户操作（播放、暂停、切歌）经由 `MediaControlManager` 转发给 `PlaybackService`，状态变化通过 `StateFlow` 实时回馈 UI。

### 3.3 无缝播放流程 (Service 驱动)
1. **发起**：Compose UI 通过 `MediaControlManager` 发送 Play 指令，或系统媒体控件经 `MediaSession` 转发。
2. **调度**：`PlaybackService` 接收指令，通过 `Playback` 接口调用 `PlaybackManager` 调度引擎。
3. **准备**：Manager 开启 `NativeAudio.startAudioEngine`，加载音频文件并解码。
4. **输出**：C++ 解码线程填充双缓冲区 → Oboe 回调读取 PCM 数据 → 播放位置达到 `LoopEnd` 采样点时重定向至 `LoopStart`。
5. **状态同步**：`MediaControlManager` 通过 `StateFlow` 实时推送播放状态、元数据、进度至 UI，`Notify` 模块更新系统通知。

### 3.4 循环点调整流程
1. 用户在 `LoopSettingsDialog` 调整进度。
2. 指令经由 `PlaybackManager` 传达至 JNI。
3. 底层立即清空解码缓冲区，精准 Seek。
4. 成功后通过 `SongRepository` 更新数据库中的配置。

---
**记录人**：莱芙・泽诺 (Lev Zenith)
**版本**：v2.0 (2026-03-06) - 适配 Jetpack Compose 架构升级
