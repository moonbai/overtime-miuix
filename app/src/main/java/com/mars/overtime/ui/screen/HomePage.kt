package com.mars.overtime.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.ui.component.AppEmptyState
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.ui.component.rememberAppRecords
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.util.AppDefaults
import com.mars.overtime.data.repository.DEFAULT_HOME_LAYOUT
import com.mars.overtime.data.repository.WidgetPlacement
import com.mars.overtime.ui.theme.AppBody1Medium
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes
import com.mars.overtime.ui.theme.AppTitle3Semibold
import com.mars.overtime.ui.theme.AppHeadline2Bold
import com.mars.overtime.util.SalaryCalculator
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首页：信息面板。
 * 结构 = 月度概览卡（总览 / 分类两种模式）+ 可自定义小组件网格（2 列，
 * 各组件尺寸由目录固定为 1×1 / 2×1 / 2×2，不可调整）。
 *
 * 组件的长按拖动排序与删除在首页直接完成（编辑态）；尺寸由代码固定；
 * 「管理小组件」入口直达设置页做启用/添加管理。
 */
@Composable
fun HomePage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository,
    onOpenRecords: () -> Unit = {},
    topReserve: androidx.compose.ui.unit.Dp = AppDimens.zero,
    bottomReserve: androidx.compose.ui.unit.Dp = AppDimens.zero,
    listState: LazyListState = rememberLazyListState()
) {
    // 「是否已加载」与「记录内容」必须来自同一份快照：旧写法用 initial=emptyList()
    // 加独立 LaunchedEffect 打标记，两者更新不同步时会出现 loaded=true 但 records
    // 仍为空的一帧，于是切回首页/记录页恰好闪一下「无记录」空状态。
    val recordsSnapshot by rememberAppRecords(repository)
    val recordsLoaded = recordsSnapshot != null
    val records = recordsSnapshot.orEmpty()
    val currentMonth = remember { SalaryCalculator.getCurrentYearMonth() }

    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = AppDefaults.PRIMARY_ARGB)
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = AppDefaults.WEEKEND_ARGB)
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = AppDefaults.HOLIDAY_ARGB)
    val overviewMode by settingsRepository.homeOverviewMode.collectAsState(initial = "total")

    // 用户配置的类型色（与统计页同源），分类概览与组件色条共用
    val typeColor: (OvertimeType) -> Color = { type ->
        when (type) {
            OvertimeType.WORKDAY -> Color(typeColorWorkday)
            OvertimeType.WEEKEND -> Color(typeColorWeekend)
            OvertimeType.HOLIDAY -> Color(typeColorHoliday)
        }
    }

    // 首页组件布局：尺寸由目录固定，读取时统一校正（兼容旧版本残留的自定义跨度）；
    // 本地 draft 为编辑期间的唯一真相，拖动排序/删除写回 draft 并持久化。
    val layoutRaw by settingsRepository.homeLayout.collectAsState(initial = DEFAULT_HOME_LAYOUT)
    val layoutValue = remember(layoutRaw) { normalizeLayout(layoutRaw) }
    var draft by remember { mutableStateOf(layoutValue) }
    val draggingIdState = remember { mutableStateOf<String?>(null) }
    val dragOffsetPxState = remember { mutableStateOf(Offset.Zero) }
    val editingState = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(layoutValue) {
        if (draggingIdState.value == null) draft = layoutValue
    }
    fun commit(list: List<WidgetPlacement>) {
        draft = list
        scope.launch { settingsRepository.setHomeLayout(list) }
    }

    // 全部组件共用的聚合（含图表所需的每日/每月/类型分布）
    val agg = remember(records, currentMonth) { aggregateHome(records, currentMonth) }

    fun handleWidgetClick(id: String) {
        when (id) {
            "month_leave", "latest_record" -> onOpenRecords()
            "last_month_hours" -> navController.navigate("statistics?month=${agg.lastMonth}")
            else -> navController.navigate("statistics")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(
            start = AppDimens.pageHorizontal,
            top = AppDimens.pageVertical + topReserve,
            end = AppDimens.pageHorizontal,
            bottom = AppDimens.pageVertical + bottomReserve
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.groupSpacing)
    ) {
        item {
            MonthlyOverviewCard(
                month = currentMonth,
                agg = agg,
                mode = overviewMode,
                typeColor = typeColor
            )
        }

        if (recordsLoaded && records.isEmpty()) {
            item {
                HomeEmptyState(
                    onAdd = {
                        // 连点时避免把多个 add_record 实例压入回退栈
                        navController.navigate("add_record") { launchSingleTop = true }
                    }
                )
            }
        } else {
            // 小组件网格（2 列，固定跨度），长按拖动排序、编辑态删除
            item(key = "widget_grid") {
                HomeWidgetGrid(
                    draft = draft,
                    agg = agg,
                    month = currentMonth,
                    typeColor = typeColor,
                    editingState = editingState,
                    draggingIdState = draggingIdState,
                    dragOffsetPxState = dragOffsetPxState,
                    onChange = { commit(it) },
                    onCardClick = { handleWidgetClick(it) }
                )
            }
            // 编辑态提示
            if (editingState.value) {
                item {
                    Text(
                        text = "长按卡片拖动排序 · 点 x 移除",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            // 底部操作：编辑态显示「完成」，否则「管理小组件」直达设置页
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        onClick = {
                            if (editingState.value) editingState.value = false
                            else navController.navigate("home_widgets")
                        },
                        color = MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                        shape = AppShapes.radiusLg
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = AppDimens.spaceMd,
                                vertical = AppDimens.spaceXxs2
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (editingState.value) MiuixIcons.Ok else MiuixIcons.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(AppIconSize.xs)
                            )
                            Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
                            Text(
                                text = if (editingState.value) "完成" else "编辑",
                                style = MiuixTheme.textStyles.footnote1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2 列固定跨度小组件网格：每个组件按 [packGrid] 排版后绝对定位。
 * - 长按进入编辑态并开始拖动：仅被拖卡片跟随手指，松手后按落点一次性排序；
 * - 编辑态下右上 × 移除组件；非编辑态点击卡片按类型跳转。
 */
@Composable
private fun HomeWidgetGrid(
    draft: List<WidgetPlacement>,
    agg: Aggregate,
    month: String,
    typeColor: (OvertimeType) -> Color,
    editingState: MutableState<Boolean>,
    draggingIdState: MutableState<String?>,
    dragOffsetPxState: MutableState<Offset>,
    onChange: (List<WidgetPlacement>) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = AppDimens.groupSpacing
    val cellH = CELL_HEIGHT
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cellW = (maxWidth - gap) / GRID_COLUMNS
        val cells = remember(draft) { packGrid(draft) }
        val rects = remember(draft, cellW) {
            cells.map { c ->
                RectDp(
                    x = (cellW + gap) * c.col,
                    y = (cellH + gap) * c.row,
                    w = cellW * c.spanX + gap * (c.spanX - 1),
                    h = cellH * c.spanY + gap * (c.spanY - 1)
                )
            }
        }
        val totalRows = cells.maxOfOrNull { it.row + it.spanY } ?: 0
        val totalHeight = cellH * totalRows + gap * (totalRows - 1)
        val density = LocalDensity.current
        val refState = remember { mutableStateOf(GridRef(draft, rects, cells, cellW, cellH)) }
        // 组合阶段不写状态：等本次组合成功后再把最新排版同步给手势闭包读取
        SideEffect { refState.value = GridRef(draft, rects, cells, cellW, cellH) }
        // 拖动开始时的布局快照：拖动期间 draft 不变，松手时按固定 rect 判定落位
        val dragStartRefState = remember { mutableStateOf<GridRef?>(null) }
        // 松手时读取最新 draft：拖动中若删除了组件，避免用旧快照把删除回滚
        val currentDraft by rememberUpdatedState(draft)

        Box(Modifier.fillMaxWidth().height(totalHeight)) {
            draft.forEachIndexed { index, item ->
                key(item.id) {
                val rect = rects[index]
                val isDragged = draggingIdState.value == item.id
                val dxDp = if (isDragged) density.run { dragOffsetPxState.value.x.toDp() } else AppDimens.zero
                val dyDp = if (isDragged) density.run { dragOffsetPxState.value.y.toDp() } else AppDimens.zero
                // 拖动中只更新偏移，排序统一在松手时做一次：旧实现每次 onDrag 都重排
                // draft，rects 随之变化导致整组卡片跟着抖动、像多个组件同时被拖动。
                fun finishDrag(applyReorder: Boolean) {
                    if (draggingIdState.value != item.id) return
                    val startRef = dragStartRefState.value
                    val offset = dragOffsetPxState.value
                    if (applyReorder && startRef != null && currentDraft.size == startRef.draft.size) {
                        val idx = startRef.draft.indexOfFirst { it.id == item.id }
                        if (idx >= 0) {
                            val dx = density.run { offset.x.toDp().value }
                            val dy = density.run { offset.y.toDp().value }
                            val cx = startRef.rects[idx].cx + dx
                            val cy = startRef.rects[idx].cy + dy
                            val target = startRef.rects.withIndex()
                                .filter { it.index != idx }
                                .minByOrNull { (it.value.cx - cx) * (it.value.cx - cx) + (it.value.cy - cy) * (it.value.cy - cy) }
                                ?.index
                            val minDragPx = density.run { AppDimens.spaceLg.toPx() }
                            if (target != null && offset.getDistance() > minDragPx) {
                                val moved = startRef.draft.toMutableList()
                                val dragged = moved.removeAt(idx)
                                moved.add(target.coerceAtMost(moved.size), dragged)
                                onChange(moved)
                            }
                        }
                    }
                    draggingIdState.value = null
                    dragOffsetPxState.value = Offset.Zero
                    dragStartRefState.value = null
                }
                Box(
                    Modifier
                        .offset(x = rect.x + dxDp, y = rect.y + dyDp)
                        .size(rect.w, rect.h)
                        .zIndex(if (isDragged) 1f else 0f)
                        .shadow(
                            if (isDragged) AppDimens.widgetDragElevation else AppDimens.zero,
                            AppShapes.radiusLg
                        )
                        .then(
                            if (editingState.value) Modifier.border(
                                AppDimens.hairline,
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.35f),
                                AppShapes.radiusLg
                            ) else Modifier
                        )
                        .pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    editingState.value = true
                                    draggingIdState.value = item.id
                                    dragOffsetPxState.value = Offset.Zero
                                    dragStartRefState.value = refState.value
                                },
                                onDrag = { _, dragAmount ->
                                    if (draggingIdState.value != item.id) return@detectDragGesturesAfterLongPress
                                    dragOffsetPxState.value += dragAmount
                                },
                                onDragEnd = { finishDrag(applyReorder = true) },
                                onDragCancel = { finishDrag(applyReorder = false) }
                            )
                        }
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = AppDimens.cardCorner,
                        insideMargin = WIDGET_CARD_PADDING,
                        onClick = { if (!editingState.value) onCardClick(item.id) },
                        showIndication = true,
                        pressFeedbackType = PressFeedbackType.Sink
                    ) {
                        WidgetBody(item.id, agg, month, typeColor)
                    }
                    if (editingState.value) {
                        // 左上：拖拽提示
                        Box(
                            Modifier.align(Alignment.TopStart)
                                .padding(AppDimens.spaceXxs2)
                                .size(AppIconSize.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                MiuixIcons.Sort,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(AppIconSize.xs)
                            )
                        }
                        // 右上：删除
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .padding(AppDimens.spaceXxs)
                                .size(AppIconSize.md)
                                .clip(AppShapes.pill)
                                .background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f))
                                .clickable { onChange(draft.filter { it.id != item.id }) },
                            contentAlignment = Alignment.Center
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
            }
        }
    }
}

}

@Composable
private fun MonthlyOverviewCard(
    month: String,
    agg: Aggregate,
    mode: String,
    typeColor: (OvertimeType) -> Color
) {
    // 概览与小组件共用同一份聚合：不再额外查一次数据库、也不再全表遍历两次，
    // 同时消除 DB 结果回来前显示「0H」的首帧闪烁。口径与 getMonthlyStats 一致
    // （请假为负、分类工时排除请假）。
    val typeHours = listOf(
        OvertimeType.WORKDAY to agg.typeWd,
        OvertimeType.WEEKEND to agg.typeWe,
        OvertimeType.HOLIDAY to agg.typeHo
    )
    val maxType = maxOf(agg.typeWd, agg.typeWe, agg.typeHo, 0.001)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.spaceXxs),
        cornerRadius = AppDimens.cardCorner
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.spaceMd)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$month 概览",
                    style = AppTitle3Semibold(),
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            if (mode == "by_type") {
                // 分类模式：净时长为主数字，三类加班以类型色条并列展示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("加班时长", SalaryCalculator.formatHours(agg.monthNet))
                    StatItem("加班薪资", SalaryCalculator.formatAmount(agg.monthAmount))
                    StatItem("加班次数", "${agg.monthCount}次")
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                typeHours.forEach { (type, hours) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppDimens.spaceXxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = type.label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = typeColor(type),
                            modifier = Modifier.width(AppDimens.overviewLabelWidth)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(AppDimens.spaceXs)
                                .background(MiuixTheme.colorScheme.secondaryContainer, AppShapes.radiusSm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((hours / maxType).toFloat().coerceIn(0f, 1f))
                                    .height(AppDimens.spaceXs)
                                    .background(typeColor(type), AppShapes.radiusSm)
                            )
                        }
                        Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                        Text(
                            text = SalaryCalculator.formatHours(hours),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.width(AppDimens.overviewValueWidth),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("总时长", SalaryCalculator.formatHours(agg.monthNet))
                    StatItem("加班薪资", SalaryCalculator.formatAmount(agg.monthAmount))
                    StatItem("记录数", "${agg.monthCount}条")
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 概览数字明显大于标签：headline2 大号加粗，与 footnote1 标签形成清晰主次
        Text(
            text = value,
            style = AppHeadline2Bold(),
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/** 首页空状态：复用全局标准件，仅按页面语义换插画图标。 */
@Composable
private fun HomeEmptyState(onAdd: () -> Unit) {
    AppEmptyState(icon = MiuixIcons.Alarm, onAction = onAdd)
}
