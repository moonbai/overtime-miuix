package com.mars.overtime.ui.snackbar

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 全局共享的 [SnackbarHostState]，由 [com.mars.overtime.MainNavHost] 提供。
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

/** 删除撤销提示的消息前缀，用于精确识别本类提示，避免误伤其他 Toast。 */
const val DELETE_SNACKBAR_PREFIX = "已删除"

/** 「正在检查更新」的完整文案：检查结束时据此精确收起，避免与结果提示叠加。 */
const val CHECKING_SNACKBAR_MESSAGE = "正在检查更新…"

/** 收起尚未消失的「正在检查更新」提示；遇到其他消息立即停止。 */
suspend fun SnackbarHostState.dismissCheckingSnackbar() {
    repeat(4) {
        val data = newestSnackbarData() ?: return
        if (data.visuals.message != CHECKING_SNACKBAR_MESSAGE) return
        data.dismiss()
    }
}

/**
 * 收起队列中尚未消失的「删除撤销」提示，只保留最新一条。
 *
 * MIUIX 0.9.4 的 [SnackbarHostState] 是多条目队列：每次 showSnackbar 都会
 * `entries.add(0, entry)`，SnackbarHost 再用 LazyColumn 渲染全部可见条目；
 * 且 showSnackbar 内部没有 finally 清理，协程被取消时条目仍会留到自身超时，
 * 因此单纯 cancel 协程不足以消除堆叠，必须显式 [top.yukonga.miuix.kmp.basic.SnackbarData.dismiss]。
 *
 * [newestSnackbarData] 返回队列中最新的可见条目，循环 dismiss 即可逐条收起；
 * 遇到非删除类提示立即停止，不影响其他页面的消息。上限防止异常情况下死循环。
 */
suspend fun SnackbarHostState.dismissDeleteSnackbars() {
    repeat(16) {
        val data = newestSnackbarData() ?: return
        if (!data.visuals.message.startsWith(DELETE_SNACKBAR_PREFIX)) return
        data.dismiss()
    }
}
