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
| `db/` | 数据库层。包含 `AppDatabase` (Room) 和 `DateConverter`。 |
| `model/` | 数据模型与 DAO 层。 |
| ├─ `Song.kt` | 歌曲模型，包含循环点字段。 |
| ├─ `SongDao.kt` | 歌曲数据访问对象。 |
| ├─ `Playlist.kt` | 歌单实体。 |
| ├─ `PlaylistItem.kt` | 歌单内的歌曲关联实体。 |
| ├─ `PlaylistFolder.kt` | 歌单文件夹分类模型。 |
| ├─ `PlaylistDao.kt` | 歌单相关操作 DAO。 |
| ├─ `LibraryItem.kt` | 媒体库列表通用项接口。 |
| ├─ `Folder.kt` | 文件夹模型。 |
| `adapter/` | 界面适配器。包含 `SongAdapter` 和 `FolderAdapter`。 |
| `scanner/` | `AudioScanner` 负责媒体库扫描。 |

### 2.3 界面资源层 (app/src/main/res/layout/)
| 文件 | 说明 |
| :--- | :--- |
| `activity_main.xml` | 包含工具栏、列表区和底部简易播放条的主布局。 |
| `dialog_loop_controls.xml` | 深度优化的深色高对比度循环点微调界面。 |
| `item_song.xml` | 列表单项布局，包含循环状态标识。 |

## 3. 核心流程

### 3.1 数据持久化流程
1. `AudioScanner` 扫描媒体库。
2. 将解析出的 `Song` 对象通过 `SongDao` 存储至 `AppDatabase`。
3. 歌单由 `PlaylistDao` 管理，支持创建文件夹及添加歌曲。

### 3.2 无缝播放流程
1. 用户点击播放 -> 从数据库获取歌曲信息。
2. `MainActivity` 开启音频 FD 传给 JNI。
3. `AudioEngine` 启动后台解码线程 -> 持续填充环形缓冲区。
4. Oboe 回调请求数据 -> 从缓冲区读取 PCM -> 如果当前帧越界则立即回跳。

### 3.3 循环点调整流程
1. 用户打开 `Loop Control` 弹窗。
2. 拖动弹窗内的进度条或点击 Set Current。
3. 调用 `setLoopPoints` (JNI) -> 底层立即清空缓冲区并重新从跳转点开始解码。
4. 同步更新数据库中的循环参数。

---
**记录人**：莱芙・泽诺 (Lev Zenith)
**版本**：v1.3 (2026-02-22)
