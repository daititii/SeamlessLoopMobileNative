# SeamlessLoopMobile

一个运行在 Android 平台上的高性能本地音乐播放器，核心特色是支持**采样级无缝循环播放**功能。

## 功能特性

- **采样级无缝循环**：精确到采样点的循环控制，实现真正的无缝衔接
- **AB 循环模式**：支持 Intro + Loop 两个文件逻辑合体，自动拼接循环
- **桌面端数据继承**：兼容桌面端 SQLite 数据库，可导入 `.db` 文件同步循环配置
- **Jetpack Compose UI**：采用现代声明式 UI 框架，界面简洁流畅
- **后台播放服务**：通过 MediaBrowserService 实现稳定的后台播放
- **系统深度集成**：支持锁屏播放控制、通知栏、蓝牙/有线耳机按键控制

## 技术架构

| 层级 | 技术栈 |
|------|--------|
| UI 层 | Jetpack Compose + Material3 |
| 应用层 | Kotlin + Jetpack (ViewModel, Room, LiveData) |
| 服务层 | PlaybackService (MediaBrowserService) |
| 数据层 | Room (SQLite) + ContentResolver |
| 底层音频 | C++ (NDK) + Google Oboe + MiniMP3 |

## 项目结构

```
app/src/main/
├── cpp/                    # C++ 音频引擎 (Native 层)
│   ├── AudioEngine.h/cpp   # 音频引擎核心
│   ├── AudioDecoder.cpp    # 解码组件
│   └── native-lib.cpp      # JNI 桥接层
└── java/com/cpu/seamlessloopmobile/
    ├── audio/              # 播放服务核心
    ├── data/               # 数据仓库
    ├── db/                 # 数据库定义
    ├── viewmodel/          # 状态管理
    ├── scanner/            # 媒体扫描
    └── ui/                 # Compose 界面
```

## 构建要求

- **Gradle**: 8.x
- **Android SDK**: API 26+ (minSdk)
- **NDK**: CMake 3.22.1

## 构建运行

```bash
# 同步项目
./gradlew sync

# 构建调试版
./gradlew assembleDebug

# 构建发布版
./gradlew assembleRelease
```

## 核心模块说明

- **PlaybackService**: 维持后台生命周期，管理音频焦点及 MediaSession
- **PlaybackManager**: 音频调度器，负责引擎与上层通信
- **AudioEngine**: C++ 实现，基于 Oboe 的 PCM 流式输出
- **MusicRepository**: 统一协调本地数据库与系统媒体库

---

**版本**: 1.0  
**平台**: Android 6.0+