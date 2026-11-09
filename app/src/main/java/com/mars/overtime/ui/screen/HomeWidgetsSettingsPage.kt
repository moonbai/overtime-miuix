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

/** 固定尺寸标签：1×1 / 2×1 / 2×2 */
private fun spanLabel(p: WidgetPlacement): String = "${p.spanX}×${p.spanY}"

/**
 * 首页小组件配置页：
 * - 概览卡展示模式：总览（时长/薪资/条数）或 分类（净时长 + 分类型工时条）；
 * - 小组件：列出已添加项（含固定尺寸），可删除；新增在添加页、排序在首页长按完成。
 */
@Composable
fun HomeWidgetsSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val overviewMode by settingsRepository.homeOverviewMode.collectAsState(initial = "total")
    val layoutRaw by settingsRepository.homeLayout.collectAsState(initial = DEFAULT_HOME_LAYOUT)
    // 尺寸由目录固定，读取时统一校正旧版本残留的自定义跨度
    val layout = remember(layoutRaw) { normalizeLayout(layoutRaw) }

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
                    text = "共 ${WIDGET_CATALOG.size} 种组件，分为本月概览 / 类型分项 / 趋势图表 / 大图分析四类。" +
                        "\n组件尺寸按内容固定，无需调整；排序与删除在首页长按组件直接完成。"
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
                        summary = "${def?.category?.label ?: "组件"}　·　${def?.desc ?: ""}　·　固定尺寸 ${spanLabel(p)}",
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
                    summary = "仅显示未添加组件，按分类筛选并预览后添加",
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
