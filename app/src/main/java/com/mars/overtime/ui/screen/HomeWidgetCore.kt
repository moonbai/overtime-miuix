package com.mars.overtime.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.WidgetPlacement
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppHeadline2Bold
import com.mars.overtime.ui.theme.AppShapes
import com.mars.overtime.util.SalaryCalculator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

// ───────────────────────── 网格排版常量 ─────────────────────────

/** 首页小组件网格列数（2 列），跨度 spanX 最大取此值 */
const val GRID_COLUMNS = 2

/** 网格基础单元高度（1x1 卡片高度），由它推导 2x1 / 2x2 */
val CELL_HEIGHT: Dp = 104.dp

// ───────────────────────── 组件目录 ─────────────────────────

/**
 * 单个组件的定义（与 [WidgetPlacement.id] 对应）。
 * 尺寸由 [fixedSpanX]/[fixedSpanY] 固定，用户不可调整：
 * - 数值类内容短，1x1 足够；
 * - 类型分布图例最多 4 项、每日工时最多 31 根柱，需整行宽度 → 2x1；
 * - 年度趋势 12 根柱带月份标签、环形图带图例，宽高都吃紧 → 2x2。
 * @param isChart 是否为图表类组件（仅影响目录展示，渲染统一走 [WidgetBody]）
 */
data class WidgetDef(
    val id: String,
    val title: String,
    val desc: String,
    val fixedSpanX: Int,
    val fixedSpanY: Int,
    val isChart: Boolean
)

/** 全部可选组件（含数值类与图表类），添加页与设置页共用 */
val WIDGET_CATALOG: List<WidgetDef> = listOf(
    WidgetDef("month_hours", "当月工时", "本月净时长，附与上月的增减对比", 1, 1, false),
    WidgetDef("month_salary", "本月薪资", "本月加班薪资（含请假扣款）", 1, 1, false),
    WidgetDef("last_month_hours", "上月工时", "上月净时长与记录条数", 1, 1, false),
    WidgetDef("year_total", "年度累计", "本年净时长与加班薪资", 1, 1, false),
    WidgetDef("month_leave", "当月请假", "本月请假次数与扣款金额", 1, 1, false),
    WidgetDef("latest_record", "最近记录", "最近一条记录的类型、日期与时长", 1, 1, false),
    WidgetDef("month_type", "类型分布", "本月工作日/周末/节假日工时对比", 2, 1, true),
    WidgetDef("month_daily", "每日工时", "本月每日净工时柱状趋势", 2, 1, true),
    WidgetDef("year_month", "年度趋势", "本年度各月净工时柱状", 2, 2, true),
    WidgetDef("type_donut", "类型占比", "本月三类加班时长占比", 2, 2, true)
)

fun widgetDef(id: String): WidgetDef? = WIDGET_CATALOG.firstOrNull { it.id == id }
fun widgetTitle(id: String): String = widgetDef(id)?.title ?: id

/**
 * 布局读取时统一按目录固定尺寸校正跨度：
 * 旧版本允许用户调整尺寸，持久化里可能残留任意 spanX/spanY，
 * 这里全部归位到设计尺寸，保证「尺寸完全由代码决定」且旧数据平滑升级。
 * 同时按 id 去重：拖拽与 key() 都以 id 为唯一标识，重复 id 会让多张卡片
 * 同时被判定为「正在拖动」，旧备份导入的重复项需在此收敛掉。
 */
fun normalizeLayout(items: List<WidgetPlacement>): List<WidgetPlacement> =
    items.distinctBy { it.id }.map { p ->
        val def = widgetDef(p.id)
        if (def != null) p.copy(spanX = def.fixedSpanX, spanY = def.fixedSpanY) else p.normalized()
    }

// ───────────────────────── 网格排版算法 ─────────────────────────

/** 排版后每个组件在网格中的占位（列、行、跨度） */
data class Cell(val col: Int, val row: Int, val spanX: Int, val spanY: Int)

/**
 * 2 列网格的「首次适配」排版：从左到右、从上到下扫描第一个能容纳
 * (spanX×spanY) 的位置放入，后续小组件会回填前面留下的空洞，
 * 因此 1x1 / 2x1 / 1x2 / 2x2 混合排布也不会产生明显空隙。
 * 列高始终连续填充（无洞），2 宽组件对齐两列。
 */
fun packGrid(items: List<WidgetPlacement>, columns: Int = GRID_COLUMNS): List<Cell> {
    val occ = mutableListOf<BooleanArray>()
    fun ensureRows(r: Int) {
        while (occ.size <= r + 4) occ.add(BooleanArray(columns))
    }
    fun fits(c0: Int, r0: Int, sx: Int, sy: Int): Boolean {
        for (r in r0 until r0 + sy) {
            ensureRows(r)
            for (c in c0 until c0 + sx) if (occ[r][c]) return false
        }
        return true
    }
    fun mark(c0: Int, r0: Int, sx: Int, sy: Int) {
        for (r in r0 until r0 + sy) for (c in c0 until c0 + sx) occ[r][c] = true
    }
    val cells = ArrayList<Cell>(items.size)
    for (item in items) {
        val sx = item.spanX.coerceIn(1, columns)
        val sy = item.spanY.coerceIn(1, 2)
        var placed = false
        outer@ for (r in 0..occ.size + sy) {
            for (c in 0..columns - sx) {
                if (fits(c, r, sx, sy)) {
                    cells.add(Cell(c, r, sx, sy))
                    mark(c, r, sx, sy)
                    placed = true
                    break@outer
                }
            }
        }
        if (!placed) {
            cells.add(Cell(0, occ.size, sx, sy))
            mark(0, occ.size, sx, sy)
        }
    }
    return cells
}

/** 以 dp 表示的网格矩形（左上角 + 宽高），用于绝对定位与命中测试 */
data class RectDp(val x: Dp, val y: Dp, val w: Dp, val h: Dp) {
    val cx: Float get() = ((x + w) / 2f).value
    val cy: Float get() = ((y + h) / 2f).value
    fun contains(o: Offset): Boolean =
        o.x >= x.value && o.x <= (x + w).value && o.y >= y.value && o.y <= (y + h).value
}

/** 拖拽排序过程中供手势闭包读取的最新排版快照（避免闭包捕获过期值） */
data class GridRef(
    val draft: List<WidgetPlacement>,
    val rects: List<RectDp>,
    val cells: List<Cell>,
    val cellW: Dp,
    val cellH: Dp
)

// ───────────────────────── 数据聚合 ─────────────────────────

/** 单条类型分段的展示数据（绝对值用于占比/宽度，label 用于图例） */
data class TypeSeg(val label: String, val hours: Double, val color: Color)

/**
 * 首页全部组件共用的月度/年度聚合结果，一次算好各组件按需取用。
 * 口径与统计页一致：请假为负（抵扣），仅体现在净时长。
 */
data class Aggregate(
    val monthNet: Double, val monthCount: Int,
    val lastMonthNet: Double, val lastMonthCount: Int, val lastMonth: String,
    val monthAmount: Double,
    val yearNet: Double, val yearAmount: Double,
    val monthLeaveCount: Int, val monthLeaveDeduct: Double,
    val latest: OvertimeRecord?,
    val typeWd: Double, val typeWe: Double, val typeHo: Double,
    val typeLeave: Double,
    val dailyNet: List<Pair<Int, Double>>,
    val monthlyNet: List<Pair<Int, Double>>
)

fun aggregateHome(records: List<OvertimeRecord>, month: String): Aggregate {
    val cal = Calendar.getInstance()
    val year = month.substringBefore("-").toIntOrNull() ?: cal.get(Calendar.YEAR)
    val mon = month.substringAfter("-").toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)
    val lastMonth = if (mon == 1) String.format(Locale.getDefault(), "%04d-%02d", year - 1, 12)
    else String.format(Locale.getDefault(), "%04d-%02d", year, mon - 1)

    var mNet = 0.0; var mCount = 0; var lNet = 0.0; var lCount = 0
    var mAmount = 0.0; var mLeaveCount = 0; var mLeaveDeduct = 0.0
    var yNet = 0.0; var yAmount = 0.0
    var wd = 0.0; var we = 0.0; var ho = 0.0; var lv = 0.0

    // 每日净工时（本月）
    val dailyMap = LinkedHashMap<Int, Double>()
    val totalDays = run {
        val c = Calendar.getInstance().apply { clear(); set(year, mon - 1, 1) }
        c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    for (d in 1..totalDays) dailyMap[d] = 0.0
    // 每月净工时（本年 1..12）
    val monthMap = LinkedHashMap<Int, Double>()
    for (m in 1..12) monthMap[m] = 0.0

    records.forEach { rec ->
        cal.timeInMillis = rec.date
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        if (y == year && m == mon) {
            mNet += rec.durationHours
            mAmount += rec.amount
            mCount++
            dailyMap[d] = (dailyMap[d] ?: 0.0) + rec.durationHours
            if (rec.isLeave) {
                mLeaveCount++
                mLeaveDeduct += abs(rec.amount)
                lv += rec.durationHours
            } else when (rec.type) {
                OvertimeType.WORKDAY -> wd += rec.durationHours
                OvertimeType.WEEKEND -> we += rec.durationHours
                OvertimeType.HOLIDAY -> ho += rec.durationHours
            }
        } else if (y == year && m == mon - 1 || (mon == 1 && y == year - 1 && m == 12)) {
            lNet += rec.durationHours
            lCount++
        }
        if (y == year) {
            yNet += rec.durationHours
            yAmount += rec.amount
            monthMap[m] = (monthMap[m] ?: 0.0) + rec.durationHours
        }
    }

    return Aggregate(
        monthNet = mNet, monthCount = mCount,
        lastMonthNet = lNet, lastMonthCount = lCount, lastMonth = lastMonth,
        monthAmount = mAmount,
        yearNet = yNet, yearAmount = yAmount,
        monthLeaveCount = mLeaveCount, monthLeaveDeduct = mLeaveDeduct,
        latest = records.maxByOrNull { it.date },
        typeWd = wd, typeWe = we, typeHo = ho, typeLeave = lv,
        dailyNet = dailyMap.map { it.key to it.value },
        monthlyNet = monthMap.map { it.key to it.value }
    )
}

/** 构建类型分段（工作日/休息日/节假日/请假），仅保留非零项（零工时隐藏） */
@Composable
fun buildTypeSegments(
    agg: Aggregate,
    typeColor: (OvertimeType) -> Color
): List<TypeSeg> = listOf(
    TypeSeg("工作日", agg.typeWd, typeColor(OvertimeType.WORKDAY)),
    TypeSeg("休息日", agg.typeWe, typeColor(OvertimeType.WEEKEND)),
    TypeSeg("节假日", agg.typeHo, typeColor(OvertimeType.HOLIDAY)),
    TypeSeg("请假", abs(agg.typeLeave), MiuixTheme.colorScheme.error)
).filter { it.hours > 0.0001 }

// ───────────────────────── 卡片内容渲染 ─────────────────────────

/**
 * 单个组件的内容（不含卡片外壳）。数值类与图表类统一在此渲染，
 * 首页与「添加」预览页共用，保证「预览即所见」。
 */
@Composable
fun WidgetBody(
    id: String,
    agg: Aggregate,
    month: String,
    typeColor: (OvertimeType) -> Color,
    modifier: Modifier = Modifier
) {
    // 尺寸已固定，不再整体放大文字（放大字号正是小卡片里文字溢出的元凶）。
    // 所有文本单行 + 省略号 + 禁止换行，任意数据长度都不会遮挡或越界。
    Column(modifier.fillMaxSize()) {
        Text(
            text = widgetTitle(id),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        when (id) {
            "month_hours" -> StatRows(
                SalaryCalculator.formatHours(agg.monthNet),
                when {
                    agg.lastMonthNet <= 0.0 && agg.monthNet <= 0.0 -> "上月无记录"
                    agg.monthNet >= agg.lastMonthNet -> "较上月 +${SalaryCalculator.formatHours(agg.monthNet - agg.lastMonthNet)}"
                    else -> "较上月 -${SalaryCalculator.formatHours(agg.lastMonthNet - agg.monthNet)}"
                }
            )
            "last_month_hours" -> StatRows(
                SalaryCalculator.formatHours(agg.lastMonthNet),
                "${agg.lastMonthCount}条记录"
            )
            "month_salary" -> StatRows(
                SalaryCalculator.formatAmount(agg.monthAmount),
                "含请假扣款"
            )
            "year_total" -> StatRows(
                SalaryCalculator.formatHours(agg.yearNet),
                SalaryCalculator.formatAmount(agg.yearAmount)
            )
            "month_leave" -> StatRows(
                "${agg.monthLeaveCount}次",
                if (agg.monthLeaveCount > 0) "扣款 ${SalaryCalculator.formatAmount(agg.monthLeaveDeduct)}" else "本月无请假"
            )
            "latest_record" -> {
                val latest = agg.latest
                val cal = Calendar.getInstance().apply { timeInMillis = latest?.date ?: 0L }
                StatRows(
                    if (latest != null) {
                        if (latest.isLeave) "请假" else latest.type.label
                    } else "暂无记录",
                    if (latest != null) {
                        "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 · " +
                            SalaryCalculator.formatHours(abs(latest.durationHours))
                    } else "去记录页添加"
                )
            }
            "month_type" -> TypeBarChart(buildTypeSegments(agg, typeColor))
            "month_daily" -> DailyBarChart(agg.dailyNet)
            "year_month" -> YearBarChart(agg.monthlyNet, month.substringAfter("-").toIntOrNull() ?: 1)
            "type_donut" -> DonutChart(
                buildTypeSegments(agg, typeColor),
                agg.typeWd + agg.typeWe + agg.typeHo + agg.typeLeave
            )
            else -> StatRows("--", "")
        }
    }
}

@Composable
private fun StatRows(value: String, sub: String) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = AppHeadline2Bold()
        // 数值字号按可用高度收敛（1x1 内容区约 76dp），给标题、副标题和
        // 间距留足空间；金额 / 长备注用省略号收尾，绝不换行挤到下一行。
        val valueSp = minOf(base.fontSize.value, maxHeight.value * 0.42f).sp
        Column(Modifier.fillMaxSize()) {
            Text(
                text = value,
                style = base.copy(fontSize = valueSp),
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = sub,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 图例单项：色块 + 名称 + 数值，单行省略，宽度由外部 weight 分配 */
@Composable
private fun LegendItem(seg: TypeSeg, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .background(seg.color, AppShapes.radiusXs)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = seg.label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = SalaryCalculator.formatHours(seg.hours),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End
        )
    }
}

/** 本月类型分布：单条横向堆叠条 + 两列图例（零工时类型不显示） */
@Composable
private fun TypeBarChart(segs: List<TypeSeg>) {
    if (segs.isEmpty()) {
        EmptyChartHint("本月暂无加班")
        return
    }
    val total = segs.sumOf { it.hours }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(MiuixTheme.colorScheme.secondaryContainer, AppShapes.radiusSm)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                segs.forEach { seg ->
                    val frac = (seg.hours / total).toFloat().coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .weight(frac)
                            .fillMaxHeight()
                            .background(seg.color, AppShapes.radiusXs)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // 2x1 卡片内容区只有约 76dp 高：4 项图例若排成 4 行会挤压文字，
        // 改为两列、最多 2 行，并让图例整体占满剩余高度。
        segs.chunked(2).forEach { rowSegs ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowSegs.forEach { seg -> LegendItem(seg, Modifier.weight(1f)) }
                if (rowSegs.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 本月每日净工时柱状（最多 31 根，正负以主色/错误色区分） */
@Composable
private fun DailyBarChart(dailyNet: List<Pair<Int, Double>>) {
    if (dailyNet.all { it.second == 0.0 }) {
        EmptyChartHint("本月暂无工时")
        return
    }
    val maxAbs = dailyNet.maxOf { abs(it.second) }.coerceAtLeast(0.001)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            dailyNet.forEach { (_, net) ->
                val frac = (abs(net) / maxAbs).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(frac)
                            .background(
                                if (net >= 0) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.error,
                                AppShapes.radiusXs
                            )
                    )
                }
            }
        }
    }
}

/** 本年度各月净工时柱状（12 根，当月高亮） */
@Composable
private fun YearBarChart(monthlyNet: List<Pair<Int, Double>>, currentMonth: Int) {
    val maxAbs = monthlyNet.maxOf { abs(it.second) }.coerceAtLeast(0.001)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            monthlyNet.forEach { (m, net) ->
                val frac = (abs(net) / maxAbs).toFloat().coerceIn(0f, 1f)
                val highlight = m == currentMonth
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(frac)
                                .background(
                                    if (highlight) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    AppShapes.radiusXs
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$m",
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (highlight) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** 类型占比环形图（中心显示净时长，图例在侧） */
@Composable
private fun DonutChart(segs: List<TypeSeg>, net: Double) {
    if (segs.isEmpty()) {
        EmptyChartHint("暂无数据")
        return
    }
    val sum = segs.sumOf { it.hours }
    Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(76.dp)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = size.minDimension * 0.2f
                val inner = size.minDimension - strokePx
                val topLeft = Offset(strokePx / 2f, strokePx / 2f)
                val arcSize = androidx.compose.ui.geometry.Size(inner, inner)
                var start = -90f
                segs.forEach { seg ->
                    val sweep = ((seg.hours / sum) * 360f).toFloat()
                    drawArc(
                        color = seg.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = SalaryCalculator.formatHours(net),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "加班时长",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            segs.forEach { seg ->
                LegendItem(
                    seg,
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyChartHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 添加页/预览用的静态卡片外壳（无交互），内部渲染 [WidgetBody] */
@Composable
fun PreviewWidgetCard(
    id: String,
    agg: Aggregate,
    month: String,
    typeColor: (OvertimeType) -> Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        cornerRadius = AppDimens.cardCorner,
        insideMargin = PaddingValues(14.dp)
    ) {
        WidgetBody(
            id = id,
            agg = agg,
            month = month,
            typeColor = typeColor
        )
    }
}
