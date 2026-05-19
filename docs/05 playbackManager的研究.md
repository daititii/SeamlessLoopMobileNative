莱芙读完了，逐条评估一下 (｀・ω・´)

| #    | 问题                           | 评估                                                         |
| :--- | :----------------------------- | :----------------------------------------------------------- |
| 1    | `onPlaybackError` 被覆盖       | ⚠️ **部分有理** — 默认实现会设 `_state=ERROR` 并更新媒体会话，外部覆写后会丢失这些行为。但实际 `PlaybackService` 是否覆写了它、覆写时是否补回了状态管理，待确认 |
| 2    | `isSingleLoop` 参数未使用      | ❌ **不准确** — 代码第269行 `isSingleLoop                     |
| 3    | AB 模式缺少 ID 检查            | ⚠️ **部分有理** — 调用方（第201-203行）已经对 B 段做了 `resolveMediaId`，A 段也在前面解析过，所以 `playAbSong` 拿到的 mediaId 大概率有效。但防御性检查总不嫌多 |
| 4    | 错误回调可能死循环             | ❌ **不太可能** — `updateMediaSessionState` 内不会触发 `onPlaybackError`，两者无循环调用链。文档说的场景现实中几乎不会发生 |
| 5    | `release()` 不释放 Native 资源 | 🟡 **视 Native 层实现而定** — `stop()` 调用了 `NativeAudio.stopAudioEngine()`，如果 C++ 层正确关闭引擎、释放 FD，就没问题。需要看 `native-lib.cpp` 确认 |

**总结：** 文档对问题 1 和 3 的直觉有道理，对 2 和 4 的分析有偏差，问题 5 需要查 Native 层才能下结论 (´･ω･`)

莱芙继续往下看了~ 数据库操作方面有几个值得注意的点：

**`actuallyPlaySong` 里的 DB 操作：**

1. **第220-234行：播放前查DB刷新数据** — ✅ 好习惯，防止用 stale 对象
2. **第301-302行：无条件 `updateSong`** — 条件看起来是"首次播放的新歌"才写，但如果 `totalSamples <= 0` 或循环点为0，每次播放都会写一次。实际上这些字段（`duration`, `totalSamples`, `loopEnd`）由 Native 层返回，多次写入不一定幂等 (･_･;

**`playAbSong` 里的 DB 操作：** 3. **完全没有 DB 操作** — 不像 `actuallyPlaySong` 会先查DB刷新数据。如果 AB 配对歌曲的元数据在内存加载后被修改，AB 模式感知不到

**通用问题：** 4. **`playSong` 里调了两次 `resolveMediaId`**（第192-194行解析A，第201-203行解析B），然后 `actuallyPlaySong` 又查一次DB（第220-234行），部分重复工作 5. **无事务包裹** — `getSongByPath` → `updateSong` 的读-写序列没有事务保护，极端情况下 crash 会导致部分写入

**总结：** 最大的缺口是 AB 模式完全不走 DB 刷新路径，以及 `updateSong` 写入时机可能不够精确 (´･ω･`) 需要莱芙补上这些吗？