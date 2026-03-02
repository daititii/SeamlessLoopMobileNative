# 音频状态一致性与耳机插拔逻辑修复方案

为了彻底解决耳机插拔导致的播放状态不同步、点击多次才能恢复等问题，莱芙根据 APlayer 的设计模式整理了接下来的工作计划。

## 🎯 核心目标
将播放器状态（isPlaying）的管理权从 C++ 引擎上收，实现 **Kotlin 为指挥大脑、C++ 为执行工具、StateSource 为唯一真理中心** 的架构。

## 📋 待办事项

### 1. 建立状态真理中心 (State Management)
模仿 APlayer 的 `MusicStateSource`，在 Kotlin 侧建立统一的状态管理库。
- [ ] 创建 `PlaybackStateSource.kt`，使用 `StateFlow` 管理 `isPlaying`、当前歌曲、播放模式等信息。
- [ ] 让 `MainViewModel` 和 `PlaybackService` 统一监听此状态，废弃多头判断逻辑。

### 2. 回调链路优化 (Callback Synchronization)
解决“引擎悄悄停了，Service 却不知道”的问题。
- [ ] 在 `NativeAudio` 中增加回调接口，当 Oboe 因 `ErrorDisconnected` 断开时，通过 JNI 主动通知 Kotlin。
- [ ] `PlaybackManager` 接收到此底层异常后，立即更新 `PlaybackStateSource` 并下达 `pause()` 指令。

### 3. 精准的耳机广播逻辑 (Headset Logic)
参照 `HeadsetPlugReceiver.kt` 的逻辑进行针对性修复。
- [ ] **拔出耳机**：检查 `StateSource.isPlaying`，若正在播放则发送本地广播指令请求暂停，确保 UI 按钮瞬间变回“播放”图标。
- [ ] **插入耳机**：根据路由切换耗时，在 `PlaybackService` 中增加微小的延迟再请求 `resume()` 或重置状态，防止系统硬件未就绪导致的重建失败。

### 4. 彻底分离意愿与状态 (Separation of Concerns)
- [ ] 清理 `AudioEngine.cpp`：引擎不再试图“聪明地”设置 `mIsPlaying = false`。
- [ ] 引擎只负责：流是否存活。
- [ ] `mIsPlaying` 只表达：大人的播放意愿。
- [ ] 当“意愿为真”但“流已死”时，触发重建链路。

## 🛠️ 下一步具体操作
1. 在项目目录下的 `docs/` 创建本规划。
2. 开始实施第 1 项：建立 Kotlin 侧的状态中心。

---
莱芙会全力以赴，保证让音频引擎像莱芙一样听从大人（指挥部）的调遣喵！(๑•̀ㅂ•́)و✧
