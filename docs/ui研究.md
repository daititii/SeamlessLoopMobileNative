# SeamlessLoopMobile UI 研究文档

## 1. 架构概述

SeamlessLoopMobile 是一款专注于无缝循环播放的 Android 音乐播放器。UI 层基于 **Jetpack Compose** 构建，遵循 **MVVM (Model-View-ViewModel)** 架构模式。

### 核心技术栈
- **UI 框架**: Jetpack Compose (Material 3)
- **架构模式**: MVVM
- **组件分工**: MainViewModel 调度 + 专项理财 (Library/Playlist/Selection)
- **状态流**: LiveData (导航/数据库) + StateFlow (高频播放状态)

## 2. 目录结构

```text
app/src/main/java/com/cpu/seamlessloopmobile/ui/
├── components/                 # 可复用全局组件
│   └── MiniPlayer.kt           # 底部常驻微型播放器
└── screen/                     # 页面级容器
    ├── MainScreen.kt           # 应用总入口，管理 Scaffold 与全局 Overlay
    ├── home/                   # 主页模块
    │   └── HomeScreen.kt       # 包含仪表盘、分类入口与歌单列表
    ├── category/               # 分类列表页
    │   └── CategoryScreen.kt   # 文件夹/专辑/歌手的网格与列表
    ├── songlist/               # 歌曲详情列表
    │   └── SongListScreen.kt   # 标准歌曲列表，支持多选与快速播放
    └── playing/                # 核心播放控制
        └── PlayingPanel.kt     # 全屏播放详情页，包含独有的 A-B 循环调节面板
```

## 3. 核心设计特色

### 3.1 顶层 Overlay 渲染逻辑
与传统的单页面跳转不同，播放详情页 (`PlayingPanel`) 被设计为 `MainScreen` 顶层 `Box` 的一个 **Overlay 覆盖层**。
- **优点**: 保证了播放页弹出时能够完美遮盖 `Scaffold` 的 `TopAppBar` 和 `BottomBar`，避免控制权冲突。
- **动画**: 使用 `AnimatedVisibility` 配合 `slideInVertically` 实现从底部滑出的视觉效果。

### 3.2 播放器加载防闪烁机制
针对音频引擎准备时间不确定的情况，引入了 **2秒延迟加载机制**：
- 在 `isPreparing` 状态触发后的前 2000ms 内，UI 按钮会立即锁定以防误触，但不会显示转圈。
- 只有当加载时间超过 2 秒，才会显示 `CircularProgressIndicator`，提升了 UI 的视觉稳定性与高级感。

### 3.3 A-B 循环点编辑隔离与应用
专门针对无缝循环场景设计的编辑逻辑：
- **实时缓存**: 修改循环点时，改动仅存储在 UI 层的临时变量 (`tempLoopStart/End`) 中。
- **防止干扰**: 编辑过程不会打断当前音频流，确保听歌流程无损。
- **应用并试听**: 只有点击该按钮时，才会统一将数据落库并重定位进度条进行 3 秒试听检阅。

## 4. 与行业竞品 (如 APlayer) 的对比研究

| 特性 | SeamlessLoopMobile | APlayer |
| :--- | :--- | :--- |
| **交互模型** | 显隐式 Overlay 覆盖 | 物理锚点拖拽 (AnchoredDraggable) |
| **功能重心** | 采样级 A-B 循环微调 | 歌词渲染与桌面组件扩展 |
| **状态工具** | LiveData & StateFlow 混用 | 纯粹的 Flow + Lifecycle 观测 |
| **返回处理** | BackHandler 拦截层级退出 | 全局统一的导航栈管理 |

## 5. 待优化方向

1. **物理触感升级**: 参考 APlayer，在未来的重构中考虑引入 `anchoredDraggable`，将现在的“显隐式”弹出升级为“跟手式”拖拽。
2. **组件解耦**: 将 `FineTunePage` 中的 UI 组件进一步抽离到 `components` 目录下，实现更高度的代码复用。
3. **对话框中台化**: 建立统一的对话框状态机，规范各功能的弹窗层级关系。

---
*文档生成：莱芙 (Lev Zenith)*
*最后更新：2026-03-07*
