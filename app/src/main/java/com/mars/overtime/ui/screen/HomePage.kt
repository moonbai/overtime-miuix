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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.data.repository.DEFAULT_HOME_LAYOUT
import com.mars.overtime.data.repository.WidgetPlacement
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    topReserve: androidx.compose.ui.unit.Dp = 0.dp,
    bottomReserve: androidx.compose.ui.unit.Dp = 0.dp,
    listState: LazyListState = rememberLazyListState()
) {
    // 「是否已加载」与「记录内容」必须来自同一份快照：旧写法用 initial=emptyList()
    // 加独立 LaunchedEffect 打标记，两者更新不同步时会出现 loaded=true 但 records
    // 仍为空的一帧，于是切回首页/记录页恰好闪一下「无记录」空状态。
    val recordsSnapshot by repository.getAllRecords().collectAsState(initial = null)
    val recordsLoaded = recordsSnapshot != null
    val records = recordsSnapshot.orEmpty()
    val currentMonth = remember { SalaryCalculator.getCurrentYearMonth() }

    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())
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
            // 明细类组件点进去看记录，其余分析类组件进统计页
            "month_leave", "latest_record", "month_count", "avg_record",
            "max_single", "max_daily", "streak_days" -> onOpenRecords()
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
                repository = repository,
                month = currentMonth,
                records = records,
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
                        text = "长按卡片拖动排序 · 点 × 移除",
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (editingState.value) MiuixIcons.Ok else MiuixIcons.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(AppIconSize.xs)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (editingState.value) "完成" else "管理小组件",
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
 * - 长按进入编辑态并开始拖动排序（跟随手指，跨过其它卡片中心即交换）；
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
        refState.value = GridRef(draft, rects, cells, cellW, cellH)

        Box(Modifier.fillMaxWidth().height(totalHeight)) {
            draft.forEach { item ->
                key(item.id) {
                val index = draft.indexOf(item)
                val rect = rects[index]
                val isDragged = draggingIdState.value == item.id
                val dxDp = if (isDragged) density.run { dragOffsetPxState.value.x.toDp() } else 0.dp
                val dyDp = if (isDragged) density.run { dragOffsetPxState.value.y.toDp() } else 0.dp
                Box(
                    Modifier
                        .offset(x = rect.x + dxDp, y = rect.y + dyDp)
                        .size(rect.w, rect.h)
                        .zIndex(if (isDragged) 1f else 0f)
                        .shadow(if (isDragged) AppDimens.widgetDragElevation else 0.dp, AppShapes.radiusLg)
                        // 常态给白卡片一层极淡描边，让它在浅灰大背景上「浮」起来；
                        // 编辑态改用主题色描边提示可操作。
                        .border(
                            1.dp,
                            if (editingState.value)
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                            AppShapes.radiusLg
                        )
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    editingState.value = true
                                    draggingIdState.value = item.id
                                    dragOffsetPxState.value = Offset.Zero
                                },
                                onDrag = { _, dragAmount ->
                                    val id = draggingIdState.value
                                    if (id != item.id) return@detectDragGesturesAfterLongPress
                                    dragOffsetPxState.value += dragAmount
                                    val ref = refState.value
                                    val idx = ref.draft.indexOfFirst { it.id == item.id }
                                    if (idx < 0) return@detectDragGesturesAfterLongPress
                                    val dx = density.run { dragOffsetPxState.value.x.toDp().value }
                                    val dy = density.run { dragOffsetPxState.value.y.toDp().value }
                                    val cx = ref.rects[idx].cx + dx
                                    val cy = ref.rects[idx].cy + dy
                                    val target = ref.rects.withIndex()
                                        .filter { it.index != idx }
                                        .minByOrNull { (it.value.cx - cx) * (it.value.cx - cx) + (it.value.cy - cy) * (it.value.cy - cy) }
                                        ?.index
                                    if (target != null && target != idx) {
                                        val moved = ref.draft.toMutableList()
                                        val it2 = moved.removeAt(idx)
                                        moved.add(target.coerceAtMost(moved.size), it2)
                                        onChange(moved)
                                    }
                                },
                                onDragEnd = {
                                    if (draggingIdState.value == item.id) {
                                        draggingIdState.value = null
                                        dragOffsetPxState.value = Offset.Zero
                                    }
                                }
                            )
                        }
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = AppDimens.cardCorner,
                        insideMargin = PaddingValues(14.dp),
                        onClick = { if (!editingState.value) onCardClick(item.id) },
                        showIndication = true,
                        pressFeedbackType = PressFeedbackType.Sink
                    ) {
                        WidgetBody(item.id, agg, month, typeColor)
                    }
                    if (editingState.value) {
                        // 左上：拖拽提示
                        Box(
                            Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp),
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
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
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
    repository: OvertimeRepository,
    month: String,
    records: List<OvertimeRecord>,
    mode: String,
    typeColor: (OvertimeType) -> Color
) {
    var stats by remember { mutableStateOf<com.mars.overtime.data.repository.MonthlyStats?>(null) }

    // 以 month 与 records 为 key：当前月内新增/删除记录时自动重算概览
    LaunchedEffect(month, records) {
        stats = repository.getMonthlyStats(month)
    }

    // 分类模式：本月三类工时；请假按「负数的休息日」并入休息日（状态仍显示请假）
    val typeHours = remember(records, month) {
        val cal = Calendar.getInstance()
        var workday = 0.0; var weekend = 0.0; var holiday = 0.0
        records.forEach { rec ->
            cal.timeInMillis = rec.date
            val m = "${cal.get(Calendar.YEAR)}-${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}"
            if (m == month) {
                if (rec.isLeave) weekend += rec.durationHours
                else when (rec.type) {
                    OvertimeType.WORKDAY -> workday += rec.durationHours
                    OvertimeType.WEEKEND -> weekend += rec.durationHours
                    OvertimeType.HOLIDAY -> holiday += rec.durationHours
                }
            }
        }
        Triple(workday, weekend, holiday)
    }
    val maxType = maxOf(typeHours.first, typeHours.second, typeHours.third, 0.001)

    Card(
        // 不再额外内缩 4dp：与小组件网格左右边缘严格对齐
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppDimens.cardCorner
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 与小组件卡统一的设计语言：色点 + 标题 + 右侧口径徽标
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(AppShapes.pill)
                        .background(MiuixTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$month 概览",
                    style = AppTitle3Semibold(),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "本月",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (mode == "by_type") {
                // 分类模式：净时长为主数字，三类加班以类型色条并列展示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        "净时长",
                        stats?.let { SalaryCalculator.formatHours(it.totalHours) } ?: "0小时"
                    )
                    StatItem("预估薪资", stats?.let { SalaryCalculator.formatAmount(it.totalAmount) } ?: "¥0.00")
                    StatItem("记录数", "${stats?.recordCount ?: 0}条")
                }
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    OvertimeType.WORKDAY to typeHours.first,
                    OvertimeType.WEEKEND to typeHours.second,
                    OvertimeType.HOLIDAY to typeHours.third
                ).forEach { (type, hours) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = type.label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = typeColor(type),
                            modifier = Modifier.width(48.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .background(MiuixTheme.colorScheme.secondaryContainer, AppShapes.radiusSm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((hours / maxType).toFloat().coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .background(typeColor(type), AppShapes.radiusSm)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = SalaryCalculator.formatHours(hours),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.width(56.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("总时长", stats?.let { SalaryCalculator.formatHours(it.totalHours) } ?: "0小时")
                    StatItem("预估薪资", stats?.let { SalaryCalculator.formatAmount(it.totalAmount) } ?: "¥0.00")
                    StatItem("记录数", "${stats?.recordCount ?: 0}条")
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

/**
 * 首页空状态：轻量插画 + 引导文案 + 主操作按钮。
 * 用「主题色图标嵌套淡色圆 + 两个点缀圆」构成一张克制的小插画，
 * 弱化了「无数据」的空旷感，并把首条记录的转化入口放在最显眼处。
 */
@Composable
private fun HomeEmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(156.dp),
            contentAlignment = Alignment.Center
        ) {
            // 主圆（淡色容器）
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(AppShapes.pill)
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
            )
            // 右上点缀（主题色浅块）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(42.dp)
                    .offset(x = 10.dp, y = (-8).dp)
                    .clip(AppShapes.pill)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            )
            // 左下点缀（次容器块）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(30.dp)
                    .offset(x = (-12).dp, y = 10.dp)
                    .clip(AppShapes.pill)
                    .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f))
            )
            // 主题色插画主体图标（闹钟：呼应加班/打卡）
            Icon(
                MiuixIcons.Alarm,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }
        Text(
            text = "还没有加班记录",
            style = AppTitle3Semibold(),
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = "记录第一笔加班，从这里开始",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = AppDimens.controlCorner,
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Icon(
                MiuixIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(AppIconSize.xs)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("添加记录", fontWeight = FontWeight.Medium)
        }
    }
}
