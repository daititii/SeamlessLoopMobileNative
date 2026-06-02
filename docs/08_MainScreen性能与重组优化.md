# 更改计划

## 需求描述
对 `MainScreen` 与 `MainViewModel` 进行深度大重构（界面与导航完全解耦，降低重组负担）：
1. 采纳 CPU 大人的英明指示，将现有的 `MainViewModel` 进一步拆分，在各个特定的子界面直接使用对应的子 ViewModel（如 `PlaylistViewModel`、`LibraryViewModel`、`SelectionViewModel`），彻底消除 `MainViewModel` 的繁重代理属性，实现极致的解耦与局部重组。
2. 将一级 Tab 页（Page 0，即“歌单” Tab 页面）彻底抽象并封装为独立的 Composable 页面 `PlaylistTabScreen`，与其直接传入对应的子 ViewModel，实现物理隔离。
3. 彻底理清主页面与二级列表页（`SongListScreen`、`SearchScreen`）的状态托管边界，消除在 `MainScreen` 顶层使用 `resolveSongsForState` 进行动态解析的逻辑，让各个子 Screen 配合子 ViewModel 自行进行状态自治。
4. 规范各级组件与导航的架构边界，让 `MainScreen` 退化为单纯的“导航骨架与视图调度室”。

## 需要了解的信息
- 当前项目的相关文件：
  - [MainScreen.kt](file:///d:/seamless%20loop%20music/SeamlessLoopMobile/app/src/main/java/com/cpu/seamlessloopmobile/ui/screen/MainScreen.kt)（重构核心，需削减其状态收集并剥离 PlaylistTab）
  - [MainViewModel.kt](file:///d:/seamless%20loop%20music/SeamlessLoopMobile/app/src/main/java/com/cpu/seamlessloopmobile/viewmodel/MainViewModel.kt)（核心 Coordinator VM，需剥离冗余的代理只读 LiveData/Flow 属性）
  - [SongListScreen.kt](file:///d:/seamless%20loop%20music/SeamlessLoopMobile/app/src/main/java/com/cpu/seamlessloopmobile/ui/screen/songlist/SongListScreen.kt)（需增加状态自治性，直接获取其展示的歌曲列表数据）
  - [SearchScreen.kt](file:///d:/seamless%20loop%20music/SeamlessLoopMobile/app/src/main/java/com/cpu/seamlessloopmobile/ui/screen/search/SearchScreen.kt)（确保其内部完全隔离，不受 `MainScreen` 干扰）
  - [CategoryScreen.kt](file:///d:/seamless%20loop%20music/SeamlessLoopMobile/app/src/main/java/com/cpu/seamlessloopmobile/ui/screen/category/CategoryScreen.kt)（分类 Tab 页组件）

## 当前项目状态
- 相关功能的现有代码情况：
  - 目前 `MainScreen.kt` 的 Pager 中 Page 0 直接内嵌了歌单 Tab 页面的 LazyColumn 和所有的 CategoryListItem，导致一级的 Tab 部分代码量较大，且所有的 playlist 状态都污染了最顶层。
  - `MainViewModel.kt` 里写了将近 15 个代理属性来将各子 ViewModel 的 Flow/LiveData 中转暴露出来。这导致任何子 VM 里的状态稍微一动，主屏的 `MainScreen` 就会被迫整体重组。
  - 二级列表 `SongListScreen` 处于木偶（Stateless）状态，展示的歌曲 `songsToShow` 需要依靠顶层的 `resolveSongsForState` 进行动态全量解析并由参数向下传导。这种模糊的边界导致列表在滑动或子状态变化时，整个大组合树频繁重组。
  - 主屏中充斥着对 bottomBar (MiniPlayer)、SettingsDrawer、CentralizedDialogHost 的全局状态暴露，高频轮询的状态与交互动画交织在一起，放大了卡顿感。

## 可选实现方案
- **方案A：全面的 Lambda 缓存化 + 核心状态局部下沉（轻度优化）**
  - *说明*：仅做参数 Lambda 缓存与微观状态下沉，不解耦页面。
  - *状态*：未选择。

- **方案B：深度大重构 —— 界面与导航完全解耦，拆分 ViewModel，彻底分离一级 Tab 与二级页面状态（已选择）**
  - *说明*：
    1. 新建独立的 `PlaylistTabScreen` 承载 Page 0 的全部逻辑，传入 `PlaylistViewModel`、`LibraryViewModel` 和 `SelectionViewModel` 自治监听。
    2. 将二级列表页的歌曲数据生成逻辑下沉。`SongListScreen` 将直接根据当前的 `MusicUiState` 局部监听和重组，不再经过 `MainScreen` 的中转计算，消灭 `MainViewModel` 的代理属性。
    3. `MainScreen` 仅保留 `uiState`（导航）、Pager 控制器以及全局 Overlay 的显示开关状态（如 `isPlayingPanelVisible`, `isSettingsPanelVisible`），其他数据级状态全部下沉。
  - *优点*：彻底根治大组合树造成的渲染灾难，子界面拥有极高的局部重组自治性，架构健壮且极易扩展。
  - *缺点*：重构范围较大，需要对状态分发流与 Composable 参数传递进行精细的解耦和调试。

## 实现计划（方案B 详细步骤）

### 第一阶段：新建与剥离 PlaylistTabScreen（物理隔离）
1. **新建 Composable 文件**：
   - 创建新文件 [PlaylistTabScreen.kt](file:///d:/seamless%20loop%20music/SeamlessLoopMobile/app/src/main/java/com/cpu/seamlessloopmobile/ui/screen/PlaylistTabScreen.kt)。
2. **移植 Page 0 逻辑**：
   - 将原 `MainScreen.kt` 里的“歌单” Tab 渲染逻辑完全打包移植至 `PlaylistTabScreen`。
   - 接口设计为接收 `PlaylistViewModel`、`LibraryViewModel` 和 `SelectionViewModel`，在内部自行监听 `playlistsWithCounts`、`allSongs` 等状态，彻底将重组边界隔绝。

### 第二阶段：重塑列表页状态托管边界与 ViewModel 瘦身
3. **下沉歌曲数据解析**：
   - 消除 `MainScreen.kt` 顶层的全局状态收集（如 `allSongs`、`folders`、`albums`、`artists`、`favorites` 等仅为子页或列表页服务的状态）。
   - 修改 `SongListScreen`。使其作为智能包装器，在内部通过对应的子 ViewModel 直接局部收集其渲染歌曲。
4. **清理 MainViewModel 中的中转代理**：
   - 逐步从 `MainViewModel.kt` 中剔除类似于 `allSongs`、`folders`、`playlists` 等用于数据透传代理的属性，将 ViewModel 瘦身为纯粹的“协调器与播放驱动”。

### 第三阶段：精简 MainScreen 骨架与 Lambda 缓存
5. **MainScreen “瘦身”**：
   - 清理 `MainScreen.kt` 顶层的数据监听，只保留 `uiState`（用来驱动 Tab 渲染与 Overlay 路由）以及弹窗/抽屉显示状态。
   - `Scaffold` 内的 Pager 只负责组装 `PlaylistTabScreen` 和 `CategoryScreen`。
6. **Lambda 稳定性治理**：
   - 用 `remember(viewModel)` 和方法引用（如 `viewModel::clearSelection`）将所有顶栏、底部 MiniPlayer 的回调 Lambda 缓存化，防止子组件的 Skip 机制失效。

### 第四阶段：验证与调优
7. **编译验证**：
   - 运行 `gradlew.bat assembleDebug` 确保代码无缝编译。
8. **流畅度测试**：
   - 滑动一二级 Tab，进行歌曲多选，验证重构后的重组范围与帧率。

## 待确认问题
- 在 `PlaylistTabScreen` 和 `SongListScreen` 解耦后，为了确保子界面能直接从 `MainViewModel` 获取数据，是否建议通过参数直接传递 `viewModel`？（已确认：按照 CPU 大人的决策，直接传递对应的子 ViewModel 如 `PlaylistViewModel` 等进行深度隔离与解耦。）

## 预期结果
- `MainScreen` 代码行数大幅缩减，架构退化为纯粹的“骨架容器”。
- `MainViewModel` 的高负荷代理逻辑彻底卸载，数据流向无比清晰。
- “歌单 Tab” 拥有独立的局部重组边界，滑动和加载歌曲时完全独立。
- 二级列表 `SongListScreen` 自治化，当后台 JNI 读取或数据库更新时，只针对该子页面内部或 MiniPlayer 进行小范围刷新，完全杜绝整屏闪烁或掉帧，UI 交互流畅度得到成倍提升。
