package com.overtime.miuix.ui.snackbar

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 全局共享的 [SnackbarHostState]，由 [com.overtime.miuix.MainNavHost] 提供。
 * 各页面 Scaffold 通过 [top.yukonga.miuix.kmp.basic.SnackbarHost] 挂载后，
 * 任意位置均可调用 [SnackbarHostState.showCustomToast] 弹出 2 秒自定义提示。
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided. Wrap content in a Scaffold with snackbarHost.")
}

/**
 * 弹出自定义提示，2 秒后自动消失（Custom 2s 提示）。
 *
 * 在组合阶段通过 [LocalSnackbarHostState] 取得 state 引用并保存，随后在
 * `rememberCoroutineScope()` 的 `scope.launch { state.showCustomToast(msg) }` 中调用即可。
 */
suspend fun SnackbarHostState.showCustomToast(message: String) {
    showSnackbar(message, duration = SnackbarDuration.Custom(2000))
}
