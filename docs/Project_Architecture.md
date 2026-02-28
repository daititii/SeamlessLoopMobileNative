# SeamlessLoopMobile 项目结构参考文档

本项目是一个高精度的无缝循环音频播放器，基于 Android 平台开发，核心处理逻辑由 C++ 实现。

## 1. 整体架构
项目采用 **MVVM + Service + Repository** 的现代 Android 混合开发架构。

- **UI 层 (ui/viewmodel)**：通过 LiveData/StateFlow 响应状态，不直接触碰底层。
- **服务层 (audio)**：核心枢纽。`PlaybackService` (MediaBrowserService) 维持后台生命周期，`PlaybackManager` 负责音频调度。
- **仓库层 (data/db)**：数据真相来源。`MusicRepository` 统一管理 Room 数据库及系统 MediaStore 扫描结果。
- **Native 层 (C++)**：高性能音频中心。实现基于 Oboe 的 PCM 流式输出、采样点级微秒跳转及 AB 逻辑拼接。

## 2. 详细文件说明

### 2.1 Native 核心层 (app/src/main/cpp/)
| 文件 | 说明 |
| :--- | :--- |
| `AudioEngine.h/cpp` | 音频引擎核心。管理 Oboe 流，实现双缓冲区(FIFO)生产消费模式，控制循环跳转。 |
| `AudioDecoder.h/cpp` | 基于系统 NDK 媒解器的解码组件。支持采样点级寻道。 |
| `native-lib.cpp` | JNI 桥接层。暴露给 Android 端的底层接口实现。 |

### 2.2 Android 业务层 (app/src/main/java/com/cpu/seamlessloopmobile/)
| 目录/文件 | 说明 |
| :--- | :--- |
| `MainActivity.kt` | 程序主容器，承载 UI 框架。 |
| `audio/` | **[核心]** 包含 `PlaybackService` (后台司令部), `PlaybackManager` (引擎翻译官), `MediaControlManager` (通知栏控制器)。 |
| `data/` | **[核心]** 包含 `MusicRepository`，负责跨模块的数据协调。 |
| `db/` | 数据库定义。`AppDatabase` 存储歌曲、歌单及循环配置。 |
| `model/` | 持久化实体类 (`Song`, `Playlist`) 与 DAO 接口。 |
| `viewmodel/` | `MainViewModel` 全局状态中心，负责 UI 与 Service 的通信。 |
| `scanner/` | `AudioScanner` 负责媒体库扫描及采样点精确计数。 |
| `adapter/` | 列表适配器 (`LibraryAdapter`, `SongAdapter`)。 |
| `utils/` | 工具类，如 `TimeUtils` 等。 |

### 2.3 界面资源层 (app/src/main/res/layout/)
| 文件 | 说明 |
| :--- | :--- |
| `activity_main.xml` | 主界面布局，包含 Toolbar 和 Bottom Controls。 |
| `dialog_loop_controls.xml` | 独立弹出的循环点微调界面，支持波形逻辑展示（计划中）。 |

## 3. 核心流程

### 3.1 数据持久化流程
1. `AudioScanner` 从 MediaStore 捞取歌曲 -> JNI 获取精确采样总数。
2. 通过 `MusicRepository` 将 `Song` 对象固化到 Room。
3. UI 层直接订阅 Repository 或通过 ViewModel 间接获取。

### 3.2 无缝播放流程 (Service 驱动)
1. **发起**：UI 通过 `MediaController` 发送 Play 指令。
2. **调度**：`PlaybackService` 接收指令，命令 `PlaybackManager` 加载歌曲。
3. **准备**：Manager 通过 `ContentResolver` 获取 FD，开启 `NativeAudio.startAudioEngine`。
4. **输出**：C++ 解码线程填充缓冲 -> Oboe 回调取值 -> 帧数达 `LoopEnd` 时指针重定向至 `LoopStart`。
5. **合体 (AB模式)**：C++ 侧连接 A、B 两个解码流，A 结束后平滑切换至 B 循环。

### 3.3 循环点调整流程
1. 用户在 UI 调整进度或手动 Set Point。
2. 指令经由 `PlaybackManager` 传达至 JNI。
3. 底层立即清空解码缓冲区，精准 Seek 到目标采样点以保证听感即时刷新。
4. 成功后通过 Repository 异步回写数据库。

---
**记录人**：莱芙・泽诺 (Lev Zenith)
**版本**：v1.4 (2026-02-28)
