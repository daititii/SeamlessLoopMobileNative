# AGENTS.md - SeamlessLoopMobile

## 项目概览

Android 无缝循环音频播放器，Kotlin + C++ (Oboe) 混合架构。
单模块项目 (`:app`)，Min SDK 26，Compile/Target SDK 35。
Kotlin 2.1.0 + AGP 9.0.1 + Gradle 9.1.0，Compose compiler 由 Kotlin 2.1.0 内置。

## 构建与测试

```bash
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
gradlew.bat testDebugUnitTest --tests "com.cpu.seamlessloopmobile.viewmodel.PlayModeTest"
gradlew.bat connectedAndroidTest               # 需要设备
```

**测试前提**：`PcDatabaseImporterTest` 需要 `app/src/test/resources/pc_db_samples/pc_3nf_sample.db` 存在才能运行（仅 `pc_3nf_sample.db` 存在且有内容）；若文件缺失则测试静默通过而不报错！

**快速部署**（需 root 设备）：`run.bat` — adb push → pm install → am start。

## 架构要点

- **导航**：自定义 `MusicUiState` 密封类 + `AnimatedContent`，未使用 Navigation 组件（虽依赖 navigation-compose）

- **服务**：`PlaybackService` (MediaBrowserService) 后台播放，`MediaControlManager` 管理媒体会话

- **ViewModel**：`MainViewModel` 作为协调者，由 `MainViewModelFactory` 创建并通过属性赋值持有四个子 ViewModel（`LibraryViewModel`、`SelectionViewModel`、`PlaylistViewModel`、`LoopDetectionViewModel`）；其中**自动循环点探测**与试听调度逻辑已被彻底解耦，由全新的子管家 `LoopDetectionViewModel` 进行状态管理，并将耗时调用完全剥离出主线程以防止音频锁与 UI 锁争用。

- **Native 层**：`app/src/main/cpp/` — 包含两个核心引擎：
  1. **播放引擎**：Oboe 1.9.3 + NDK 解码器(minimp3)
  2. **探测引擎**：`loopfinder` (基于 FFT/Chroma 分析)
  - 统一通过 `NativeAudio.kt` 进行 JNI 桥接
  - 注意：`NativeAudio` 的 `init` 块中需同时加载 `seamlessloopmobile` 和 `loopfinder` 两个原生库

- **UI**：Jetpack Compose + Material3，状态通过 ViewModel 的 LiveData + MediaControlManager 的 StateFlow/SharedFlow 驱动

- **对话框**：统一 `MusicDialog` 密封类 + `CentralizedDialogHost` 集中管理

## 数据层（重要 — 近期大规模重构）

**数据库**：Room 2.7.0-alpha11，version 12，`fallbackToDestructiveMigration()`，DB 存在 `getExternalFilesDir(null)/databases/seamless_loop_db`

**3NF 表结构**（9 张表）：

| 表 | 实体 | 说明 |
|----|------|------|
| `Songs` | `SongEntity` | 主表，FK → Artists.Id, Albums.Id；索引：FilePath, FileName+duration, ArtistId, AlbumId, IsAbPartB |
| `Artists` | `Artist` | — |
| `Albums` | `Album` | — |
| `LoopPoints` | `LoopPoint` | 1:1 与 Songs，FK CASCADE |
| `UserRatings` | `UserRating` | 1:1 与 Songs，FK CASCADE |
| `Playlists` | `Playlist` | — |
| `PlaylistItems` | `PlaylistItem` | 关联 Playlist↔Song，有 SortOrder |
| `PlaylistFolders` | `PlaylistFolder` | Playlist→Folder 映射 |
| `PlayQueue` | `PlayQueueItem` | 持久化当前播放队列 |

**DAO 层**（3 个，都在 `model/`）：

- `SongDao` — 最复杂的 DAO。含 `insertOrUpdateSong()`（双指纹匹配：优先 fileName+duration，回退 filePath）、`updateSongsMetadataBatch()`（批量同步）、`getOrCreateArtist/Album()`、`Song` POJO（`@Relation` 聚合 SongEntity+Artist+Album+LoopPoint+UserRating）
- `PlaylistDao` — 含 `clearAndSyncPlaylist()`、`addSongsToPlaylist()`（去重）
- `PlayQueueDao` — `replacePlayQueue()` 事务方法

**Repository 层**（`data/`，6 文件）：

- `MusicRepository` — Facade，聚合 3 个子 Repository + PlayQueueDao
- `SongRepository` — 歌曲 CRUD
- `PlaylistRepository` — 播放列表基础 CRUD（A/B 检测、PC song 匹配等逻辑在 MusicScannerRepository 中）
- `LoopDetectionRepository` — **新版逻辑核心**！负责音频临时文件安全拷贝、跨线程 JNI 调用与 JSON 缓存管理。
- `MusicScannerRepository` — 扫描逻辑，含 `getInitialScannedSongs()`（全量扫描+批量更新+Artist/Album 预创建+A/B 标记+双指纹匹配）、`findAbPair()` / `findAbPairRobust()`（DB + MediaStore）
- `SettingsManager` — 单例 `getInstance(context)`，Gson 序列化，持久化 lastSongPath/lastPosition/playMode/isAbMode 等

**扫描流程**：
`AudioScanner.scan()` (MediaStore) → `MusicScannerRepository.getInitialScannedSongs()` → 双指纹匹配 → Artist/Album 预创建 → 批量写入 → A/B 标记（文件后缀 _B/_b/_loop/_Loop 设 `isAbPartB=true`，被大多数查询过滤）

**PC 数据库导入**：`PcDatabaseImporter`（顶层 object），支持 3NF 和 flat 两种 schema，三级流程（预加载 → 容差匹配 → 事务批量写入）。注意：测试时可以覆盖 `ioDispatcher`/`mainDispatcher` 属性为 `Dispatchers.Unconfined` 来避免死锁。

## 开发注意事项

- **Kotlin 源码目录**：所有 Kotlin 文件实际放在 `app/src/main/java/` 下（非 `kotlin/`），这是历史遗留的目录结构。搜索源码时需注意。
- **Kotlin Android 插件被注释**：`app/build.gradle.kts` 中 `alias(libs.plugins.kotlin.android)` 被注释掉了（AGP 9.x 内置 Kotlin 支持 + compose-compiler 插件足以编译）。
- **Room KSP**：`ksp { arg("room.generateKotlin", "true") }`，构建前需生成代码
- **Robolectric 测试**：`@Config(sdk = [34])`，JVM 模拟 Android 环境
- **CMake**：3.22.1，C++17，prefab 启用
- **配置缓存**：`org.gradle.configuration-cache=true`（默认开启，缓存问题可临时禁用）
- **调试安装**：`android.injected.testOnly=false` 解决调试弹窗
- **JVM 参数**：`org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -XX:MaxMetaspaceSize=512m -Xss4m`
- **KSP 兼容**：`android.disallowKotlinSourceSets=false` 解决 KSP 与 AGP 9.0+ 内置 Kotlin 的 sourceSets 兼容冲突
- **代码风格**：`kotlin.code.style=official`
- **双指纹去重**：`insertOrUpdateSong` 先用 fileName+duration 匹配，失败再用 filePath 匹配
- **Native 库加载**：`NativeAudio` 需同时加载 `seamlessloopmobile` 和 `loopfinder` 两个原生库
- **A/B 过滤**：含 `isAbPartB=true` 的歌曲默认在 UI 列表查询中被排除（由 `SongDao` 查询逻辑保证）
- **GEMINI.md**：为 `AGENTS.md` 的过时副本，应忽略之。
- **PcDatabaseImporter 测试**：需要 `app/src/test/resources/pc_db_samples/pc_3nf_sample.db` 存在（目前仅 `pc_3nf_sample.db` 存在，`pc_flat_sample.db` 不存在），测试中会覆盖 `ioDispatcher` 和 `mainDispatcher`。
- **快速部署**：项目根目录下的 `run.bat` 提供 root 设备的 adb push + pm install + am start 一键部署流程。
- **⚠️ 物理路径与 JNI 避坑硬约束**：探测引擎的 C++ `fopen` 无法直接读取 `content://` 或 MediaStore URI！自动探测循环点时，必须先使用 `LoopDetectionRepository` 将音频拷贝至私有 cache 目录，再将物理路径传入 `NativeAudio.analyzeLoopPoints`；分析完毕后必须在 `finally` 块中立即彻底删除临时文件，防止磁盘残留。

## 包结构速查

| 路径 | 职责 |
|------|------|
| `audio/` | PlaybackService, PlaybackManager (IMultiPlayer), MediaControlManager, QueueManager, AudioFocusManager, Notify, HeadsetPlugReceiver, SystemMediaProgressSyncController 等 |
| `data/` | MusicRepository + SongRepository + PlaylistRepository + MusicScannerRepository + SettingsManager + LoopDetectionRepository |
| `db/` | AppDatabase + DateConverter + PcDatabaseImporter（实体/DAO 已移到 model/） |
| `model/` | 9 个 Room 实体 + 3 个 DAO + Song(Lookup POJO) + SongMetadataUpdate + LibraryItem + Folder 等 15 文件 |
| `ui/screen/` | MainScreen + HomeScreen + CategoryScreen + SongListScreen + PlayingPanel |
| `ui/components/` | CentralizedDialogHost, MiniPlayer, PlayingComponents, FineTuneComponents, ListItems |
| `viewmodel/` | MainViewModel + LibraryViewModel + SelectionViewModel + PlaylistViewModel + LoopDetectionViewModel + MainViewModelFactory |
| `scanner/` | AudioScanner（Object，MediaStore 扫描；采样点在后续原生层处理） |
| `jni/` | NativeAudio（Kotlin object，JNI 入口）、LoopPoint（原生层返回的数据类） |
| `utils/` | TimeUtils |

## 注意

**AB式音乐**在本项目专指一首可循环歌曲分为两个音乐文件，A段是intro，B段是loop，与其他的一首可循环歌曲一个音乐文件不同。
文件名含 `_B`、`_b`、`_loop`、`_Loop` 后缀的文件被标记为 B 段（`isAbPartB=true`），在 UI 列表中默认隐藏。
