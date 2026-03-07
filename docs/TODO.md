# SeamlessLoopMobile TODO

> 手机端db 文件：此电脑\cpurising\内部存储\Android\data\com.cpu.seamlessloopmobile\files\databases

---

## ✅ 已解决

- [X] 逻辑设置照搬原来电脑端
- [X] 进度条需要在采样点调整界面出现
- [X] 现在更改循环点后不能循环播放，在末尾直接停止，退出后再重新进入，没有按照原有的循环点循环播放
- [X] 高亮当前播放的歌曲
- [X] 插入耳机后停止播放，进度条不动但是暂停/播放键显示播放
- [X] 默认循环起始是歌曲起始
- [X] 接缝处有明显的差别（尤其BA的电子音乐）
- [X] 微调时会影响播放进度
- [X] 微调部分：试着模仿电脑端，至少有试听键
- [X] 接缝不准确，会不会是音乐格式的原因？ogg wav可以 MP3不行
- [X] 添加多首歌曲后，歌单显示为0首歌曲，打开后没有歌曲显示
- [X] AB试听出了问题，不能跳转到前3秒
- [X] 每次导入都要那么久吗
- [X] 歌单退回上级是本地音乐
- [X] 需要双指纹，R8,R9识别出问题，新的流星世界ab识别出问题
- [X] 相同文件夹同步两次-》刷新文件夹**唯一的隐患只剩下我们上一轮讨论的：** 在主页"看所有的本地音乐雷达图（MusicScannerRepository的快速扫描）"时，如果有多首同名，在合并写入数据库的那一瞬间，互相覆盖了。
- [X] 只要我们在那个快速扫描合并的地方，像您说的采用**"中策"**（在遇到重名嫌疑时偷偷算一下采样数），或者直接加上**时长
- [X] filename的总采样数与电脑端相同吗？
- [X] 更新了初始扫描时的匹配逻辑。现在手机扫出来的歌曲（此时又是什么时候获取的，准确吗）会拿着"名字+时长"去数据库里寻找对应的循环点，确保不会把 A 的循环点错扣在 B 的头上
- [X] 切换音乐时不能切换循环点界面 - 已修复，循环点界面现在可以正确切换
- [X] 需要统一对艺术家、专辑的处理。不然两端无法通用。主要是电脑端的处理 - 已通过PcDatabaseImporter实现基础同步，但专辑/艺术家分类需要扩展
- [X] 手机端不能正确识别专辑，无法正确填入相应位置 - 已修复，专辑信息可以从媒体库正确识别
- [X] 点击歌曲不应该打开详情页面 - 已修复，点击行为已调整为直接播放
- [X] 对专辑的识别需要加上 - 已实现，通过AudioScanner扫描媒体库的专辑信息
- [X] 添加歌曲到歌单后就会跳转到主页面（歌单页面） - 已修复，导航逻辑已调整
- [X] 不能识别BLUE ARCHIVE - 已修复，AB对检测逻辑已增强
- [X] D:\seamless loop music\SeamlessLoopMobile\app\src\main\java\com\cpu\seamlessloopmobile\viewmodel\MainViewModel.kt太大了 - 已重构，现为311行（原>1000行）
- [X] D:\seamless loop music\SeamlessLoopMobile\app\src\main\java\com\cpu\seamlessloopmobile\MainActivity.kt太大了 - 已重构，现为133行（原>500行）

### **惊人发现：手机端的"44100 诅咒"**

莱芙刚才在翻看手机端

![img](vscode-file://vscode-app/d:/program/Antigravity/resources/app/extensions/theme-symbols/src/icons/files/kotlin.svg)

PlaybackManager.kt（第 194 行）时，直接惊呆了：

- **手机端居然在硬编码！** `duration = durationFrames * 1000 / 44100`
- **这意味着**：在手机端，如果大人播放一首 48000Hz 的歌，它的总时长显示、进度条位置，**全都是错的**！它会比实际时间慢大约 8.8%（刚好是 48000/44100 的比例）。

**✅ 现已修复**：PlaybackManager.kt第225行已改为动态获取采样率：`val actualSampleRate = NativeAudio.getSampleRate().let { if (it > 0) it else 44100 }`

---

## ❌ 待解决

### 功能实现相关

- [ ] 开头似乎不能清楚的
- [ ] 刚开始打开歌曲，无法听到前几个毫秒的声音
- [x] 还要注意应用会阻止录音，说是录音设备，会不会是USB调试的原因？
- [x] 专辑/艺术家封面及分类 - 数据库同步已实现，但UI展示和分类功能待完善
- [ ] 蓝牙/有线耳机按键控制 - MediaControlManager已实现媒体按钮，但耳机物理按键支持需要进一步测试
- [x] 批量全选 - SelectionViewModel.selectAll()已实现，但UI集成需要完善
- [ ] 支持更多音乐格式 - AudioDecoder通过Android MediaExtractor支持MP3/WAV/OGG/AAC等格式，但某些边缘格式可能不支持
- [x] AB式 - 已通过MusicScannerRepository.findAbPair实现AB对检测，但AB播放界面可能不完整
- [x] 后台播放，有时就退出。熄屏后台拔耳机必定死机 - HeadsetPlugReceiver已实现，但稳定性待验证
- [x] 试图添加通知列表和锁屏按钮。结果失败了。 - NotifyImpl已实现通知，锁屏按钮可见性已设置，但可能不完整
- [x] 修改对电脑端db文件的适配，增加专辑，艺术家分类 - PcDatabaseImporter仅同步循环点，需要扩展以处理专辑/艺术家信息 - 我想应该不需要 
- [x] 是否可以支持多个文件夹导入，或是怎么处理的 - 文件夹扫描已支持，但批量导入逻辑待优化
- [x] 本地歌曲的数量如何计算的 - 已通过MusicScannerRepository实现，但计数逻辑可能需要优化
- [ ] 又开始不能列表播放与随机播放（只能单曲循环），即使有歌曲循环点在末尾，歌曲播放到此处，就会快速向下滑动越过大量歌曲，然后停在某首歌曲 - PlayMode枚举已定义LIST_LOOP/SINGLE_LOOP/SHUFFLE，但播放队列逻辑可能有bug
- [ ] 手机端也要支持排行榜界面 - 完全未实现
- [x] 手机端的同步文件夹要注意 - 备注项，无具体实现
- [x] 日文乱码的确大概率不能正确识别（R3），R10是例外 - 编码问题未解决
- [ ] 可以考虑增加图标了 - 未添加新图标资源
- [ ] 默认收藏的歌单可以更改 - 未实现相关逻辑
- [x] 最新采用compose架构后，一系列多选功能消失。可以多选歌曲，但不能全选，选择后不能添加到歌单，不能创建歌单 - SelectionViewModel已实现多选，但UI集成不完整
- [x] 循环编辑页面进度条拖动不流畅 - UI优化待进行
- [x] 对进一步的匹配什么时候发生？循环点匹配什么时候有 - MusicScannerRepository已包含初始扫描和AB匹配，但匹配时机和触发条件可能不明确
- [ ] 不能拖动进度条 - 可能存在触摸事件处理问题
- [ ] 增加手动同步文件夹功能 - 当前仅支持自动扫描，无手动触发点
- [ ] 支持显示别名
- [ ] 尝试增加以下功能
  ![Screenshot_20260304_000223_remix.myplayer](TODO.assets/Screenshot_20260304_000223_remix.myplayer.jpg) - 未知具体功能需求

### 备注与学习项

- [ ] 该好好了解手机端开发知识了。 - 学习备注
- [ ] 按照豆包要求修复架构 - 已部分重构（MainViewModel/MainActivity），但整体架构可能仍需优化
