# AGENTS.md - SeamlessLoopMobile

## 项目概览

Android 无缝循环音频播放器，Kotlin + C++ (Oboe) 混合架构。
单模块项目 (`:app`)，Min SDK 26，Compile/Target SDK 35。

## 构建与测试

```bash
# Windows 使用 gradlew.bat
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest

# 运行单个测试类
gradlew.bat testDebugUnitTest --tests "com.cpu.seamlessloopmobile.viewmodel.PlayModeTest"

# 仪表测试（需要设备）
gradlew.bat connectedAndroidTest
```

## 架构要点

- **导航**：自定义 `MusicUiState` 密封类 + `AnimatedContent`，未使用 Navigation 组件
- **服务**：`PlaybackService` (MediaBrowserService) 后台播放，`MediaControlManager` 管理媒体会话
- **数据**：`MusicRepository` 聚合 `SongRepository`/`PlaylistRepository`/`MusicScannerRepository`，Room 数据库
- **Native 层**：`app/src/main/cpp/` — Oboe 音频引擎 + NDK 解码器，JNI 桥接在 `native-lib.cpp`
- **UI**：Jetpack Compose + Material3，状态通过 `MainViewModel` + `MediaControlManager` 的 StateFlow/LiveData 驱动

## 开发注意事项

- **Room KSP**：`ksp { arg("room.generateKotlin", "true") }`，构建前需生成代码
- **Robolectric 测试**：使用 `@Config(sdk = [34])`，本地测试运行在 JVM 模拟 Android 环境
- **CMake**：3.22.1，C++17，NDK 解码器 + Oboe 1.9.3，`app/src/main/cpp/CMakeLists.txt`
- **配置缓存**：`org.gradle.configuration-cache=true`，如遇缓存问题可临时关闭
- **调试安装**：`android.injected.testOnly=false` 解决调试弹窗问题

## 包结构速查

| 路径 | 职责 |
|------|------|
| `audio/` | PlaybackService, PlaybackManager, MediaControlManager, AudioFocusManager |
| `data/` | MusicRepository (Facade), SettingsManager |
| `db/` | AppDatabase, Song/Playlist/Folder 实体与 DAO, PcDatabaseImporter |
| `ui/screen/` | Compose 屏幕：Home, Category, SongList, PlayingPanel |
| `ui/components/` | 可复用组件 (MiniPlayer 等) |
| `scanner/` | AudioScanner (媒体库扫描 + 采样点计数) |
| `jni/` | NativeAudio (JNI Java 侧入口) |
| `model/` | 数据实体与 DAO 接口 |

## 参考文档

- `docs/Project_Architecture.md` — 完整架构说明
- `docs/Unit_Testing_Guide.md` — 测试编写指南
