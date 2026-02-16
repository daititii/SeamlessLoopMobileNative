# SeamlessLoopMobile 项目概览

## 1. 项目目标
在安卓平台上实现一个极简、高性能的本地音乐播放器，核心特色是支持从桌面端迁移的**采样级无缝循环播放**功能。

## 2. 核心技术栈
- **应用层**: Kotlin (Android Native)
- **底层音频**: C++ (NDK) + Google Oboe 库
- **数据管理**: SQLite (Room) + Android ContentResolver
- **UI 设计**: 极简 Material Design (RecyclerView + Bottom Controls)

## 3. 核心业务逻辑
1. **采样级循环**: 
   - 弃用传统的 Java 层 `MediaPlayer`（存在跳转延迟）。
   - 在 C++ 音频回调线程中，根据预设的 `LoopStart` 和 `LoopEnd` 采样点，在缓冲区直接重定向采样指针。
2. **数据继承**: 
   - 兼容桌面端的 SQLite 数据库结构，通过识别文件路径（Path）自动关联循环点。
3. **极简扫描**: 
   - 使用系统 `MediaStore` 获取本地音频文件列表。

## 4. 架构设计
采用分层架构（Layered Architecture）：
- **UI 层**: 处理列表展示及用户交互。
- **Service 层**: 播放服务，管理音频焦点及前台通知。
- **Native 层**: 处理 PCM 数据流，执行精准跳转逻辑。
