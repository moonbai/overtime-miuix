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
 * 添加小组件页：只展示**尚未添加**的组件，以当前真实数据实时预览卡片样式。
 * 已添加项不出现在列表中（添加成功后立即移出）；移除统一在首页长按编辑态
 * 或「首页小组件」管理页完成，避免同一动作多处重复。
 *
 * 组件尺寸由 [WIDGET_CATALOG] 按内容固定（不可调整）；
 * 近 30 个组件按 [WidgetCategory] 分组 + 顶部筛选条，筛选计数均为「未添加数」。
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

    // 本页只展示「尚未添加」的组件：已添加的直接不出现，添加成功后该项立即消失。
    // 移除入口统一放在首页长按编辑态与「首页小组件」管理页，避免同一动作三处重复。
    val addedIds = remember(layout) { layout.mapTo(HashSet()) { it.id } }
    val available = remember(addedIds) { WIDGET_CATALOG.filter { it.id !in addedIds } }
    var filter by remember { mutableStateOf<WidgetCategory?>(null) }
    val shown = remember(filter, available) {
        if (filter == null) available else available.filter { it.category == filter }
    }
    // 当前筛选分类里的组件全部添加完后，自动回到「全部」，避免停在空白页
    LaunchedEffect(available) {
        if (filter != null && available.none { it.category == filter }) filter = null
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
            SettingsNote("仅显示尚未添加的组件；预览为当前数据实时渲染，尺寸按内容自动匹配。添加后该项自动移出列表，移除请到首页长按组件或「首页小组件」管理页操作。")
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = AppDimens.spaceXs),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("全部 ${available.size}", filter == null) { filter = null }
                WidgetCategory.values().forEach { cat ->
                    val count = available.count { it.category == cat }
                    if (count > 0) FilterChip("${cat.label} $count", filter == cat) { filter = cat }
                }
            }
        }

        if (available.isEmpty()) {
            item {
                SettingsGroup(title = "全部已添加") {
                    BasicComponent(
                        title = "${WIDGET_CATALOG.size} 种组件已全部加入首页",
                        summary = "如需移除，请返回首页长按组件进入编辑态，或到「首页小组件」管理页操作",
                        onClick = { navController.popBackStack() }
                    )
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
                                persist(
                                    layout + WidgetPlacement(
                                        def.id,
                                        def.fixedSpanX,
                                        def.fixedSpanY
                                    )
                                )
                                scope.launch { snackbarHostState.showCustomToast("已添加「${def.title}」") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                MiuixIcons.Add,
                                contentDescription = null,
                                modifier = Modifier.size(AppIconSize.xs)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("添加")
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
