# SeamlessLoopMobile 项目结构参考文档

本项目是一个高精度的无缝循环音频播放器，基于 Android 平台开发，核心处理逻辑由 C++ 实现。

## 1. 整体架构
项目采用 **Android (Kotlin) + NDK (C++)** 混合开发模式。
- **Java/Kotlin 侧**：负责 UI 展示、媒体库扫描、文件管理以及调用 Native 接口。
- **Native (C++) 侧**：负责高性能音频解码（MediaCodec）、实时音频输出（Oboe 库）以及微秒级的无缝跳转逻辑。

## 2. 详细文件说明

### 2.1 Native 核心层 (app/src/main/cpp/)
| 文件 | 说明 |
| :--- | :--- |
| `AudioEngine.h/cpp` | 音频引擎核心。管理 Oboe 流，实现双缓冲区(FIFO)生产消费模式，控制循环跳转。 |
| `AudioDecoder.h/cpp` | 基于系统 NDK 媒解器的解码组件。支持采样点级寻道。 |
| `native-lib.cpp` | JNI 桥接层。暴露给 Android 端的底层接口实现。 |
| `CMakeLists.txt` | C++ 编译配置文件。 |

### 2.2 Android 业务层 (app/src/main/java/com/cpu/seamlessloopmobile/)
| 目录/文件 | 说明 |
| :--- | :--- |
| `MainActivity.kt` | 程序主入口。负责 UI 逻辑集成、JNI 接口调用和 BottomSheet 弹窗控制。 |
| `model/Song.kt` | 核心数据模型，定义了循环点字段及元数据。 |
| `model/Folder.kt` | 文件夹模型，用于扁平化列表展示。 |
| `adapter/SongAdapter.kt` | 歌曲列表适配器，支持 DiffUtil 增量刷新。 |
| `adapter/FolderAdapter.kt` | 文件夹列表适配器。 |
| `scanner/AudioScanner.kt` | 使用 ContentResolver 扫描系统中符合要求的音频文件。 |

### 2.3 界面资源层 (app/src/main/res/layout/)
| 文件 | 说明 |
| :--- | :--- |
| `activity_main.xml` | 包含工具栏、列表区和底部简易播放条的主布局。 |
| `dialog_loop_controls.xml` | 深度优化的深色高对比度循环点微调界面。 |
| `item_song.xml` | 列表单项布局，包含循环状态标识。 |

## 3. 核心流程

### 3.1 无缝播放流程
1. `AudioScanner` 扫描媒体库 -> 转换成 `Song` 列表。
2. 用户点击播放 -> `MainActivity` 开启音频 FD 传给 JNI。
3. `AudioEngine` 启动后台解码线程 -> 持续填充环形缓冲区。
4. Oboe 回调请求数据 -> 从缓冲区读取 PCM -> 如果当前帧越界则立即回跳。

### 3.2 循环点调整流程
1. 用户打开 `Loop Control` 弹窗。
2. 拖动弹窗内的进度条或点击 Set Current。
3. 调用 `setLoopPoints` (JNI) -> 底层立即清空缓冲区并重新从跳转点开始解码。

---
**记录人**：莱芙・泽诺 (Lev Zenith)
**版本**：v1.2 (2026-02-19)
