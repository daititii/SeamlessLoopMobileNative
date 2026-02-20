# SeamlessLoopMobile 开发日志 - 第一阶段与第二阶段

**项目名称**: SeamlessLoopMobile  
**开发时间**: 2026-02-16 23:30 - 2026-02-17 01:02  
**开发阶段**: 第一阶段（地基搭建）+ 第二阶段（音频心脏）初步实现  
**状态**: 基础架构完成，音频引擎已集成但存在音质问题待解决

---

## 一、第一阶段：地基搭建（已完成）

### 1.1 数据模型层
**文件**: `app/src/main/java/com/cpu/seamlessloopmobile/model/Song.kt`

**实现内容**:
- 创建 `Song` 数据类，字段设计完全同步桌面端 `LoopPoints` 表结构
- 核心字段包括：
  - `fileName`: 文件名（带扩展名）
  - `filePath`: 物理路径
  - `totalSamples`: 总采样数（核心识别指纹）
  - `loopStart` / `loopEnd`: 循环起始/结束采样点
  - `loopCandidatesJson`: 候选循环点缓存
  - `displayName`: 显示别名
  - `lastModified`: 最后修改时间

**技术要点**:
- 采用 `data class` 确保自动生成 `equals()` 和 `hashCode()`
- 使用 `totalSamples` 作为音频指纹，与桌面端保持一致的识别逻辑

---

### 1.2 音频扫描器
**文件**: `app/src/main/java/com/cpu/seamlessloopmobile/scanner/AudioScanner.kt`

**实现内容**:
- 基于 Android `ContentResolver` 查询系统媒体库
- 过滤条件：
  - `IS_MUSIC != 0`: 仅音乐文件
  - `DURATION >= 10000`: 时长 ≥ 10 秒
- 物理文件存在性验证（`File.exists()`）

**扫描范围**:
- 外部存储（`EXTERNAL_CONTENT_URI`）
- 自动排除系统铃声、通知音、微信语音等非音乐文件

**已知限制**:
- `totalSamples` 字段当前初始化为 0，需后续通过解码器获取精确值

---

### 1.3 UI 层实现
**文件**:
- `app/src/main/res/layout/activity_main.xml`: 主界面布局
- `app/src/main/res/layout/item_song.xml`: 列表项布局
- `app/src/main/java/com/cpu/seamlessloopmobile/adapter/SongAdapter.kt`: RecyclerView 适配器

**实现内容**:
1. **主界面布局**:
   - Material Toolbar（标题栏）
   - RecyclerView（歌曲列表）
   - 底部播放控制栏占位

2. **列表项设计**:
   - 播放图标（左侧）
   - 歌曲标题 + 艺术家信息（中间）
   - 循环状态指示器（∞ 符号，右侧）

3. **适配器优化**:
   - 使用 `DiffUtil` 实现增量刷新
   - 支持大数据量场景（1000+ 歌曲）
   - 通过 `areItemsTheSame` 和 `areContentsTheSame` 精确对比

**主题适配问题及解决**:
- **问题**: 初始使用 Material 3 属性（`?attr/colorSurfaceContainer`、`?attr/colorOnSurface`）导致崩溃
- **原因**: 项目主题为 Material 2（`Theme.MaterialComponents`）
- **解决**: 替换为兼容属性（`?attr/colorPrimaryVariant`、`@android:color/white`）

---

### 1.4 权限管理
**文件**: `app/src/main/AndroidManifest.xml`

**实现内容**:
- 声明存储读取权限：
  - `READ_EXTERNAL_STORAGE`（Android 12 及以下）
  - `READ_MEDIA_AUDIO`（Android 13+）
- 在 `MainActivity.kt` 中实现动态权限请求，兼容不同 Android 版本

---

## 二、第二阶段：音频心脏（初步实现）

### 2.1 依赖配置
**文件**: `app/build.gradle.kts`

**实现内容**:
1. 引入 Google Oboe 库：
   ```kotlin
   implementation("com.google.oboe:oboe:1.9.3")
   ```

2. 启用 Prefab 构建特性：
   ```kotlin
   buildFeatures {
       prefab = true
   }
   ```

3. 配置 C++ 标准库：
   ```kotlin
   arguments += "-DANDROID_STL=c++_shared"
   ```

**技术要点**:
- Oboe 要求使用共享 STL（`c++_shared`），否则会报链接错误
- Prefab 允许 CMake 自动发现 AAR 包中的 C++ 库

---

### 2.2 CMake 构建配置
**文件**: `app/src/main/cpp/CMakeLists.txt`

**实现内容**:
```cmake
find_package(oboe REQUIRED CONFIG)

add_library(seamlessloopmobile SHARED
    native-lib.cpp
    AudioEngine.cpp)

target_link_libraries(seamlessloopmobile
    oboe::oboe
    android
    log)
```

**技术要点**:
- `find_package(oboe REQUIRED CONFIG)`: 通过 Prefab 查找 Oboe
- 链接 `android` 和 `log` 库以支持日志输出

---

### 2.3 音频引擎核心
**文件**: 
- `app/src/main/cpp/AudioEngine.h`
- `app/src/main/cpp/AudioEngine.cpp`

**架构设计**:
```
AudioEngine (继承 oboe::AudioStreamCallback)
├── start()          // 启动音频流
├── stop()           // 停止音频流
├── setLoopPoints()  // 设置循环点
├── loadAudioSource() // 加载音频数据
└── onAudioReady()   // Oboe 回调（核心循环逻辑）
```

**核心实现**:

1. **音频流配置**:
   ```cpp
   builder.setFormat(oboe::AudioFormat::Float);
   builder.setChannelCount(2);
   builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
   builder.setSharingMode(oboe::SharingMode::Shared);
   ```

2. **采样级循环逻辑**（`onAudioReady` 回调）:
   ```cpp
   if (mIsLooping && mCurrentReadFrame >= mLoopEndFrame) {
       mCurrentReadFrame = mLoopStartFrame.load(); // 瞬间跳回
   }
   ```

3. **线程安全设计**:
   - 使用 `std::atomic<int64_t>` 保护循环点变量
   - 避免主线程与音频线程的竞态条件

**已知问题**:
- 当前使用模拟正弦波（440Hz）作为测试音源
- 实际播放存在杂音，可能原因：
  1. 采样率匹配问题（已尝试动态获取，但仍有问题）
  2. 缓冲区大小不匹配
  3. 数据类型转换问题
  4. 循环点设置不当

---

### 2.4 JNI 桥接层
**文件**: `app/src/main/cpp/native-lib.cpp`

**实现内容**:
```cpp
// 启动引擎
Java_com_cpu_seamlessloopmobile_MainActivity_startAudioEngine()

// 停止引擎
Java_com_cpu_seamlessloopmobile_MainActivity_stopAudioEngine()

// 设置循环点
Java_com_cpu_seamlessloopmobile_MainActivity_setLoopPoints(jlong start, jlong end)
```

**调用流程**:
1. Kotlin 层调用 `startAudioEngine()`
2. JNI 创建 `AudioEngine` 实例
3. 启动 Oboe 音频流
4. 加载测试音频数据
5. 开始播放

**技术要点**:
- 函数签名必须严格匹配 JNI 命名规范
- 使用全局静态指针 `audioEngine` 管理引擎生命周期

---

### 2.5 Kotlin 层集成
**文件**: `app/src/main/java/com/cpu/seamlessloopmobile/MainActivity.kt`

**实现内容**:
```kotlin
external fun startAudioEngine()
external fun stopAudioEngine()
external fun setLoopPoints(start: Long, end: Long)

companion object {
    init {
        System.loadLibrary("seamlessloopmobile")
    }
}
```

**点击事件处理**:
```kotlin
adapter = SongAdapter(emptyList()) { song ->
    stopAudioEngine()
    startAudioEngine()
    setLoopPoints(44100, 44100 * 5) // 1-5 秒循环
}
```

---

## 三、遇到的技术难点及解决方案

### 3.1 应用启动崩溃
**问题**: 应用点击后立即退出

**排查过程**:
1. 初次崩溃：JNI 库加载失败（`System.loadLibrary` 找不到库）
   - **解决**: 暂时注释 JNI 代码，先验证 Kotlin 层逻辑

2. 二次崩溃：Material 3 属性不兼容
   - **错误**: `UnsupportedOperationException: Failed to resolve attribute`
   - **原因**: 使用了 `?attr/colorSurfaceContainer` 等 Material 3 专属属性
   - **解决**: 替换为 Material 2 兼容属性

### 3.2 C++ 编译错误
**问题 1**: STL 链接冲突
```
User is using a static STL but library requires a shared STL
```
**解决**: 在 `build.gradle.kts` 中添加 `arguments += "-DANDROID_STL=c++_shared"`

**问题 2**: `std::atomic` 赋值错误
```
error: overload resolution selected deleted operator '='
mCurrentReadFrame = mLoopStartFrame;
```
**原因**: 不能直接将一个 `std::atomic` 赋值给另一个 `std::atomic`  
**解决**: 使用 `.load()` 显式读取值：
```cpp
mCurrentReadFrame = mLoopStartFrame.load();
```

### 3.3 音频杂音问题（未解决）
**现象**: 播放时出现低沉杂音，调整采样率后杂音特征改变但仍存在

**已尝试方案**:
1. 动态获取 Oboe 实际采样率
2. 调整 `start()` 和 `loadAudioSource()` 调用顺序

**可能原因**:
1. 缓冲区欠载（Underrun）：音频回调速度跟不上播放速度
2. 数据对齐问题：立体声通道数据交错存储可能有误
3. 循环点设置：`setLoopPoints(44100, 44100 * 5)` 可能与实际采样率不符
4. 音频格式：Float 格式的数值范围可能超出 [-1.0, 1.0]

---

## 四、当前项目状态

### 4.1 已完成功能
✅ 数据模型定义（与桌面端同步）  
✅ 系统媒体库扫描  
✅ 歌曲列表展示（支持 DiffUtil 优化）  
✅ 权限管理（兼容多版本 Android）  
✅ Oboe 音频引擎集成  
✅ JNI 桥接层实现  
✅ 采样级循环逻辑框架  
✅ 应用可正常启动并播放音频（虽有杂音）

### 4.2 待解决问题
⚠️ 音频播放杂音问题  
⚠️ 真实音频文件解码（当前仅支持模拟正弦波）  
⚠️ 循环点精度验证  
⚠️ 性能优化（缓冲区管理）

### 4.3 未实现功能
❌ SQLite 数据库持久化（Room）  
❌ 手动调节循环点界面  
❌ 真实音频解码器（FFmpeg / MediaCodec）  
❌ 前台服务（后台播放）  
❌ 采样插值优化（消除循环跳转噪音）

---

## 五、下一步工作计划

### 优先级 P0（紧急）
1. **排查音频杂音问题**:
   - 添加详细日志输出（采样率、缓冲区大小、回调频率）
   - 验证 Float 数据范围是否正确
   - 检查立体声数据交错存储逻辑

2. **简化测试场景**:
   - 先播放单声道音频
   - 使用更简单的波形（方波 / 锯齿波）
   - 缩短循环区间（0.5 秒）

### 优先级 P1（重要）
3. **引入音频解码器**:
   - 评估 FFmpeg vs NDK MediaCodec
   - 实现 MP3/FLAC 解码为 PCM

4. **实现 Room 数据库**:
   - 创建 `SongDao` 和 `AppDatabase`
   - 持久化循环点数据

### 优先级 P2（常规）
5. **完善 UI 交互**:
   - 播放/暂停按钮
   - 进度条显示
   - 循环点可视化标记

---

## 六、技术总结

### 6.1 关键技术栈
- **应用层**: Kotlin + Android SDK
- **UI 框架**: Material Components + RecyclerView + ViewBinding
- **音频引擎**: C++ + Google Oboe
- **构建工具**: Gradle + CMake + Prefab
- **跨语言通信**: JNI

### 6.2 核心技术亮点
1. **采样级精度循环**: 在 C++ 音频回调中直接操作采样指针，理论延迟 < 1ms
2. **线程安全设计**: 使用 `std::atomic` 保护共享变量
3. **高性能列表**: DiffUtil 增量刷新，支持千级数据量
4. **跨平台数据兼容**: 模型设计与桌面端完全一致

### 6.3 经验教训
1. **Material 版本兼容性**: 必须确认主题版本再使用对应属性
2. **C++ STL 选择**: Oboe 等第三方库通常要求 `c++_shared`
3. **JNI 调试难度**: 函数签名错误会导致运行时崩溃，需严格遵循命名规范
4. **音频开发复杂性**: 采样率、缓冲区、线程同步等细节极易出错

---

## 七、第三阶段：性能优化与交互完善（本日更新）

### 7.1 音频引擎架构深度重构
**文件**: 
- `app/src/main/cpp/AudioEngine.h`
- `app/src/main/cpp/AudioEngine.cpp`
- `app/src/main/cpp/AudioDecoder.cpp`

**实现内容**:
1. **异步解码机制**:
   - 引入后台解码线程 `mDecodingThread` 与 2 秒容错深度的环形缓冲区 (FIFO)。
   - **原理**: 解码工作与播放回调解耦。后台线程预先填充数据，播放回调仅进行 FIFO 读取。
   - **成果**: 彻底消灭了由 MediaCodec 解码抖动引起的 Underrun（刺啦杂音）。

2. **采样级精准寻道**:
   - 优化了 `seekToFrame` 逻辑，采用 `PREVIOUS_SYNC` 结合 PTS 帧丢弃方案。
   - **成果**: 实现了物理波形级的无缝循环跳转，跳转精度达到单个采样点。

---

### 7.2 交互功能与 UI 升级
**文件**: 
- `app/src/main/java/com/cpu/seamlessloopmobile/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`

**实现内容**:
1. **扁平文件夹浏览**:
   - 实现了按物理路径自动归类歌曲。
   - 采用“文件夹库 -> 歌曲列表”的双模交互，极大提升了大曲库下的选歌效率。

2. **完整播放控制**:
   - 底部控制栏升级，增加上一首、播放/暂停、下一首物理按钮。
   - 进度条支持动态采样率计算，确保进度显示与听感同步。

---

## 八、当前项目状态 (2026-02-18 更新)

### 8.1 已完成功能
✅ **异步解码架构**（解决音质刺啦声）  
✅ **采样级精准寻道**  
✅ **扁平化文件夹浏览模式**  
✅ **完整播放控制交互**（播放/暂停/切歌）  
✅ **动态采样率进度计算**  

### 8.2 待解决问题
⚠️ **A/B Loop 拼接逻辑**：需实现桌面端的 A+B 连续循环播放逻辑。  
⚠️ **UI 美化**：当前的 Material 2 界面仍有优化空间。  

---

**记录版本**: v0.3-alpha  
**日志撰写时间**: 2026-02-18 23:25  
**撰写人**: 莱芙・泽诺 (Lev Zenith)  
**审核状态**: 待 CPU 大人审阅
