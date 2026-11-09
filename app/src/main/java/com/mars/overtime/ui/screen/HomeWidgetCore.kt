package com.mars.overtime.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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

/**
 * 网格基础单元高度（1x1 卡片高度）。
 * 旧值 104dp 扣掉 14dp×2 内边距后只剩 76dp，标题 + 数值 + 副信息必然挤在一起；
 * 取 128dp（内容区 100dp）：数值卡放得下「标题 / 主数值 / 进度条 / 胶囊」四层，
 * 2x1 图表卡也放得下「标题 / 摘要行 / 图形 / 图例」而不互相挤压。
 */
val CELL_HEIGHT: Dp = AppDimens.widgetCellHeight

// ───────────────────────── 组件目录 ─────────────────────────

/** 组件分类：添加页按此分组，也让卡片配色有语义 */
enum class WidgetCategory(val label: String) {
    OVERVIEW("本月概览"),
    TYPE("类型分项"),
    TREND("趋势图表"),
    LARGE("大图分析")
}

/**
 * 强调色语义。不直接存 Color：用户自定义类型色在 Composable 上下文里才能取到，
 * 这里只存「意图」，渲染时由 [resolveAccent] 解析。
 */
enum class WidgetAccent {
    PRIMARY, WORKDAY, WEEKEND, HOLIDAY, DANGER, VIOLET, NEUTRAL
}

/**
 * 单个组件的定义（与 [WidgetPlacement.id] 对应）。
 * 尺寸由 [fixedSpanX]/[fixedSpanY] 固定，用户不可调整：
 * - 单指标数值卡 1x1；
 * - 需要整行宽度的堆叠条 / 柱状 / 横向对比 2x1；
 * - 带图例、刻度或多行网格的复杂图表 2x2。
 */
data class WidgetDef(
    val id: String,
    val title: String,
    val desc: String,
    val fixedSpanX: Int,
    val fixedSpanY: Int,
    val category: WidgetCategory = WidgetCategory.OVERVIEW,
    val accent: WidgetAccent = WidgetAccent.PRIMARY,
    val isChart: Boolean = false
)

/** 全部可选组件（数值 / 分项 / 趋势 / 大图四类），添加页、设置页、首页共用 */
val WIDGET_CATALOG: List<WidgetDef> = listOf(
    // ── 本月概览：1x1 单指标 ──
    WidgetDef("month_hours", "当月工时", "本月净加班时长，附与上月环比", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.PRIMARY),
    WidgetDef("month_salary", "本月薪资", "本月预估加班费（含请假扣款）", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.WEEKEND),
    WidgetDef("month_count", "本月记录", "本月记录笔数与有记录天数", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.WORKDAY),
    WidgetDef("avg_daily", "日均工时", "按本月有记录天数平均", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.WORKDAY),
    WidgetDef("avg_record", "单次均时", "平均每笔加班时长", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.VIOLET),
    WidgetDef("max_single", "单次最长", "本月最长的一笔加班", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.HOLIDAY),
    WidgetDef("max_daily", "单日最高", "本月单日净工时峰值", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.HOLIDAY),
    WidgetDef("streak_days", "连续记录", "截至今日连续有记录的天数", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.VIOLET),
    WidgetDef("week_hours", "本周工时", "本周一至今的净加班时长", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.PRIMARY),
    WidgetDef("week_salary", "本周薪资", "本周一至今预估加班费", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.WEEKEND),
    WidgetDef("last_month_hours", "上月工时", "上月净时长与记录条数", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.NEUTRAL),
    WidgetDef("year_total", "年度累计", "本年净时长与预估薪资", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.PRIMARY),
    WidgetDef("year_count", "年度记录", "本年累计记录笔数", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.WORKDAY),
    WidgetDef("month_leave", "当月请假", "本月请假次数与扣款金额", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.DANGER),
    WidgetDef("latest_record", "最近记录", "最近一笔的类型、日期与时长", 1, 1, WidgetCategory.OVERVIEW, WidgetAccent.NEUTRAL),

    // ── 类型分项：1x1，跟随用户自定义类型色 ──
    WidgetDef("type_workday", "工作日工时", "本月工作日加班净时长与占比", 1, 1, WidgetCategory.TYPE, WidgetAccent.WORKDAY),
    WidgetDef("type_weekend", "休息日工时", "本月休息日加班净时长与占比", 1, 1, WidgetCategory.TYPE, WidgetAccent.WEEKEND),
    WidgetDef("type_holiday", "节假日工时", "本月节假日加班净时长与占比", 1, 1, WidgetCategory.TYPE, WidgetAccent.HOLIDAY),

    // ── 趋势图表：2x1 ──
    WidgetDef("month_type", "类型分布", "本月三类工时堆叠占比与图例", 2, 1, WidgetCategory.TREND, WidgetAccent.PRIMARY, true),
    WidgetDef("month_daily", "每日工时", "本月每日净工时柱状趋势", 2, 1, WidgetCategory.TREND, WidgetAccent.PRIMARY, true),
    WidgetDef("last7_trend", "近 7 天", "最近 7 天净工时与星期分布", 2, 1, WidgetCategory.TREND, WidgetAccent.VIOLET, true),
    WidgetDef("last6_month", "近 6 月", "近半年每月净工时趋势", 2, 1, WidgetCategory.TREND, WidgetAccent.VIOLET, true),
    WidgetDef("rate_split", "倍率贡献", "按加班倍率分段的薪资构成", 2, 1, WidgetCategory.TREND, WidgetAccent.WEEKEND, true),
    WidgetDef("type_amount", "类型薪资", "三类加班各自贡献的金额", 2, 1, WidgetCategory.TREND, WidgetAccent.WEEKEND, true),
    WidgetDef("month_compare", "本月对比", "本月与上月时长 / 薪资双条对比", 2, 1, WidgetCategory.TREND, WidgetAccent.WORKDAY, true),

    // ── 大图分析：2x2 ──
    WidgetDef("type_donut", "类型占比", "环形占比 + 分项明细与占比条", 2, 2, WidgetCategory.LARGE, WidgetAccent.PRIMARY, true),
    WidgetDef("year_month", "年度趋势", "本年度 12 个月净工时柱状", 2, 2, WidgetCategory.LARGE, WidgetAccent.PRIMARY, true),
    WidgetDef("calendar_heatmap", "本月日历", "日历热力图，颜色深浅表示单日工时", 2, 2, WidgetCategory.LARGE, WidgetAccent.WORKDAY, true),
    WidgetDef("month_summary", "月度综合", "四项关键指标 + 近 7 天迷你趋势", 2, 2, WidgetCategory.LARGE, WidgetAccent.PRIMARY, true)
)

fun widgetDef(id: String): WidgetDef? = WIDGET_CATALOG.firstOrNull { it.id == id }
fun widgetTitle(id: String): String = widgetDef(id)?.title ?: id

/** 数值类组件 id 集合：统一走 [MetricContent] 三段式渲染 */
private val METRIC_IDS: Set<String> =
    WIDGET_CATALOG.filterNot { it.isChart }.mapTo(LinkedHashSet()) { it.id }

/**
 * 布局读取时统一按目录固定尺寸校正跨度：
 * 旧版本允许用户调整尺寸，持久化里可能残留任意 spanX/spanY，这里全部归位到设计尺寸。
 */
fun normalizeLayout(items: List<WidgetPlacement>): List<WidgetPlacement> =
    items.map { p ->
        val def = widgetDef(p.id)
        if (def != null) p.copy(spanX = def.fixedSpanX, spanY = def.fixedSpanY) else p.normalized()
    }

// ───────────────────────── 网格排版算法 ─────────────────────────

/** 排版后每个组件在网格中的占位（列、行、跨度） */
data class Cell(val col: Int, val row: Int, val spanX: Int, val spanY: Int)

/** 2 列网格「首次适配」排版：后续小组件回填前面留下的空洞 */
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

/** 单条类型分段的展示数据 */
data class TypeSeg(val label: String, val hours: Double, val color: Color)

/** 柱状图单个条目 */
private data class BarEntry(val label: String, val value: Double, val highlight: Boolean = false)

/**
 * 首页全部组件共用的聚合结果，一次算好各组件按需取用。
 * 口径与统计页一致：请假为负（抵扣），仅体现在净时长。
 */
data class Aggregate(
    val monthNet: Double, val monthCount: Int,
    val lastMonthNet: Double, val lastMonthCount: Int, val lastMonth: String,
    val lastMonthAmount: Double,
    val monthAmount: Double,
    val yearNet: Double, val yearAmount: Double, val yearCount: Int,
    val monthLeaveCount: Int, val monthLeaveDeduct: Double,
    val latest: OvertimeRecord?,
    val typeWd: Double, val typeWe: Double, val typeHo: Double,
    val typeLeave: Double,
    val typeAmountWd: Double, val typeAmountWe: Double, val typeAmountHo: Double,
    val dailyNet: List<Pair<Int, Double>>,
    val monthlyNet: List<Pair<Int, Double>>,
    val monthDays: Int,
    val monthFirstDayOffset: Int,
    val activeDays: Int,
    val monthOvertimeCount: Int,
    val monthGross: Double,
    val avgDailyNet: Double,
    val avgPerRecord: Double,
    val maxSingleHours: Double,
    val maxDailyHours: Double,
    val streakDays: Int,
    val weekNet: Double, val weekAmount: Double, val weekCount: Int,
    val rateBuckets: List<Pair<Double, Double>>,
    val last7: List<Pair<String, Double>>,
    val last6Months: List<Pair<String, Double>>
)

/** yyyyMMdd 整数键，用于按自然日聚合与连续天数计算 */
private fun dayKey(time: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = time }
    return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

/** yyyyMM 整数键，用于跨年月份聚合 */
private fun monthKey(time: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = time }
    return c.get(Calendar.YEAR) * 100 + (c.get(Calendar.MONTH) + 1)
}

private val WEEK_LABELS = arrayOf("日", "一", "二", "三", "四", "五", "六")

fun aggregateHome(records: List<OvertimeRecord>, month: String): Aggregate {
    val cal = Calendar.getInstance()
    val year = month.substringBefore("-").toIntOrNull() ?: cal.get(Calendar.YEAR)
    val mon = month.substringAfter("-").toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)
    val prevYear = if (mon == 1) year - 1 else year
    val prevMon = if (mon == 1) 12 else mon - 1
    val lastMonth = String.format(Locale.getDefault(), "%04d-%02d", prevYear, prevMon)

    var mNet = 0.0; var mCount = 0
    var lNet = 0.0; var lCount = 0; var lAmount = 0.0
    var mAmount = 0.0; var mLeaveCount = 0; var mLeaveDeduct = 0.0
    var yNet = 0.0; var yAmount = 0.0; var yCount = 0
    var wd = 0.0; var we = 0.0; var ho = 0.0; var lv = 0.0
    var amtWd = 0.0; var amtWe = 0.0; var amtHo = 0.0
    var gross = 0.0; var overtimeCount = 0
    var maxSingle = 0.0
    var weekNet = 0.0; var weekAmount = 0.0; var weekCount = 0

    // 本月每日净工时
    val dailyMap = LinkedHashMap<Int, Double>()
    val totalDays = run {
        val c = Calendar.getInstance().apply { clear(); set(year, mon - 1, 1) }
        c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    for (d in 1..totalDays) dailyMap[d] = 0.0
    // 本月 1 号是周几（转成「周一为第 0 列」的偏移）
    val firstOffset = run {
        val c = Calendar.getInstance().apply { clear(); set(year, mon - 1, 1) }
        (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
    // 本年每月净工时
    val monthMap = LinkedHashMap<Int, Double>()
    for (m in 1..12) monthMap[m] = 0.0
    // 按自然日 / 月 / 倍率聚合
    val dayTotals = HashMap<Int, Double>()
    val monthTotals = HashMap<Int, Double>()
    val rateTotals = LinkedHashMap<Double, Double>()

    val weekStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }.timeInMillis

    records.forEach { rec ->
        cal.timeInMillis = rec.date
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val dk = dayKey(rec.date)
        val mk = y * 100 + m

        dayTotals[dk] = (dayTotals[dk] ?: 0.0) + rec.durationHours
        monthTotals[mk] = (monthTotals[mk] ?: 0.0) + rec.durationHours

        if (y == year && m == mon) {
            mNet += rec.durationHours
            mAmount += rec.amount
            mCount++
            dailyMap[d] = (dailyMap[d] ?: 0.0) + rec.durationHours
            if (rec.isLeave) {
                mLeaveCount++
                mLeaveDeduct += abs(rec.amount)
                lv += rec.durationHours
            } else {
                gross += rec.durationHours
                overtimeCount++
                maxSingle = maxOf(maxSingle, rec.durationHours)
                val bucket = (rec.rate * 100).toInt() / 100.0
                rateTotals[bucket] = (rateTotals[bucket] ?: 0.0) + rec.amount
                when (rec.type) {
                    OvertimeType.WORKDAY -> { wd += rec.durationHours; amtWd += rec.amount }
                    OvertimeType.WEEKEND -> { we += rec.durationHours; amtWe += rec.amount }
                    OvertimeType.HOLIDAY -> { ho += rec.durationHours; amtHo += rec.amount }
                }
            }
        } else if ((y == prevYear && m == prevMon)) {
            lNet += rec.durationHours
            lAmount += rec.amount
            lCount++
        }
        if (y == year) {
            yNet += rec.durationHours
            yAmount += rec.amount
            yCount++
            monthMap[m] = (monthMap[m] ?: 0.0) + rec.durationHours
        }
        if (rec.date >= weekStart) {
            weekNet += rec.durationHours
            weekAmount += rec.amount
            weekCount++
        }
    }

    val activeDays = dailyMap.values.count { abs(it) > 0.0001 }
    val maxDaily = dailyMap.values.maxOfOrNull { abs(it) } ?: 0.0

    // 连续有记录天数：从今天往前逐日回退（今天无记录时从昨天起算）
    var streak = 0
    val cursor = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    if (!dayTotals.containsKey(dayKey(cursor.timeInMillis))) cursor.add(Calendar.DAY_OF_MONTH, -1)
    while (dayTotals.containsKey(dayKey(cursor.timeInMillis)) && streak < 366) {
        streak++
        cursor.add(Calendar.DAY_OF_MONTH, -1)
    }

    // 近 7 天（含今天），标签为星期
    val last7 = ArrayList<Pair<String, Double>>(7)
    val c7 = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    c7.add(Calendar.DAY_OF_MONTH, -6)
    repeat(7) {
        last7.add(WEEK_LABELS[c7.get(Calendar.DAY_OF_WEEK) - 1] to (dayTotals[dayKey(c7.timeInMillis)] ?: 0.0))
        c7.add(Calendar.DAY_OF_MONTH, 1)
    }
    // 近 6 个月（含当前月），跨年回退
    val last6 = ArrayList<Pair<String, Double>>(6)
    val c6 = Calendar.getInstance().apply {
        clear(); set(Calendar.DAY_OF_MONTH, 1)
        set(year, mon - 1, 1)
    }
    c6.add(Calendar.MONTH, -5)
    repeat(6) {
        val k = c6.get(Calendar.YEAR) * 100 + (c6.get(Calendar.MONTH) + 1)
        last6.add("${c6.get(Calendar.MONTH) + 1}月" to (monthTotals[k] ?: 0.0))
        c6.add(Calendar.MONTH, 1)
    }

    return Aggregate(
        monthNet = mNet, monthCount = mCount,
        lastMonthNet = lNet, lastMonthCount = lCount, lastMonth = lastMonth,
        lastMonthAmount = lAmount,
        monthAmount = mAmount,
        yearNet = yNet, yearAmount = yAmount, yearCount = yCount,
        monthLeaveCount = mLeaveCount, monthLeaveDeduct = mLeaveDeduct,
        latest = records.maxByOrNull { it.date },
        typeWd = wd, typeWe = we, typeHo = ho, typeLeave = lv,
        typeAmountWd = amtWd, typeAmountWe = amtWe, typeAmountHo = amtHo,
        dailyNet = dailyMap.map { it.key to it.value },
        monthlyNet = monthMap.map { it.key to it.value },
        monthDays = totalDays,
        monthFirstDayOffset = firstOffset,
        activeDays = activeDays,
        monthOvertimeCount = overtimeCount,
        monthGross = gross,
        avgDailyNet = if (activeDays > 0) gross / activeDays else 0.0,
        avgPerRecord = if (overtimeCount > 0) gross / overtimeCount else 0.0,
        maxSingleHours = maxSingle,
        maxDailyHours = maxDaily,
        streakDays = streak,
        weekNet = weekNet, weekAmount = weekAmount, weekCount = weekCount,
        rateBuckets = rateTotals.entries.sortedByDescending { it.value }.map { it.key to it.value },
        last7 = last7,
        last6Months = last6
    )
}

/** 构建类型分段（工作日/休息日/节假日/请假），仅保留非零项 */
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

// ───────────────────────── 格式化 / 配色工具 ─────────────────────────

/** 小卡片专用紧凑时长：19h / 2h30m / 45m，避免「19小时30分钟」被省略号截断 */
fun widgetHours(hours: Double): String {
    val sign = if (hours < 0) "-" else ""
    val a = abs(hours)
    val h = a.toInt()
    val m = ((a - h) * 60).toInt()
    return when {
        h == 0 && m == 0 -> "0h"
        m == 0 -> "$sign${h}h"
        h == 0 -> "$sign${m}m"
        else -> "$sign${h}h${m}m"
    }
}

/** 金额超过万元折算成「x.x万」，避免 1x1 卡片里大字号溢出 */
fun widgetAmount(amount: Double): String {
    val a = abs(amount)
    return if (a >= 10000) {
        val sign = if (amount < 0) "-" else ""
        "$sign¥${String.format(Locale.getDefault(), "%.1f", a / 10000)}万"
    } else SalaryCalculator.formatAmount(amount)
}

private fun rateLabel(rate: Double): String =
    if (abs(rate - rate.toInt()) < 0.001) "${rate.toInt()}×" else "${rate}×"

@Composable
private fun resolveAccent(accent: WidgetAccent, typeColor: (OvertimeType) -> Color): Color =
    when (accent) {
        WidgetAccent.PRIMARY -> MiuixTheme.colorScheme.primary
        WidgetAccent.WORKDAY -> typeColor(OvertimeType.WORKDAY)
        WidgetAccent.WEEKEND -> typeColor(OvertimeType.WEEKEND)
        WidgetAccent.HOLIDAY -> typeColor(OvertimeType.HOLIDAY)
        WidgetAccent.DANGER -> MiuixTheme.colorScheme.error
        WidgetAccent.VIOLET -> Color(0xFF8B5CF6)
        WidgetAccent.NEUTRAL -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

/** 卡片右上角的时间范围徽标，给所有卡片一个统一的「数据口径」提示 */
private fun widgetBadge(id: String, agg: Aggregate, month: String): String = when {
    id.startsWith("week_") -> "本周"
    id == "last7_trend" -> "近 7 天"
    id == "last6_month" -> "近半年"
    id == "year_month" || id == "year_total" || id == "year_count" -> "${month.substringBefore("-")} 年"
    id == "last_month_hours" -> "${agg.lastMonth.substringAfter("-").toIntOrNull()} 月"
    id == "month_compare" -> "环比"
    id == "latest_record" || id == "streak_days" -> "至今"
    else -> "${month.substringAfter("-").toIntOrNull()} 月"
}

// ───────────────────────── 数值卡数据 ─────────────────────────

/**
 * 数值卡的展示模型。所有 1x1 卡片共用同一套三段式布局，
 * 避免「有的卡只有数字、有的卡带长副标题」造成的密度不齐。
 */
private data class Metric(
    val value: String,
    val sub: String,
    val trend: Int = 0,             // 1 上升 / -1 下降 / 0 无趋势
    val progress: Float? = null,    // 底部迷你进度条
    val highlight: Boolean = false  // 主数值使用强调色而非普通文字色
)

private fun shortTypeLabel(type: OvertimeType): String = when (type) {
    OvertimeType.WORKDAY -> "工作日"
    OvertimeType.WEEKEND -> "休息日"
    OvertimeType.HOLIDAY -> "节假日"
}

private fun metricFor(id: String, agg: Aggregate): Metric = when (id) {
    "month_hours" -> {
        val diff = agg.monthNet - agg.lastMonthNet
        Metric(
            value = widgetHours(agg.monthNet),
            sub = when {
                agg.lastMonthNet == 0.0 && agg.monthNet == 0.0 -> "上月无记录"
                diff >= 0 -> "较上月 +${widgetHours(diff)}"
                else -> "较上月 -${widgetHours(-diff)}"
            },
            trend = when {
                abs(diff) < 0.0001 -> 0
                diff > 0 -> 1
                else -> -1
            },
            highlight = true
        )
    }
    "month_salary" -> Metric(widgetAmount(agg.monthAmount), "含请假扣款", highlight = true)
    "month_count" -> Metric(
        "${agg.monthCount}笔",
        "${agg.activeDays} 天有记录",
        progress = if (agg.monthDays > 0) agg.activeDays.toFloat() / agg.monthDays else null
    )
    "avg_daily" -> Metric(widgetHours(agg.avgDailyNet), "有记录日均", highlight = true)
    "avg_record" -> Metric(widgetHours(agg.avgPerRecord), "共 ${agg.monthOvertimeCount} 笔")
    "max_single" -> Metric(widgetHours(agg.maxSingleHours), "本月单笔峰值")
    "max_daily" -> Metric(widgetHours(agg.maxDailyHours), "本月单日峰值")
    "streak_days" -> Metric("${agg.streakDays}天", "连续有记录")
    "week_hours" -> Metric(widgetHours(agg.weekNet), "${agg.weekCount} 笔记录", highlight = true)
    "week_salary" -> Metric(widgetAmount(agg.weekAmount), "本周预估", highlight = true)
    "last_month_hours" -> Metric(widgetHours(agg.lastMonthNet), "${agg.lastMonthCount} 笔记录")
    "year_total" -> Metric(widgetHours(agg.yearNet), widgetAmount(agg.yearAmount), highlight = true)
    "year_count" -> Metric("${agg.yearCount}笔", "全年累计")
    "month_leave" -> Metric(
        "${agg.monthLeaveCount}次",
        if (agg.monthLeaveCount > 0) "扣款 ${widgetAmount(agg.monthLeaveDeduct)}" else "本月无请假",
        highlight = agg.monthLeaveCount > 0
    )
    "latest_record" -> {
        val latest = agg.latest
        if (latest == null) Metric("暂无记录", "去记录页添加")
        else {
            val c = Calendar.getInstance().apply { timeInMillis = latest.date }
            Metric(
                if (latest.isLeave) "请假" else shortTypeLabel(latest.type),
                "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)} · ${widgetHours(abs(latest.durationHours))}"
            )
        }
    }
    "type_workday" -> typeMetric(agg.typeWd, agg.monthGross, "工作日")
    "type_weekend" -> typeMetric(agg.typeWe, agg.monthGross, "休息日")
    "type_holiday" -> typeMetric(agg.typeHo, agg.monthGross, "节假日")
    else -> Metric("--", "")
}

private fun typeMetric(hours: Double, gross: Double, label: String): Metric {
    val pct = if (gross > 0.0001) (hours / gross * 100).toInt() else 0
    return Metric(
        value = widgetHours(hours),
        sub = "$label · 占 $pct%",
        progress = if (gross > 0.0001) (hours / gross).toFloat().coerceIn(0f, 1f) else null,
        highlight = true
    )
}

// ───────────────────────── 卡片内容渲染 ─────────────────────────

/**
 * 单个组件的内容（不含卡片外壳）。首页与「添加」预览页共用，保证「预览即所见」。
 *
 * 统一四层结构：
 *  1. [WidgetHeader]：accent 色点 + 半粗标题 + 右侧口径徽标
 *  2. 主体区：数值卡走 [MetricContent]，图表卡走各自的图表
 *  3. 数值卡底部可选迷你进度条
 *  4. 2x2 图表底部统一 [ChartScaffold] 摘要条
 */
@Composable
fun WidgetBody(
    id: String,
    agg: Aggregate,
    month: String,
    typeColor: (OvertimeType) -> Color,
    modifier: Modifier = Modifier
) {
    val def = widgetDef(id)
    val accent = resolveAccent(def?.accent ?: WidgetAccent.NEUTRAL, typeColor)
    Column(modifier.fillMaxSize()) {
        WidgetHeader(
            title = widgetTitle(id),
            accent = accent,
            badge = widgetBadge(id, agg, month)
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                id in METRIC_IDS -> MetricContent(id, agg, accent)
                id == "month_type" -> TypeBarChart(buildTypeSegments(agg, typeColor))
                id == "month_daily" -> ColumnBars(
                    agg.dailyNet.mapIndexed { i, (d, v) ->
                        BarEntry("$d", v, highlight = d == Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
                    },
                    accent,
                    labelStride = 5
                )
                id == "last7_trend" -> ColumnBars(
                    agg.last7.mapIndexed { i, (label, v) -> BarEntry(label, v, highlight = i == 6) },
                    accent
                )
                id == "last6_month" -> ColumnBars(
                    agg.last6Months.mapIndexed { i, (label, v) -> BarEntry(label, v, highlight = i == 5) },
                    accent
                )
                id == "rate_split" -> RateSplitChart(agg.rateBuckets, accent)
                id == "type_amount" -> TypeAmountChart(agg, typeColor)
                id == "month_compare" -> MonthCompareChart(agg, accent)
                id == "type_donut" -> DonutChart(
                    buildTypeSegments(agg, typeColor),
                    agg.typeWd + agg.typeWe + agg.typeHo + agg.typeLeave,
                    accent
                )
                id == "year_month" -> ChartScaffold(
                    summary = listOf(
                        "全年" to widgetHours(agg.yearNet),
                        "月薪" to widgetAmount(agg.yearAmount),
                        "记录" to "${agg.yearCount}笔"
                    )
                ) {
                    ColumnBars(
                        agg.monthlyNet.map { (m, v) ->
                            BarEntry("$m", v, highlight = m == (month.substringAfter("-").toIntOrNull() ?: 1))
                        },
                        accent
                    )
                }
                id == "calendar_heatmap" -> ChartScaffold(
                    summary = listOf(
                        "合计" to widgetHours(agg.monthNet),
                        "有记录" to "${agg.activeDays}天",
                        "峰值" to widgetHours(agg.maxDailyHours)
                    )
                ) {
                    CalendarHeatmap(agg, accent)
                }
                id == "month_summary" -> ChartScaffold(
                    summary = listOf(
                        "时长" to widgetHours(agg.monthNet),
                        "薪资" to widgetAmount(agg.monthAmount),
                        "日均" to widgetHours(agg.avgDailyNet)
                    )
                ) {
                    MonthSummary(agg, accent)
                }
                else -> EmptyChartHint("暂无数据")
            }
        }
    }
}

/** 统一的标题行：色点 + 标题 + 右侧数据口径，形成卡片视觉锚点 */
@Composable
private fun WidgetHeader(title: String, accent: Color, badge: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(AppShapes.pill)
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = badge,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** 数值卡主体：主数值占满剩余高度，底部固定胶囊 + 可选进度条 */
@Composable
private fun MetricContent(id: String, agg: Aggregate, accent: Color) {
    val m = metricFor(id, agg)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            val base = AppHeadline2Bold()
            val sizeSp = minOf(base.fontSize.value, maxHeight.value * 0.70f).sp
            Text(
                text = m.value,
                style = base.copy(fontSize = sizeSp),
                color = if (m.highlight) accent else MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (m.progress != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(AppShapes.radiusXs)
                    .background(MiuixTheme.colorScheme.secondaryContainer)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(m.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(AppShapes.radiusXs)
                        .background(accent)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        MetricSub(m, accent)
    }
}

/** 底部说明胶囊：涨跌用箭头 + 淡色底，长文本在胶囊内单行省略，绝不越界 */
@Composable
private fun MetricSub(m: Metric, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.radiusSm)
            .background(
                if (m.trend > 0) accent.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (m.trend != 0) {
            Text(
                text = if (m.trend > 0) "↑" else "↓",
                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                color = if (m.trend > 0) accent else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = m.sub,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 2x2 图表统一骨架：图表区占满，底部一条三指标摘要，消除大片留白 */
@Composable
private fun ChartScaffold(
    summary: List<Pair<String, String>>,
    content: @Composable BoxScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.radiusMd)
                .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            summary.forEach { (label, value) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = value,
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = label,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ───────────────────────── 图表：通用柱状 ─────────────────────────

/**
 * 带网格线、零值轨道、峰值刻度与当前项高亮的通用柱状图。
 * daily / last7 / last6 / year 四种 2x1 / 2x2 组件共用，保证图表语言一致。
 */
@Composable
private fun ColumnBars(
    entries: List<BarEntry>,
    accent: Color,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    labelStride: Int = 1
) {
    val maxAbs = entries.maxOfOrNull { abs(it.value) }?.coerceAtLeast(0.001) ?: 0.001
    val gridColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)
    val trackColor = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    val subtle = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "峰值 ${widgetHours(maxAbs)}",
                style = MiuixTheme.textStyles.footnote1,
                color = subtle,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "${entries.count { abs(it.value) > 0.0001 }} 项有数据",
                style = MiuixTheme.textStyles.footnote1,
                color = subtle,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 1..3) {
                    val y = size.height * i / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                }
                drawLine(gridColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                entries.forEachIndexed { idx, e ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(trackColor, AppShapes.radiusXs)
                            )
                            val frac = if (abs(e.value) > 0.0001)
                                (abs(e.value) / maxAbs).toFloat().coerceIn(0.05f, 1f) else 0f
                            if (frac > 0f) {
                                val fill = when {
                                    e.value < 0 -> MiuixTheme.colorScheme.error
                                    e.highlight -> accent
                                    else -> accent.copy(alpha = 0.55f)
                                }
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .fillMaxHeight(frac)
                                        .background(fill, AppShapes.radiusXs)
                                )
                            }
                        }
                        if (showLabels) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (labelStride <= 1 || idx % labelStride == 0) e.label else "",
                                style = MiuixTheme.textStyles.footnote1,
                                color = if (e.highlight) accent else subtle,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── 图表：2x1 ─────────────────────────

/** 图例单项：色块 + 名称 + 数值，紧凑单行 */
@Composable
private fun LegendItem(seg: TypeSeg, total: Double, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(AppShapes.pill).background(seg.color))
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
        val pct = if (total > 0.0001) (seg.hours / total * 100).toInt() else 0
        Text(
            text = "${widgetHours(seg.hours)}·$pct%",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End
        )
    }
}

/** 类型分布：堆叠占比条 + 两列图例（零工时类型隐藏） */
@Composable
private fun TypeBarChart(segs: List<TypeSeg>) {
    if (segs.isEmpty()) {
        EmptyChartHint("本月暂无加班")
        return
    }
    val total = segs.sumOf { it.hours }
    val subtle = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("合计 ${widgetHours(total)}", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurface, maxLines = 1)
            Text("${segs.size} 类", style = MiuixTheme.textStyles.footnote1, color = subtle, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(AppShapes.radiusSm)
                .background(MiuixTheme.colorScheme.secondaryContainer)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                segs.forEach { seg ->
                    val frac = (seg.hours / total).toFloat().coerceIn(0.02f, 1f)
                    Box(
                        Modifier
                            .weight(frac)
                            .fillMaxHeight()
                            .clip(AppShapes.radiusXs)
                            .background(seg.color)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        segs.chunked(2).forEach { rowSegs ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowSegs.forEach { seg -> LegendItem(seg, total, Modifier.weight(1f)) }
                if (rowSegs.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 横向对比条：倍率 / 类型薪资共用 */
@Composable
private fun HorizontalBars(
    rows: List<Triple<String, Double, Color>>,
    valueText: (Double) -> String,
    emptyText: String
) {
    if (rows.isEmpty()) {
        EmptyChartHint(emptyText)
        return
    }
    val maxAbs = rows.maxOf { abs(it.second) }.coerceAtLeast(0.001)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        rows.take(4).forEach { (label, value, color) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(46.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(AppShapes.radiusSm)
                        .background(MiuixTheme.colorScheme.secondaryContainer)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((abs(value) / maxAbs).toFloat().coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(AppShapes.radiusSm)
                            .background(color)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = valueText(value),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(74.dp)
                )
            }
        }
    }
}

@Composable
private fun RateSplitChart(buckets: List<Pair<Double, Double>>, accent: Color) {
    HorizontalBars(
        rows = buckets.map { (rate, amount) -> Triple(rateLabel(rate), amount, accent) },
        valueText = { widgetAmount(it) },
        emptyText = "本月暂无加班薪资"
    )
}

@Composable
private fun TypeAmountChart(agg: Aggregate, typeColor: (OvertimeType) -> Color) {
    val rows = listOf(
        Triple("工作日", agg.typeAmountWd, typeColor(OvertimeType.WORKDAY)),
        Triple("休息日", agg.typeAmountWe, typeColor(OvertimeType.WEEKEND)),
        Triple("节假日", agg.typeAmountHo, typeColor(OvertimeType.HOLIDAY))
    ).filter { abs(it.second) > 0.0001 }
    HorizontalBars(rows, { widgetAmount(it) }, "本月暂无加班薪资")
}

/** 本月 vs 上月：时长 / 薪资双条对比 + 差异胶囊 */
@Composable
private fun MonthCompareChart(agg: Aggregate, accent: Color) {
    val subtle = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val maxHours = maxOf(abs(agg.monthNet), abs(agg.lastMonthNet), 0.001)
    val maxAmount = maxOf(abs(agg.monthAmount), abs(agg.lastMonthAmount), 0.001)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        CompareRow("时长", widgetHours(agg.monthNet), widgetHours(agg.lastMonthNet),
            (abs(agg.monthNet) / maxHours).toFloat(), (abs(agg.lastMonthNet) / maxHours).toFloat(), accent, subtle)
        CompareRow("薪资", widgetAmount(agg.monthAmount), widgetAmount(agg.lastMonthAmount),
            (abs(agg.monthAmount) / maxAmount).toFloat(), (abs(agg.lastMonthAmount) / maxAmount).toFloat(), accent, subtle)
        val diff = agg.monthNet - agg.lastMonthNet
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.radiusSm)
                .background(accent.copy(alpha = 0.10f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    diff >= 0 -> "↑ 较上月多 ${widgetHours(diff)}"
                    else -> "↓ 较上月少 ${widgetHours(-diff)}"
                },
                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                color = accent,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompareRow(
    label: String,
    current: String,
    previous: String,
    currentFrac: Float,
    previousFrac: Float,
    accent: Color,
    subtle: Color
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = subtle,
            maxLines = 1,
            modifier = Modifier.width(32.dp)
        )
        Column(Modifier.weight(1f)) {
            val track = MiuixTheme.colorScheme.secondaryContainer
            Box(Modifier.fillMaxWidth().height(9.dp).clip(AppShapes.radiusSm).background(track)) {
                Box(Modifier.fillMaxWidth(currentFrac.coerceIn(0.02f, 1f)).fillMaxHeight()
                    .clip(AppShapes.radiusSm).background(accent))
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(9.dp).clip(AppShapes.radiusSm).background(track)) {
                Box(Modifier.fillMaxWidth(previousFrac.coerceIn(0.02f, 1f)).fillMaxHeight()
                    .clip(AppShapes.radiusSm).background(accent.copy(alpha = 0.35f)))
            }
        }
        Spacer(Modifier.width(8.dp))
        // 2x1 内容区有限：右侧只保留一行「本月 / 上月」紧凑数值，长文本单行省略
        Text(
            text = "$current / $previous",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.width(96.dp)
        )
    }
}

// ───────────────────────── 图表：2x2 ─────────────────────────

/** 类型占比：左侧带轨道环的环形图 + 右侧填满的占比明细，底部摘要条 */
@Composable
private fun DonutChart(segs: List<TypeSeg>, net: Double, accent: Color) {
    if (segs.isEmpty()) {
        EmptyChartHint("暂无数据")
        return
    }
    val sum = segs.sumOf { it.hours }
    ChartScaffold(
        summary = listOf(
            "净时长" to widgetHours(net),
            "分类" to "${segs.size} 类",
            "最高" to (segs.maxByOrNull { it.hours }?.label ?: "--")
        )
    ) {
        val track = MiuixTheme.colorScheme.secondaryContainer
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val min = size.minDimension
                    val stroke = min * 0.16f
                    val inset = stroke / 2f
                    val arc = Size(min - stroke, min - stroke)
                    val topLeft = Offset((size.width - arc.width) / 2f, (size.height - arc.height) / 2f)
                    drawArc(
                        color = track, startAngle = 0f, sweepAngle = 360f,
                        useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke)
                    )
                    var start = -90f
                    segs.forEach { seg ->
                        val sweep = ((seg.hours / sum) * 360f).toFloat()
                        drawArc(
                            color = seg.color,
                            startAngle = start + 1.6f,
                            sweepAngle = (sweep - 3.2f).coerceAtLeast(0.6f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arc,
                            style = Stroke(stroke)
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = widgetHours(net),
                        style = AppHeadline2Bold(),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "净时长",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                segs.take(4).forEach { seg ->
                    val pct = (seg.hours / sum * 100).toInt()
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(AppShapes.pill).background(seg.color))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = seg.label,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$pct%",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.fillMaxWidth().height(5.dp)
                                .clip(AppShapes.radiusXs)
                                .background(MiuixTheme.colorScheme.secondaryContainer)
                        ) {
                            Box(
                                Modifier.fillMaxWidth((seg.hours / sum).toFloat().coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(AppShapes.radiusXs)
                                    .background(seg.color)
                            )
                        }
                    }
                }
                if (segs.size < 4) {
                    Text(
                        text = "共 ${widgetHours(sum)} 加班",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 本月日历热力图：周一为首列，色深表示单日净工时 */
@Composable
private fun CalendarHeatmap(agg: Aggregate, accent: Color) {
    val days = agg.dailyNet
    val maxAbs = days.maxOfOrNull { abs(it.second) }?.coerceAtLeast(0.001) ?: 0.001
    val offset = agg.monthFirstDayOffset
    val rows = (offset + days.size + 6) / 7
    val subtle = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val emptyCell = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                Text(
                    text = d,
                    style = MiuixTheme.textStyles.footnote1,
                    color = subtle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        repeat(rows) { r ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(7) { c ->
                    val idx = r * 7 + c - offset
                    val net = if (idx in 1..days.size) days[idx - 1].second else null
                    val cellColor = when {
                        net == null -> Color.Transparent
                        abs(net) < 0.0001 -> emptyCell
                        net < 0 -> MiuixTheme.colorScheme.error.copy(alpha = 0.7f)
                        else -> accent.copy(alpha = 0.22f + 0.78f * (net / maxAbs).toFloat().coerceIn(0f, 1f))
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(AppShapes.radiusXs)
                            .background(cellColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (net != null) {
                            Text(
                                text = "$idx",
                                style = MiuixTheme.textStyles.footnote1,
                                color = if (abs(net) / maxAbs > 0.55f)
                                    MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("少", style = MiuixTheme.textStyles.footnote1, color = subtle, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { a ->
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(AppShapes.radiusXs)
                        .background(accent.copy(alpha = 0.15f + 0.85f * a))
                )
                Spacer(Modifier.width(4.dp))
            }
            Text("多", style = MiuixTheme.textStyles.footnote1, color = subtle, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(
                text = "峰值 ${widgetHours(maxAbs)}",
                style = MiuixTheme.textStyles.footnote1,
                color = subtle,
                maxLines = 1
            )
        }
    }
}

/** 月度综合：2x2 四项关键指标 + 底部近 7 天迷你趋势 */
@Composable
private fun MonthSummary(agg: Aggregate, accent: Color) {
    val subtle = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile("净时长", widgetHours(agg.monthNet), accent, Modifier.weight(1f))
            SummaryTile("预估薪资", widgetAmount(agg.monthAmount), accent, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile("记录笔数", "${agg.monthCount} 笔", accent, Modifier.weight(1f))
            SummaryTile("有记录天", "${agg.activeDays} 天", accent, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.radiusMd)
                .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("近 7 天", style = MiuixTheme.textStyles.footnote1, color = subtle, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            val maxAbs = agg.last7.maxOfOrNull { abs(it.second) }?.coerceAtLeast(0.001) ?: 0.001
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                agg.last7.forEach { (_, v) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(18.dp)
                            .clip(AppShapes.radiusXs)
                            .background(MiuixTheme.colorScheme.secondaryContainer)
                    ) {
                        val frac = if (abs(v) > 0.0001) (abs(v) / maxAbs).toFloat().coerceIn(0.15f, 1f) else 0f
                        if (frac > 0f) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .fillMaxHeight(frac)
                                    .clip(AppShapes.radiusXs)
                                    .background(accent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(AppShapes.radiusMd)
            .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold),
            color = accent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyChartHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(AppShapes.radiusMd)
                .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(AppShapes.pill).background(MiuixTheme.colorScheme.onSurfaceVariantSummary))
            Spacer(Modifier.width(8.dp))
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
