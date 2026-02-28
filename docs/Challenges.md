# SeamlessLoopMobile 手机端开发挑战与官方文档汇总

本指南总结了在 Android 平台上实现“采样级无缝循环播放器”过程中遇到的限制、困难，以及对应的官方参考文档，方便后期维护与查阅。

---

## 一、 开发困难与平台限制总结

### 1. 锁屏控制与后台播放限制 (Foreground & Media)
*   **挑战**：Android 系统为了省电，常常会在后台杀死应用进程。为了让音乐一直播放并在锁屏/通知栏上显示控制按钮，必须深度整合 `Foreground Service`（前台服务）、`Notification`（常驻通知）和 `MediaSessionCompat`。
*   **痛点**：不同 Android 版本（尤其是 8.0 到 13.0）的 API 行为差异极大，稍有不慎就会导致按钮失效或播放被系统强制中断。

### 2. UI 框架与多线程的封装复杂性 (Jetpack & Lifecycle)
*   **挑战**：UI 只能在主线程更新，而数据库查询（Room）和解码逻辑必须在子线程。
*   **痛点**：跨模块（Service 到 Activity）的状态实时同步（如刷新列表、进度条更新）非常麻烦，对 `ViewModel`、`LiveData` 和组件生命周期的管理要求极高。

### 3. 应用生命周期的不确定性 (Persistence)
*   **挑战**：用户随时可能在多任务栏“划掉”应用。
*   **痛点**：如何在应用被杀死前的瞬间，准确、平衡地保存当前播放位置和用户设置（如“保存最后一首播放”的功能），而不产生性能拖累。

### 4. JNI 桥接与 C++ 底层调试 (NDK & Oboe)
*   **挑战**：Kotlin 应用层与 C++ 音频引擎（`AudioEngine.cpp`）之间存在频繁的数据交互和指令传递。
*   **痛点**：JNI 调用复杂且容易产生内存泄漏。C++ 端的致命错误（如 `SIGSEGV` 指针越界）会导致应用直接冷启动，且报错信息难以精确追踪到源代码行。

### 5. 存储访问权限封锁 (Scoped Storage)
*   **挑战**：Android 11+ 强制执行“分区存储”，应用不再能自由访问外部 SD 卡或公共文件夹。
*   **痛点**：为了实现扫描音乐文件和导入 PC 端 `.db` 文件，需要编写繁琐的权限适配代码（`READ_EXTERNAL_STORAGE` 与 `READ_MEDIA_AUDIO`）。

---

## 二、 官方开发文档直通车 (必读秘籍)

### 1. 核心与架构
*   **Android 开发者官网**: [developer.android.com](https://developer.android.com/)
*   **应用架构指南 (MVVM)**: [推荐架构最佳实践](https://developer.android.com/topic/architecture)
*   **协程指南 (Kotlin Coroutines)**: [处理异步与后台任务](https://developer.android.com/kotlin/coroutines)

### 2. 后台服务与媒体控制
*   **前台服务指南**: [如何维持后台常驻服务](https://developer.android.com/guide/components/foreground-services)
*   **音频应用开发概览**: [构建高质量音频应用](https://developer.android.com/guide/topics/media-apps/audio-app/building-an-audio-app)
*   **MediaSession 使用**: [处理锁屏与媒体按键逻辑](https://developer.android.com/guide/topics/media-apps/working-with-a-media-session)

### 3. NDK (底层 C++ / JNI)
*   **NDK 原生开发套件**: [原生 C/C++ 开发指南](https://developer.android.com/ndk/guides)
*   **JNI 最佳实践 (防闪退必读)**: [Android JNI Tips](https://developer.android.com/training/articles/perf-jni)
*   **Oboe 高性能音频库**: [Google Oboe GitHub / Doc](https://github.com/google/oboe)

### 4. 权限与存储
*   **Android 存储系统简介**: [分区存储与普通存储区别](https://developer.android.com/training/data-storage)
*   **请求应用权限**: [如何处理运行时权限请求](https://developer.android.com/training/permissions/requesting)
