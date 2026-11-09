package com.mars.overtime.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.data.repository.DEFAULT_HOME_LAYOUT
import com.mars.overtime.data.repository.WidgetPlacement
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes
import com.mars.overtime.ui.theme.AppTitle3Semibold
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 网格跨度中文：1x1 / 2x1 / 1x2 / 2x2 */
private fun spanLabel(p: WidgetPlacement): String = "${p.spanX}×${p.spanY}"

/**
 * 首页小组件配置页：
 * - 概览卡展示模式：总览（时长/薪资/条数）或 分类（净时长 + 分类型工时条）；
 * - 小组件：列出已添加项（含跨度），可删除；新增/调整顺序/缩放在首页长按直接完成。
 */
@Composable
fun HomeWidgetsSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val overviewMode by settingsRepository.homeOverviewMode.collectAsState(initial = "total")
    val layout by settingsRepository.homeLayout.collectAsState(initial = DEFAULT_HOME_LAYOUT)

    fun persist(items: List<WidgetPlacement>) {
        scope.launch { settingsRepository.setHomeLayout(items) }
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
            SettingsGroup(title = "已添加组件") {
                SettingsNote(
                    text = "排序、缩放（1×1 / 2×1 / 1×2 / 2×2）与删除均在首页长按组件直接完成；\n点击下方「添加组件」可预览各种卡片样式后添加。"
                )
                if (layout.isEmpty()) {
                    Text(
                        text = "尚未添加任何组件",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(
                            horizontal = AppDimens.itemHorizontal,
                            vertical = AppDimens.itemVertical
                        )
                    )
                }
                layout.forEach { p ->
                    val def = widgetDef(p.id)
                    BasicComponent(
                        title = def?.title ?: p.id,
                        summary = "${def?.desc ?: ""}　·　大小 ${spanLabel(p)}",
                        endActions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        persist(layout.filter { it.id != p.id })
                                        scope.launch {
                                            snackbarHostState.showCustomToast("已移除「${def?.title ?: p.id}」")
                                        }
                                    }
                                ) {
                                    Icon(
                                        MiuixIcons.Delete,
                                        contentDescription = "移除",
                                        tint = MiuixTheme.colorScheme.error,
                                        modifier = Modifier.size(AppIconSize.xs)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "添加") {
                BasicComponent(
                    title = "添加组件",
                    summary = "预览卡片样式后添加，支持数值与多种图表",
                    startAction = {
                        Box(Modifier.padding(end = AppDimens.spaceSm)) {
                            Icon(
                                MiuixIcons.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(AppIconSize.listIcon)
                            )
                        }
                    },
                    onClick = { navController.navigate("add_widget") }
                )
            }
        }
    }
}
