好，逐函数解释 (๑•̀ㅂ•́)و✧

---

### 构造 & 内部子仓库

```kotlin
class MusicRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val playQueueDao: PlayQueueDao
)
```
构造函数注入三个 DAO，内部用它们实例化三个子仓库。

```kotlin
private val songRepository = SongRepository(songDao)
private val playlistRepository = PlaylistRepository(playlistDao, songDao)
private val musicScannerRepository = MusicScannerRepository(songDao)
```

---

### 歌曲相关转发 → SongRepository

| 函数                                     | 作用                                                         |
| ---------------------------------------- | ------------------------------------------------------------ |
| `getAllSongs()`                          | 挂起函数，返回 DB 中所有歌曲（含 Artist/Album/LoopPoint/UserRating 关联数据） |
| `getAllSongsRaw()`                       | 同上，但返回不组装封面路径等衍生物                           |
| `getAllSongsFlow()`                      | 返回 `Flow<List<Song>>`，DB 变化时自动推送新数据（响应式）   |
| `getAllSongsRawFlow()`                   | 同上，raw 版本                                               |
| `getSongByPath(path)`                    | 按文件路径查单曲                                             |
| `getSongById(id)`                        | 按 ID 查单曲                                                 |
| `updateSong(song)`                       | 更新一首歌的所有字段到 DB                                    |
| `insertOrUpdateSong(song)`               | **指纹匹配 upsert**：按 fileName+duration → totalSamples → filePath 依次匹配，已有则 update，没有则 insert |
| `resolveMediaId(context, song)`          | 通过 MediaStore 获取或生成歌曲的 `mediaId`（用于 MediaBrowserService），回写 DB |
| `updateSongLoopPoints(song, start, end)` | 更新一首歌的 A-B 循环起止点，写 `LoopPoints` 表              |
| `updateSongRating(song, rating)`         | 更新评分，写 `UserRatings` 表                                |

---

### 歌单相关转发 → PlaylistRepository

| 函数                                                | 作用                                                         |
| --------------------------------------------------- | ------------------------------------------------------------ |
| `getAllPlaylists()`                                 | 返回所有播放列表基本信息                                     |
| `getPlaylistsWithCounts()`                          | 返回播放列表 + 各列表内歌曲数量                              |
| `getPlaylistsWithCountsFlow()`                      | 同上，响应式 Flow 版本                                       |
| `getSongsInPlaylist(playlistId)`                    | 查某个播放列表内的所有歌曲                                   |
| `insertPlaylist(playlist)`                          | 新建一个播放列表，返回列表 ID                                |
| `deletePlaylist(playlist)`                          | 删除播放列表及其关联的 PlaylistItem                          |
| `addSongsToPlaylist(playlistId, songIds)`           | 向播放列表添加多首歌，返回实际添加数                         |
| `removeSongsFromPlaylist(playlistId, songIds)`      | 从播放列表移除多首歌                                         |
| `getSongCountInPlaylist(playlistId)`                | 获取播放列表的歌曲总数                                       |
| `syncFolderPlaylist(context, playlist, onProgress)` | **文件夹同步**：清空播放列表 → 扫描文件夹内所有音频文件 → upsert 入库 → 加入列表 → 删除磁盘已不存在的脏数据，`onProgress` 回调进度 |

---

### 扫描与探测转发 → MusicScannerRepository

| 函数                              | 作用                                                         |
| --------------------------------- | ------------------------------------------------------------ |
| `getInitialScannedSongs(context)` | **初次扫描**：遍历 MediaStore 所有音频 → 已有歌更新元数据 → 新歌批量 insert → 写入 LoopPoints/UserRatings |
| `cleanupStaleSongs(context)`      | **清理脏数据**：遍历 DB 所有歌，检查 `filePath` 是否还在磁盘上，不在则删除 |

| 函数                                | 作用                                                         |
| ----------------------------------- | ------------------------------------------------------------ |
| `findAbPair(song, allScannedSongs)` | **普通 AB 对查找**：给定一首歌，在同目录下按文件名约定（如 `_A`/`_B`）找其配对 |
| `findAbPairRobust(context, song)`   | **鲁棒 AB 对查找**：同上但更宽松的匹配逻辑                   |

---

### 播放队列转发 → PlayQueueDao

| 函数                        | 作用                                                         |
| --------------------------- | ------------------------------------------------------------ |
| `getPlayQueueSongs()`       | 获取当前持久化的播放队列（全部歌曲）                         |
| `replacePlayQueue(songIds)` | 替换整个播放队列：`DELETE FROM PlayQueue` → 按顺序 `INSERT` 新的 songIds |