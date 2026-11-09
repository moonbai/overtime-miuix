package com.mars.overtime.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.util.AppDefaults
import com.mars.overtime.data.repository.DEFAULT_HOME_LAYOUT
import com.mars.overtime.data.repository.WidgetPlacement
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 添加小组件页：仅展示**尚未添加**的组件，以当前真实数据实时预览卡片样式。
 * 组件尺寸由 [WIDGET_CATALOG] 按内容固定（不可调整）；
 * 已添加组件的排序与移除在首页编辑态 /「首页小组件」设置页完成。
 */
@Composable
fun AddWidgetPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val records by repository.getAllRecords().collectAsState(initial = emptyList())
    val layoutRaw by settingsRepository.homeLayout.collectAsState(initial = DEFAULT_HOME_LAYOUT)
    // 旧版本残留的自定义跨度统一归位到目录固定尺寸
    val layout = remember(layoutRaw) { normalizeLayout(layoutRaw) }
    // DataStore 回写是异步的：连点多个「添加」时，若每次都基于旧 layout 写入，
    // 后一次会覆盖前一次。这里先本地累积待添加项，布局 Flow 回来后清空。
    val pending = remember { mutableStateListOf<WidgetPlacement>() }
    LaunchedEffect(layout) { pending.clear() }
    // 已添加项（含本次待写入项）不再出现在添加页
    val available = WIDGET_CATALOG.filter { def ->
        layout.none { it.id == def.id } && pending.none { it.id == def.id }
    }
    val currentMonth = remember { SalaryCalculator.getCurrentYearMonth() }
    val agg = remember(records, currentMonth) { aggregateHome(records, currentMonth) }

    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = AppDefaults.PRIMARY_ARGB)
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = AppDefaults.WEEKEND_ARGB)
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = AppDefaults.HOLIDAY_ARGB)
    val typeColor: (OvertimeType) -> Color = { type ->
        when (type) {
            OvertimeType.WORKDAY -> Color(typeColorWorkday)
            OvertimeType.WEEKEND -> Color(typeColorWeekend)
            OvertimeType.HOLIDAY -> Color(typeColorHoliday)
        }
    }

    fun persist(items: List<WidgetPlacement>) {
        scope.launch { settingsRepository.setHomeLayout(items) }
    }

    SettingsScreen(
        title = "添加组件",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    ) {
        item {
            SettingsNote("仅展示尚未添加的组件，预览为当前数据实时渲染；组件尺寸按内容自动匹配，无需调整。已添加组件的排序与移除在首页长按编辑或「首页小组件」中完成。")
        }
        if (available.isEmpty()) {
            item {
                SettingsGroup {
                    BasicComponent(
                        title = "全部组件已添加",
                        summary = "返回「首页小组件」可查看、排序与移除已添加组件",
                        onClick = { navController.popBackStack() }
                    )
                }
            }
        }
        items(items = available, key = { it.id }) { def ->
            SettingsGroup {
                BasicComponent(
                    title = def.title,
                    summary = def.desc,
                    endActions = {
                        Text(
                            text = "${def.fixedSpanX}×${def.fixedSpanY}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    },
                    onClick = {}
                )
                // 实时预览：按该组件的固定尺寸等比渲染
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.spaceXs),
                    contentAlignment = Alignment.Center
                ) {
                    // 按首页真实网格宽度计算：首页内容区 = 屏幕宽 - 两侧页面留白。
                    // 设置页分组卡片可能略窄，用 coerceAtMost 兜底，而不是缩放卡片内文字。
                    val homeGridWidth = LocalConfiguration.current.screenWidthDp.dp -
                        AppDimens.pageHorizontal * 2
                    val cellW = (homeGridWidth - AppDimens.groupSpacing) / GRID_COLUMNS
                    val w = (cellW * def.fixedSpanX + AppDimens.groupSpacing * (def.fixedSpanX - 1))
                        .coerceAtMost(maxWidth)
                    val h = CELL_HEIGHT * def.fixedSpanY + AppDimens.groupSpacing * (def.fixedSpanY - 1)
                    Box(
                        Modifier
                            .width(w)
                            .height(h)
                    ) {
                        PreviewWidgetCard(
                            id = def.id,
                            agg = agg,
                            month = currentMonth,
                            typeColor = typeColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // 只提供「添加」：已添加项不在此页展示，移除统一在首页 / 设置页
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.itemHorizontal, vertical = AppDimens.spaceXs)
                ) {
                    Button(
                        onClick = {
                            pending.add(WidgetPlacement(def.id, def.fixedSpanX, def.fixedSpanY))
                            persist(layout + pending.toList())
                            scope.launch { snackbarHostState.showCustomToast("已添加「${def.title}」") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            MiuixIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(AppIconSize.xs)
                        )
                        Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
                        Text("添加")
                    }
                }
            }
        }
    }
}
