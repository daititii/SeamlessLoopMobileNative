# SeamlessLoopMobile 项目结构参考文档

本项目是一个高精度的无缝循环音频播放器，基于 Android 平台开发，核心处理逻辑由 C++ 实现。

## 1. 整体架构
项目采用 **MVVM + Service + Repository** 的现代 Android 混合开发架构。

- **UI 层 (ui/viewmodel)**：通过 LiveData/StateFlow 响应状态，不直接触碰底层。包含 `SelectionController` 处理列表交互。
- **服务层 (audio)**：核心枢纽。`PlaybackService` (MediaBrowserService) 维持后台生命周期。通过 `Playback` 接口解耦，`PlaybackManager` 负责音频调度。
- **仓库层 (data/db)**：数据真相来源。包含细分的仓库 (`SongRepository`, `PlaylistRepository`, `MusicScannerRepository`)，统一管理 Room 及系统 MediaStore。
- **Native 层 (C++)**：高性能音频中心。实现基于 Oboe 的 PCM 流式输出及采样点级无缝跳转。

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
| `MainActivity.kt` | 程序主容器，承载 UI 框架。 |
| `audio/` | **[核心]** 包含 `PlaybackService` (后台服务), `Playback` (播放接口), `PlaybackManager` (底层调度), `AudioFocusManager` (音频焦点管理), `Notify/NotifyImpl` (通知栏接口及实现), `MediaControlManager` (通知栏逻辑控制), `PlaybackCommand` (播放命令模型)。 |
| `data/` | **[核心]** 包含 `MusicRepository` (多合一协调), `SongRepository`, `PlaylistRepository`, `MusicScannerRepository` (数据持久化子系统)。 |
| `db/` | 数据库定义。`AppDatabase` 存储歌曲、歌单及循环配置。 |
| `model/` | 持久化实体类 (`Song`, `Playlist`) 与 DAO 接口。 |
| `viewmodel/` | `MainViewModel` 全局状态中心，负责 UI 与 Service 的通信。 |
| `scanner/` | `AudioScanner` 负责媒体库扫描及采样点精确计数。 |
| `jni/` | `NativeAudio` 定义，作为 JNI 函数的 Java 侧入口。 |
| `dialogs/` | 包含 `LoopSettingsDialog` 处理循环点微调 UI。 |
| `ui/` | 包含 `SelectionController` 控制列表多选等逻辑。 |
| `adapter/` | 列表适配器 (`LibraryAdapter`, `SongAdapter`)。 |
| `utils/` | 工具类，如 `TimeUtils` 等。 |

### 2.3 界面资源层 (app/src/main/res/layout/)
| 文件 | 说明 |
| :--- | :--- |
| `activity_main.xml` | 主界面布局，包含 Toolbar 和 Bottom Controls。 |
| `dialog_loop_controls.xml` | 独立弹出的循环点微调界面布局。 |

## 3. 核心流程

### 3.1 数据持久化流程
1. `AudioScanner` 扫描媒体库 → JNI 获取精确采样总数。
2. 通过 `MusicScannerRepository` 存入数据库，经由 `MusicRepository` 协调管理。
3. UI 层通过 ViewModel 订阅相关的 Repository 同步 UI 状态。

### 3.2 无缝播放流程 (Service 驱动)
1. **发起**：UI 与 `MediaController` 交互发送 Play 指令。
2. **调度**：`PlaybackService` 接收指令，通过 `Playback` 接口调用 `PlaybackManager` 调度引擎。
3. **准备**：Manager 开启 `NativeAudio.startAudioEngine`。
4. **输出**：C++ 解码线程填充缓冲 → Oboe 回调取值 → 帧数达 `LoopEnd` 时重定向至 `LoopStart`。
5. **通知**：`Notify` 模块在 `MediaControlManager` 协同下更新系统通知。

### 3.3 循环点调整流程
1. 用户在 `LoopSettingsDialog` 调整进度。
2. 指令经由 `PlaybackManager` 传达至 JNI。
3. 底层立即清空解码缓冲区，精准 Seek。
4. 成功后通过 `SongRepository` 更新数据库中的配置。

---
**记录人**：莱芙・泽诺 (Lev Zenith)
**版本**：v1.5 (2026-03-01)
