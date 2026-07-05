package com.cpu.seamlessloopmobile.data.sync

/**
 * GitHub 同步配置。
 * 纯数据占位 — 不包含 token 存储或网络实现。
 * 未来可在实际 SyncBackend 实现中使用。
 */
data class GitHubSyncConfig(
    val owner: String,
    val repo: String,
    val branch: String = "main",
    val path: String = "seamless-loop/sync.json"
)
