# SeamlessLoopMobile 单元测试指南

本文档记录了项目中单元测试的编写标准与实践方法。为了保证播放器的稳定性和业务逻辑的独立性，本项目采用了分层测试策略。

## 一、 测试目录结构

Android 项目的标准测试目录分为两种：

1. **`app/src/test/java/...`** (本地单元测试)
   - **特点**：直接在开发机器的 JVM（电脑 CPU）上运行，速度极快（毫秒/秒级）。
   - **适用场景**：纯 Kotlin/Java 业务逻辑计算（如时间转换、播放列表顺序计算），以及不涉及复杂 UI 交互的系统模拟（使用 Robolectric）。
   - **本项目实践**：本项目的单元测试均放置于此目录下。

2. **`app/src/androidTest/java/...`** (Android 仪表测试)
   - **特点**：需要打包 APK 并安装到真实 Android 设备或虚拟机上运行，速度较慢。
   - **适用场景**：必须在真实 Android 环境中验证的硬件、UI 交互、底层框架（如复杂的 SQLite 查询或特定机型的媒体库表现）。

---

## 二、 已编写的测试用例

### 1. 纯业务逻辑测试：`PlayModeTest`
- **路径**：`app/src/test/java/com/cpu/seamlessloopmobile/viewmodel/PlayModeTest.kt`
- **测试目标**：验证 `MainViewModel` 中负责决定“上一首/下一首”播放顺序的核心数学逻辑。
- **技术要点**：
  - 不依赖任何 Android 环境。
  - 直接实例化包含假数据的歌曲列表 (`List<Song>`)。
  - 验证在不同播放模式下（如单曲循环、列表循环），经过边界计算后得出的下一首索引号是否正确。
- **核心价值**：确保复杂的状态机与切歌逻辑不会导致死循环或数组越界崩溃。

### 2. Android 依赖隔离测试：`AudioScannerTest`
- **路径**：`app/src/test/java/com/cpu/seamlessloopmobile/scanner/AudioScannerTest.kt`
- **测试目标**：验证 `AudioScanner` 扫描设备媒体库时的数据映射和条件过滤逻辑。
- **技术要点**：
  - **Robolectric 框架**：使用了黑科技 `@RunWith(RobolectricTestRunner::class)`，让代码可以在电脑上“假装”运行在一个 Android 系统中。为了兼容性，指定了最高支持的 SDK 为 34（`@Config(sdk = [34])`）。
  - **Shadow & Mock**：`AudioScanner` 强依赖系统的 `ContentResolver` 去读取 `MediaStore` 数据库。在测试中，我们使用了 Robolectric 提供的 `RoboCursor` 来伪造一份包含测试用例的虚拟查询结果表格。
  - **强制接管查询**：通过 `Shadows.shadowOf(contentResolver).setCursor()`，强行将我们画好的标准数据表格递交给了正在尝试查询底层数据库的扫描逻辑。
- **核心价值**：实现了对高耦合度、涉及系统 API 调用的组件进行快速的隔离测试，无需每次修改扫描条件就构建安装包并在真机上抓取文件。

### 3. 时间测量精度测试：`TimeUtilsTest`
- **路径**：`app/src/test/java/com/cpu/seamlessloopmobile/utils/TimeUtilsTest.kt`
- **测试目标**：验证在进行无缝循环时，底层 C++ 的采样帧（Frames）与 UI 展示的毫秒时间（Milliseconds）之间双向转换的绝对精确度。
- **技术要点**：
  - 纯 Kotlin 函数计算，不带任何环境依赖。
  - 通过注入不同且离散的采样率（如 44100Hz, 48000Hz，以及错误防跌落的 0Hz）及各种边界数字进行运算对抗。
- **核心价值**：保护进度条与时间的正确物理换算基础，杜绝 1 帧的偏移所带来的微小爆音隐患。

### 4. AB伴侣搜寻健壮性测试：`ABLoopTest`
- **路径**：`app/src/test/java/com/cpu/seamlessloopmobile/data/ABLoopTest.kt`
- **测试目标**：核心验证 `MusicRepository` 中负责发现 AB 段业务算法的全面性和灾难恢复能力（如跨系统游标搜救）。
- **技术要点**：
  - **Mockito 拦截流**：没有使用 Robolectric，而是使用极致轻量级的 `Mockito` 创造数据（Fake Data）。
  - **伪造文件系统深渊**：当数据库查询落空（返回空白）时，通过深度伪造 `Context`、`ContentResolver` 和游标 `Cursor`，模拟程序在 `MediaStore` 里抓取孤儿文件的极端场景。
- **核心价值**：完美重现了“新文件未入库因此无法启动 AB 模式”的历史遗留 Bug，用单元测试彻底堵死了该盲区。

---

## 三、 未来测试建议与行动方向

1. **重构解耦**：当发现一个类的测试需要编写大量的“虚假环境（Mock）”时（例如 `PlaybackManager` 和 JNI 耦合较深），这通常意味着代码的职责过重，需要将其业务逻辑判断剥离到更加纯粹的架构层中。
2. **UI 交互边界保护**：在后续开发中引入对 RecyclerView 列表状态或选歌界面的 Instrumented UI Test，防止手滑或者内存回收造成界面的迷失。
