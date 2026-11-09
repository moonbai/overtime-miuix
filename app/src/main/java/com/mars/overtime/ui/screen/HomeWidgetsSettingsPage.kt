package com.mars.overtime.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.data.repository.SettingsRepository.Companion.DEFAULT_HOME_WIDGETS
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 小组件元信息：id + 标题 + 说明（与 SettingsRepository.HomeWidgetIds 保持一致） */
private data class WidgetMeta(val id: String, val title: String, val desc: String)

private val WIDGET_POOL = listOf(
    WidgetMeta("month_hours", "当月工时", "本月净时长，附与上月的增减对比"),
    WidgetMeta("month_salary", "本月薪资", "本月预估薪资（含请假扣款）"),
    WidgetMeta("last_month_hours", "上月工时", "上月净时长与记录条数"),
    WidgetMeta("year_total", "年度累计", "本年净时长与预估薪资"),
    WidgetMeta("month_leave", "当月请假", "本月请假次数与扣款金额"),
    WidgetMeta("latest_record", "最近记录", "最近一条记录的类型、日期与时长")
)

private fun widgetTitle(id: String): String = WIDGET_POOL.firstOrNull { it.id == id }?.title ?: id

/**
 * 首页小组件配置页：
 * - 概览卡展示模式：总览（时长/薪资/条数）或 分类（净时长 + 分类型工时条）；
 * - 小组件：开关启用，启用项可上移 / 下移调整首页展示顺序。
 */
@Composable
fun HomeWidgetsSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val overviewMode by settingsRepository.homeOverviewMode.collectAsState(initial = "total")
    val enabledWidgets by settingsRepository.homeWidgets.collectAsState(initial = DEFAULT_HOME_WIDGETS)

    // 展示顺序：已启用（按配置顺序）在前，未启用按组件池顺序跟在后面
    val displayList = remember(enabledWidgets) {
        enabledWidgets + WIDGET_POOL.map { it.id }.filter { it !in enabledWidgets }
    }

    fun persist(ids: List<String>) {
        scope.launch { settingsRepository.setHomeWidgets(ids) }
    }

    SettingsScreen(
        title = "首页小组件",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    ) {
        item {
            SettingsGroup(title = "概览卡展示") {
                listOf(
                    "total" to Pair("总览", "总时长 · 预估薪资 · 记录数"),
                    "by_type" to Pair("分类", "净时长与薪资，附分类型工时条")
                ).forEach { (mode, meta) ->
                    BasicComponent(
                        title = meta.first,
                        summary = meta.second,
                        endActions = if (overviewMode == mode) {
                            { Icon(MiuixIcons.Ok, contentDescription = "已选择", tint = MiuixTheme.colorScheme.primary) }
                        } else null,
                        onClick = {
                            scope.launch {
                                settingsRepository.setHomeOverviewMode(mode)
                            }
                        }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "小组件") {
                SettingsNote(
                    text = "开启后显示在首页，顺序即首页排布顺序；\n点击首页概览卡右上角「编辑」也可直达本页。"
                )
                displayList.forEachIndexed { index, id ->
                    val enabled = id in enabledWidgets
                    val pos = enabledWidgets.indexOf(id)
                    BasicComponent(
                        title = widgetTitle(id),
                        summary = WIDGET_POOL.firstOrNull { it.id == id }?.desc,
                        endActions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (enabled) {
                                    IconButton(
                                        onClick = {
                                            if (pos > 0) {
                                                persist(enabledWidgets.toMutableList().apply { add(pos, removeAt(pos - 1)) })
                                            }
                                        },
                                        enabled = pos > 0
                                    ) {
                                        Icon(
                                            MiuixIcons.ChevronForward,
                                            contentDescription = "上移",
                                            tint = if (pos > 0) MiuixTheme.colorScheme.onSurface
                                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .rotate(-90f)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (pos < enabledWidgets.size - 1) {
                                                persist(enabledWidgets.toMutableList().apply { add(pos, removeAt(pos + 1)) })
                                            }
                                        },
                                        enabled = pos < enabledWidgets.size - 1
                                    ) {
                                        Icon(
                                            MiuixIcons.ChevronForward,
                                            contentDescription = "下移",
                                            tint = if (pos < enabledWidgets.size - 1) MiuixTheme.colorScheme.onSurface
                                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .rotate(90f)
                                        )
                                    }
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { on ->
                                        persist(
                                            if (on) enabledWidgets + id
                                            else enabledWidgets.filter { it != id }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
