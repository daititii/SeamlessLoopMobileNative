# SeamlessLoopMobile 🔄

Android 端高性能**无缝循环音频播放器**，采用 Kotlin + C++ (Oboe) 混合架构，专门为实现游戏音乐及各种循环音频的完美无缝衔接而设计。

现在支持 PC 端数据库导入与手机端数据导出为 PC 端可识别数据库；手机上的循环点、评分、歌单等修改可以通过“导出 PC 端数据库”传回电脑端使用。

![a0e7472702aab23e61a4fb1f948aa075](./image/README/a0e7472702aab23e61a4fb1f948aa075.jpg)

---

## 📱 核心功能与亮点

- **高性能 Native 播放引擎**：基于 Google Oboe 1.9.3 + NDK minimp3 解码器，实现亚毫秒级别的低延迟无缝循环播放。
- **自动循环点探测引擎 (`loopfinder`)**：集成基于 FFT & Chroma 提取的高级音频算法，支持一键分析音频的最佳循环衔接点（提供 Top 5 候选推荐）。
- **A/B 式音乐支持**：独创支持由 Intro 段（A轨）和 Loop 段（B轨）组成的双轨歌曲无缝拼接播放，自动标记 B 轨并于 UI 中优雅过滤。
- **Room 3NF 数据持久化**：精心设计的 Room 2.7.0-alpha11 规范 3NF 数据库，支持双指纹去重（优先匹配 `FileName+Duration`，兜底 `FilePath` 匹配）。

---

## 🚀 用户使用指南

### 💻 导入 / 导出 PC 数据库
> [!TIP]
> **点击主界面右上角的设置入口，进入“数据同步与管理”。**
> - **导入 PC 端数据库**：选择电脑端 `.db` 文件，APP 会自动进行容差匹配和增量批量写入，补充手机端没有的循环点、候选循环点、评分、歌单等数据。
> - **导出 PC 端数据库**：将手机端当前的歌曲元数据、循环点、候选循环点、评分、歌单与队列转换为 PC 端 3NF schema，可传回电脑端继续使用。

下面以微信传输助手为例讲解同步方法：

1.首先**下载**电脑端传输的db文件

![3ebc5a27a7cd3b6641e3182d7d9f0cb3](./image/README/3ebc5a27a7cd3b6641e3182d7d9f0cb3.jpg)

2.在播放器的设置界面寻找到db文件，如果一开始没有找到db文件，可以在文件夹的“下载”位置找到

![e05ae63c6360b5ffa2cac43a7409e50f](./image/README/e05ae63c6360b5ffa2cac43a7409e50f.jpg)

3.找到所需的db文件，按下即同步

![0431d448f108bd3314c14b89944bc93c](./image/README/0431d448f108bd3314c14b89944bc93c.jpg)

导出时在同一设置区域点击 **“导出 PC 端数据库”**，选择保存位置即可。导出的文件名默认为：

```text
seamless_loop_pc_export_yyyyMMdd_HHmmss.db
```





---

## 🛠️ 开发者快速上手

### 💻 运行环境
- **操作系统**：原生 Windows 11 (命令行推荐使用 PowerShell)。
- **编译依赖**：Min SDK 26, Target SDK 35, Gradle 9.1.0, Kotlin 2.1.0, Room KSP 启用。

### ⌨️ 常用开发命令
- **编译 Debug 包**：
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **运行单元测试**：
  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```
- **运行特定测试（例如播放模式测试）**：
  ```powershell
  .\gradlew.bat testDebugUnitTest --tests "com.cpu.seamlessloopmobile.viewmodel.PlayModeTest"
  ```
- **一键调试部署 (需 Root 设备)**：
  运行根目录下的部署脚本：
  ```powershell
  .\run.bat
  ```

---

## 📖 深入架构与避坑说明
如果您是参与本项目的**智能体(AI)** 或 **人类开发者**，在进行任何实质性代码修改前，请**务必先阅读 [AGENTS.md](file:///D:/seamless%20loop%20music/SeamlessLoopMobile/AGENTS.md) 了解以下关键细节**：
1. **构建天坑**：`app/build.gradle.kts` 中 `kotlin-android` 插件必须保持注释！
2. **JNI / fopen 路径限制**：Native 音频分析无法直接读取 `content://`，必须先拷贝到私有 cache 目录。
3. **Room Schema**：Room 9张实体表和3个DAO的详细映射图。
4. **包结构速查**：子模块（audio, data, db, viewmodel, model, scanner, jni 等）职责定义。
