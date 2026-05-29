package com.cpu.seamlessloopmobile.ui.state

/**
 * 统一的数据加载状态机，为慢速异步逻辑提供完美反馈喵！(๑•̀ㅂ•́)و✧
 */
sealed class DataUiState<out T> {
    object Loading : DataUiState<Nothing>()
    data class Error(val message: String) : DataUiState<Nothing>()
    data class Success<T>(val data: T) : DataUiState<T>()
}
