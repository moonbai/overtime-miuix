package com.mars.overtime.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.data.repository.DEFAULT_HOME_LAYOUT
import com.mars.overtime.data.repository.WidgetPlacement
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 添加小组件页：以当前真实数据**实时预览**每种卡片样式。
 * 组件尺寸由 [WIDGET_CATALOG] 按内容固定（不可调整），
 * 已添加的组件可在此移除，排序在首页长按拖动完成。
 *
 * 组件扩到近 30 个后按 [WidgetCategory] 分组 + 顶部筛选条，
 * 避免一长串无差别列表。
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
    val currentMonth = remember { SalaryCalculator.getCurrentYearMonth() }
    val agg = remember(records, currentMonth) { aggregateHome(records, currentMonth) }

    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())
    val typeColor: (OvertimeType) -> Color = { type ->
        when (type) {
            OvertimeType.WORKDAY -> Color(typeColorWorkday)
            OvertimeType.WEEKEND -> Color(typeColorWeekend)
            OvertimeType.HOLIDAY -> Color(typeColorHoliday)
        }
    }

    var filter by remember { mutableStateOf<WidgetCategory?>(null) }
    val shown = remember(filter) {
        if (filter == null) WIDGET_CATALOG else WIDGET_CATALOG.filter { it.category == filter }
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
            SettingsNote("预览为当前数据实时渲染；组件尺寸按内容自动匹配，无需调整。排序在首页长按组件拖动完成。")
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = AppDimens.spaceXs),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("全部 ${WIDGET_CATALOG.size}", filter == null) { filter = null }
                WidgetCategory.values().forEach { cat ->
                    val count = WIDGET_CATALOG.count { it.category == cat }
                    FilterChip("${cat.label} $count", filter == cat) { filter = cat }
                }
            }
        }

        WidgetCategory.values().forEach { cat ->
            val group = shown.filter { it.category == cat }
            if (group.isEmpty()) return@forEach
            item(key = "header_${cat.name}") {
                SmallTitle(text = cat.label)
            }
            items(items = group, key = { it.id }) { def ->
                val present = layout.any { it.id == def.id }
                SettingsGroup(title = def.title) {
                    BasicComponent(
                        title = def.desc,
                        summary = (if (def.isChart) "图表组件" else "数值组件") +
                            "　·　尺寸 ${def.fixedSpanX}×${def.fixedSpanY}（固定）",
                        onClick = {}
                    )
                    // 实时预览：按该组件的固定尺寸等比渲染，预览即所见
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppDimens.spaceXs),
                        contentAlignment = Alignment.Center
                    ) {
                        val cellW = (maxWidth - AppDimens.groupSpacing) / GRID_COLUMNS
                        val w = cellW * def.fixedSpanX + AppDimens.groupSpacing * (def.fixedSpanX - 1)
                        val h = CELL_HEIGHT * def.fixedSpanY + AppDimens.groupSpacing * (def.fixedSpanY - 1)
                        Box(Modifier.width(w).height(h)) {
                            PreviewWidgetCard(
                                id = def.id,
                                agg = agg,
                                month = currentMonth,
                                typeColor = typeColor,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.itemHorizontal, vertical = AppDimens.spaceXs)
                    ) {
                        Button(
                            onClick = {
                                if (present) {
                                    persist(layout.filter { it.id != def.id })
                                    scope.launch { snackbarHostState.showCustomToast("已移除「${def.title}」") }
                                } else {
                                    persist(
                                        layout + WidgetPlacement(
                                            def.id,
                                            def.fixedSpanX,
                                            def.fixedSpanY
                                        )
                                    )
                                    scope.launch { snackbarHostState.showCustomToast("已添加「${def.title}」") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (present) MiuixIcons.Delete else MiuixIcons.Add,
                                contentDescription = null,
                                modifier = Modifier.size(AppIconSize.xs)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (present) "移除" else "添加")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.secondaryContainer,
        contentColor = if (selected) MiuixTheme.colorScheme.onPrimary
        else MiuixTheme.colorScheme.onSurface,
        shape = AppShapes.pill
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
