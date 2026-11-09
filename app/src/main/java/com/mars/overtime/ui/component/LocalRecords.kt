package com.mars.overtime.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.repository.OvertimeRepository

/**
 * 记录列表共享快照。
 *
 * 首页 / 记录 / 统计 / 快速提报四个常驻界面原本各自订阅同一个 Room 全表 Flow，
 * 每次写入都会触发四次查询与四份列表拷贝。这里由 MainScreen 订阅一次并通过
 * [LocalRecords] 下发；独立入口（统计详情页、备份页、添加组件页等）没有 Provider，
 * [rememberAppRecords] 自动退化为自行订阅，行为不变。
 *
 * 初始值用 null 区分「尚未加载」与「确无记录」，避免首帧误判空态。
 */
val LocalRecords = compositionLocalOf<State<List<OvertimeRecord>?>?> { null }

@Composable
fun rememberAppRecords(repository: OvertimeRepository): State<List<OvertimeRecord>?> {
    val shared = LocalRecords.current
    if (shared != null) return shared
    return repository.getAllRecords().collectAsState(initial = null)
}
