# 技术参考与调研报告

## 1. 调研项目 A: MusicPlayer-master
### 借鉴点:
- **UI 结构**: 采用 Activity + Fragment + ViewPager 的经典布局。
- **扫描逻辑**: 使用 `ContentResolver` 配合 `StorIO` 实现响应式扫描，自动过滤微信语音等杂讯。
- **权限管理**: 提供了针对安卓不同版本的权限请求模板。

### 避坑点 (Critical):
- **A-B 循环实现**: 该项目在 Java 层轮询进度并调用 `seekTo`，存在数百毫秒的延迟与断音。证明了 Java 原生 MediaPlayer 无法实现采样级无缝循环。

## 2. 调研项目 B: APlayer-2.0.6.0
### 借鉴点:
- **现代 UI**: 全面使用 Jetpack Compose，界面极其精致滑。
- **功能扩展**: 支持 WebDAV/SMB 多源播放及逐字歌词展示。
- **性能**: 使用 ExoPlayer，比基础 MediaPlayer 兼容性更好。

### 总结:
虽然竞品在 UI 和网络功能上非常完善，但其音频核心依然受限于通用 API。咱们的优势在于**垂直领域的专业性**——针对循环音乐进行底层音频栈优化。

## 3. 核心工具包整理
- **权限库**: [XXPermissions](https://github.com/getActivity/XXPermissions)
- **音频库**: [Oboe](https://github.com/google/oboe)
- **数据库**: Room (用于本地缓存与循环点记录)
