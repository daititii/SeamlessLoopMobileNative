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

## 架构要点

- **导航**：自定义 `MusicUiState` 密封类 + `AnimatedContent`，未使用 Navigation 组件（虽依赖 navigation-compose）
- **服务**：`PlaybackService` (MediaBrowserService) 后台播放，`MediaControlManager` 管理媒体会话
- **ViewModel**：`MainViewModel` 作为协调者，由 `MainViewModelFactory` 创建并通过属性赋值持有三个子 ViewModel（`LibraryViewModel`、`SelectionViewModel`、`PlaylistViewModel`）；同时负责**自动循环点探测**逻辑的调度与状态管理
- **Native 层**：`app/src/main/cpp/` — 包含两个核心引擎：
    1. **播放引擎**：Oboe 1.9.3 + NDK 解码器(minimp3)
    2. **探测引擎**：`loopfinder` (基于 FFT/Chroma 分析)
    - 统一通过 `NativeAudio.kt` 进行 JNI 桥接
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

**Repository 层**（`data/`，5 文件）：
- `MusicRepository` — Facade，聚合 3 个子 Repository + PlayQueueDao
- `SongRepository` — 歌曲 CRUD
- `PlaylistRepository` — 播放列表基础 CRUD（A/B 检测、PC song 匹配等逻辑在 MusicScannerRepository 中）
- `MusicScannerRepository` — 扫描逻辑，含 `getInitialScannedSongs()`（全量扫描+批量更新+Artist/Album 预创建+A/B 标记+双指纹匹配）、`findAbPair()` / `findAbPairRobust()`（DB + MediaStore）
- `SettingsManager` — 单例 `getInstance(context)`，Gson 序列化，持久化 lastSongPath/lastPosition/playMode/isAbMode 等

**扫描流程**：
`AudioScanner.scan()` (MediaStore) → `MusicScannerRepository.getInitialScannedSongs()` → 双指纹匹配 → Artist/Album 预创建 → 批量写入 → A/B 标记（文件后缀 _B/_b/_loop/_Loop 设 `isAbPartB=true`，被大多数查询过滤）

**PC 数据库导入**：`PcDatabaseImporter`（顶层 object），支持 3NF 和 flat 两种 schema，三级流程（预加载 → 容差匹配 → 事务批量写入）

## 开发注意事项

- **Room KSP**：`ksp { arg("room.generateKotlin", "true") }`，构建前需生成代码
- **Robolectric 测试**：`@Config(sdk = [34])`，JVM 模拟 Android 环境
- **CMake**：3.22.1，C++17，prefab 启用
- **配置缓存**：`org.gradle.configuration-cache=true`（默认开启，缓存问题可临时禁用）
- **调试安装**：`android.injected.testOnly=false` 解决调试弹窗
- **双指纹去重**：`insertOrUpdateSong` 先用 fileName+duration 匹配，失败再用 filePath 匹配
- **Native 库加载**：`NativeAudio` 需同时加载 `seamlessloopmobile` 和 `loopfinder` 两个原生库
- **A/B 过滤**：含 `isAbPartB=true` 的歌曲默认在 UI 列表查询中被排除（由 `SongDao` 查询逻辑保证）

## 包结构速查

| 路径 | 职责 |
|------|------|
| `audio/` | PlaybackService, PlaybackManager (IMultiPlayer), MediaControlManager, QueueManager, AudioFocusManager, Notify, HeadsetPlugReceiver, SystemMediaProgressSyncController 等 |
| `data/` | MusicRepository + SongRepository + PlaylistRepository + MusicScannerRepository + SettingsManager |
| `db/` | AppDatabase + DateConverter + PcDatabaseImporter（实体/DAO 已移到 model/） |
| `model/` | 9 个 Room 实体 + 3 个 DAO + Song(Lookup POJO) + SongMetadataUpdate + LibraryItem + Folder 等 15 文件 |
| `ui/screen/` | MainScreen + HomeScreen + CategoryScreen + SongListScreen + PlayingPanel |
| `ui/components/` | CentralizedDialogHost, MiniPlayer, PlayingComponents, FineTuneComponents, ListItems |
| `viewmodel/` | MainViewModel + LibraryViewModel + SelectionViewModel + PlaylistViewModel + MainViewModelFactory |
| `scanner/` | AudioScanner（Object，MediaStore 扫描；采样点在后续原生层处理） |
| `jni/` | NativeAudio（Kotlin object，JNI 入口）、LoopPoint（原生层返回的数据类） |
| `utils/` | TimeUtils |

## 注意

**AB式音乐**在本项目专指一首可循环歌曲分为两个音乐文件，A段是intro，B段是loop，与其他的一首可循环歌曲一个音乐文件不同。
文件名含 `_B`、`_b`、`_loop`、`_Loop` 后缀的文件被标记为 B 段（`isAbPartB=true`），在 UI 列表中默认隐藏。
