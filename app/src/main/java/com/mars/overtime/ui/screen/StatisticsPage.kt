package com.mars.overtime.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.util.HolidayDataSource
import com.mars.overtime.util.HolidayManager
import com.mars.overtime.util.SalaryCalculator
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppShapes
import com.mars.overtime.ui.theme.AppTitle3Semibold
import com.mars.overtime.ui.component.EmptyState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private data class DayAggregate(
    // 净时长：加班为正、请假为负，二者相抵（如 加班2 + 请假-4 = -2）
    var netHours: Double = 0.0,
    // 当天加班累计（仅正向加班，用于类型判定/展示）
    var overtimeHours: Double = 0.0,
    var type: OvertimeType = OvertimeType.WORKDAY,
    var isLeave: Boolean = false,
    var maxAbs: Double = 0.0
)

private enum class StatsViewMode { MONTH, YEAR }

/**
 * 统计页「阅读视图」（月度日历卡片）中每个日期格子的三类属性。
 * 与 HolidayManager 的统一档位一一对应：0=工作日 / 1、2=休息日 / 3=法定节假日。
 */
private enum class DayCategory(val label: String) {
    WORKDAY("工作日"),
    REST_DAY("休息日"),
    HOLIDAY("节假日")
}

/** 三类底色直接使用外观设置里配置的类型色（实色），不再做透明度淡化。 */

/**
 * 整月日期分类。显示范围：仅 selectedMonth 的 1..当月天数（28 / 29 / 30 / 31，含闰年 2/29）。
 *
 * 分类依据（优先级由高到低）：
 *  1. 设置「忽略节假日」= true：完全不读节假日缓存，只按星期判定；
 *  2. HolidayManager 缓存命中：按其统一档位映射（与 infoToType / 添加记录页的自动判定
 *     同口径）——0 工作日（含调休上班日）、1/2 休息日、
 *     3 法定节假日（整个法定休假区间都归此类，即使落在休息日）；
 *  3. 缓存未命中（未拉取 / 拉取失败 / 该年无数据 / 异步完成前）：按 GMT+8 本地星期兜底，
 *     周六周日=休息日，其余=工作日；此时无法识别法定节假日与调休上班日。
 * 星期口径固定 GMT+8 / Locale.CHINA，与 HolidayManager.isWeekend 一致。
 */
private fun buildDayCategories(selectedMonth: String, ignoreHoliday: Boolean): Map<Int, DayCategory> {
    val parts = selectedMonth.split("-")
    val year = parts[0].toInt()
    val month = parts[1].toInt()
    val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA).apply {
        clear()
        set(year, month - 1, 1)
    }
    val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val result = HashMap<Int, DayCategory>(totalDays)
    for (day in 1..totalDays) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val offline = if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
            DayCategory.REST_DAY
        } else {
            DayCategory.WORKDAY
        }
        val key = String.format(Locale.CHINA, "%04d-%02d-%02d", year, month, day)
        val info = if (ignoreHoliday) null else HolidayManager.getDayInfo(key)
        result[day] = if (info == null) offline else when (info.type) {
            0 -> DayCategory.WORKDAY
            1, 2 -> DayCategory.REST_DAY
            3 -> DayCategory.HOLIDAY
            else -> if (info.isHoliday) DayCategory.REST_DAY else DayCategory.WORKDAY
        }
    }
    return result
}

@Composable
fun StatisticsPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository,
    topReserve: Dp = 0.dp,
    bottomReserve: Dp = 0.dp
) {
    // 全部记录：用于动态计算可统计的月份范围（最早记录月 ~ 当前月）
    val allRecords by repository.getAllRecords().collectAsState(initial = emptyList())

    val yearMonths by remember(allRecords) {
        derivedStateOf {
            val cal = Calendar.getInstance()
            val endY = cal.get(Calendar.YEAR)
            val endM = cal.get(Calendar.MONTH) + 1
            val startYM = if (allRecords.isEmpty()) {
                String.format("%04d-%02d", endY, endM)
            } else {
                val minCal = Calendar.getInstance().apply { timeInMillis = allRecords.minOf { it.date } }
                String.format("%04d-%02d", minCal.get(Calendar.YEAR), minCal.get(Calendar.MONTH) + 1)
            }
            val list = mutableListOf<String>()
            var y = startYM.substring(0, 4).toInt()
            var m = startYM.substring(5, 7).toInt()
            while (y < endY || (y == endY && m <= endM)) {
                list.add(String.format("%04d-%02d", y, m))
                m++
                if (m > 12) { m = 1; y++ }
            }
            list
        }
    }

    var selectedMonth by remember { mutableStateOf(SalaryCalculator.getCurrentYearMonth()) }
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var viewMode by remember { mutableStateOf(StatsViewMode.MONTH) }
    var stats by remember { mutableStateOf<com.mars.overtime.data.repository.MonthlyStats?>(null) }
    var yearlyStats by remember { mutableStateOf<com.mars.overtime.data.repository.YearlyStats?>(null) }
    var monthRecords by remember { mutableStateOf<List<OvertimeRecord>>(emptyList()) }

    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())

    // 日期底色与「添加记录」页的自动类型判定同源：读取同一份节假日配置，
    // 复用 HolidayManager 的缓存 / 拉取结果，保证两页对同一天给出相同结论。
    val holidayDataSource by settingsRepository.holidayDataSource
        .collectAsState(initial = HolidayDataSource.UAPIS.name)
    val holidayCustomUrl by settingsRepository.holidayCustomUrl.collectAsState(initial = "")
    val holidayMxnzpAppId by settingsRepository.holidayMxnzpAppId.collectAsState(initial = "")
    val holidayMxnzpAppSecret by settingsRepository.holidayMxnzpAppSecret.collectAsState(initial = "")
    val holidayIgnoreHoliday by settingsRepository.holidayIgnoreHoliday.collectAsState(initial = false)

    // 拉取成功后 +1 触发重算；同一年份只尝试一次，避免缓存为空时切月反复发请求
    var holidayRevision by remember { mutableIntStateOf(0) }
    val attemptedYears = remember { mutableSetOf<String>() }
    val dayCategories: Map<Int, DayCategory> = remember(
        selectedMonth, holidayIgnoreHoliday, holidayRevision
    ) {
        buildDayCategories(selectedMonth, holidayIgnoreHoliday)
    }

    // 三类底色取外观设置配置的类型色，但统一压到 14% 透明度 —— 全月满铺时不再是
    // 一片饱和色块，仅保留「工作日 / 休息日 / 节假日」的轻微色相区分，弱化整月花感；
    // 与「类型占比 / 图例」仍然同源同色。（不另写色块说明文字，避免冗余信息。）
    val dayBackground: (DayCategory) -> Color = { category ->
        val alpha = 0.14f
        when (category) {
            DayCategory.WORKDAY -> Color(typeColorWorkday).copy(alpha = alpha)
            DayCategory.REST_DAY -> Color(typeColorWeekend).copy(alpha = alpha)
            DayCategory.HOLIDAY -> Color(typeColorHoliday).copy(alpha = alpha)
        }
    }

    // 只补一次整年缓存：先查该年是否已有数据，没有才拉取；失败时保持本地星期兜底，
    // 不逐日重试（逐日 getOvertimeType 在缓存为空时会触发 N 次失败请求）。
    LaunchedEffect(
        selectedMonth, holidayDataSource, holidayCustomUrl,
        holidayMxnzpAppId, holidayMxnzpAppSecret, holidayIgnoreHoliday
    ) {
        val source = try {
            HolidayDataSource.valueOf(holidayDataSource)
        } catch (_: Exception) {
            HolidayDataSource.UAPIS
        }
        HolidayManager.configure(
            source, holidayCustomUrl, holidayMxnzpAppId,
            holidayMxnzpAppSecret, holidayIgnoreHoliday
        )
        if (holidayIgnoreHoliday) return@LaunchedEffect
        val year = selectedMonth.substring(0, 4)
        if (HolidayManager.getDayInfo("$year-01-01") == null && year !in attemptedYears) {
            attemptedYears.add(year)
            if (HolidayManager.fetchHolidays(year)) holidayRevision++
        }
    }

    // 日期详情弹窗：选中的日期及当天记录
    var showDayDetail by remember { mutableStateOf(false) }
    var detailDay by remember { mutableStateOf<Int?>(null) }
    val detailRecords: List<OvertimeRecord> = remember(detailDay, monthRecords) {
        val day = detailDay ?: return@remember emptyList()
        monthRecords.filter { r ->
            val cal = Calendar.getInstance().apply { timeInMillis = r.date }
            cal.get(Calendar.DAY_OF_MONTH) == day
        }
    }

    // 月度视图：加载当月数据和年度统计
    LaunchedEffect(selectedMonth, viewMode) {
        if (viewMode == StatsViewMode.MONTH) {
            stats = repository.getMonthlyStats(selectedMonth)
            val year = selectedMonth.split("-")[0]
            yearlyStats = repository.getYearlyStats(year)
            monthRecords = repository.getMonthRecords(selectedMonth)
        }
    }

    // 年度视图：加载全年统计
    LaunchedEffect(selectedYear, viewMode) {
        if (viewMode == StatsViewMode.YEAR) {
            yearlyStats = repository.getYearlyStats(selectedYear)
        }
    }

    fun shiftMonth(delta: Int) {
        val parts = selectedMonth.split("-")
        var y = parts[0].toInt()
        var m = parts[1].toInt()
        m += delta
        if (m < 1) { m = 12; y -= 1 }
        if (m > 12) { m = 1; y += 1 }
        val next = String.format("%04d-%02d", y, m)
        if (next in yearMonths) {
            selectedMonth = next
        } else if (delta < 0 && next > yearMonths.last()) {
            selectedMonth = yearMonths.last()
        } else if (delta > 0 && next < yearMonths.first()) {
            selectedMonth = yearMonths.first()
        }
    }

    fun shiftYear(delta: Int) {
        val y = selectedYear.toIntOrNull() ?: return
        val next = (y + delta).toString()
        selectedYear = next
    }

    // 按日聚合当月记录，用于日历显示
    val dayAgg = remember(monthRecords) {
        val map = mutableMapOf<Int, DayAggregate>()
        for (r in monthRecords) {
            val cal = Calendar.getInstance().apply { time = Date(r.date) }
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val agg = map.getOrPut(day) { DayAggregate() }
            // 净时长按有符号累加：加班为正、请假为负，两者相抵（加班2 + 请假-4 = -2）
            agg.netHours += r.durationHours
            if (!r.isLeave && r.durationHours > 0) {
                agg.overtimeHours += r.durationHours
            }
            // 类型/颜色以当天绝对值最大的记录为准
            val a = abs(r.durationHours)
            if (a >= agg.maxAbs) {
                agg.maxAbs = a
                agg.type = r.type
                agg.isLeave = r.isLeave
            }
        }
        map
    }

    val typeColor: (OvertimeType) -> Color = { type ->
        when (type) {
            OvertimeType.WORKDAY -> Color(typeColorWorkday)
            OvertimeType.WEEKEND -> Color(typeColorWeekend)
            OvertimeType.HOLIDAY -> Color(typeColorHoliday)
        }
    }

    // 年度维度：按月趋势 + 类型占比
    val yearTrend by remember(allRecords, selectedYear) {
        derivedStateOf {
            val y = selectedYear.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val cal = Calendar.getInstance()
            (1..12).map { m ->
                val sum = allRecords.filter { rec ->
                    cal.timeInMillis = rec.date
                    cal.get(Calendar.YEAR) == y && cal.get(Calendar.MONTH) + 1 == m
                }.sumOf { it.durationHours }
                m to sum
            }
        }
    }
    val yearTypeRatio by remember(allRecords, selectedYear) {
        derivedStateOf {
            val y = selectedYear.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val cal = Calendar.getInstance()
            var workday = 0.0; var weekend = 0.0; var holiday = 0.0
            allRecords.filter { rec ->
                cal.timeInMillis = rec.date
                cal.get(Calendar.YEAR) == y && !rec.isLeave
            }.forEach { rec ->
                when (rec.type) {
                    OvertimeType.WORKDAY -> workday += rec.durationHours
                    OvertimeType.WEEKEND -> weekend += rec.durationHours
                    OvertimeType.HOLIDAY -> holiday += rec.durationHours
                }
            }
            Triple(workday, weekend, holiday)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.pageHorizontal,
            top = AppDimens.pageVertical + topReserve,
            end = AppDimens.pageHorizontal,
            bottom = AppDimens.pageVertical + bottomReserve
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.groupSpacing)
    ) {
        // 顶部日期导航（点击切换月/年视图）
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = AppDimens.cardCorner
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (viewMode == StatsViewMode.MONTH) shiftMonth(-1) else shiftYear(-1)
                    }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "上${if (viewMode == StatsViewMode.MONTH) "一月" else "一年"}")
                    }

                    // 可点击的日期标题：切换月/年视图。改用 MIUIX Surface(onClick)
                    // 获得框架标准按压反馈，替代无反馈的裸 clickable
                    Surface(
                        onClick = {
                            viewMode = if (viewMode == StatsViewMode.MONTH) StatsViewMode.YEAR else StatsViewMode.MONTH
                        },
                        modifier = Modifier.weight(1f),
                        color = Color.Transparent,
                        shape = AppShapes.radiusMd
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (viewMode == StatsViewMode.MONTH) {
                                    val parts = selectedMonth.split("-")
                                    "${parts[0]}年${parts[1]}月"
                                } else {
                                    "${selectedYear}年"
                                },
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (viewMode == StatsViewMode.MONTH) "点击切换年度视图" else "点击切换月度视图",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }

                    IconButton(onClick = {
                        if (viewMode == StatsViewMode.MONTH) shiftMonth(1) else shiftYear(1)
                    }) {
                        Icon(MiuixIcons.ChevronForward, contentDescription = "下${if (viewMode == StatsViewMode.MONTH) "一月" else "一年"}")
                    }
                }
            }
        }

        // 图例：类型颜色说明（记录文字 / 色条），月/年视图都显示。
        // 「日期底色」图例已移除：底色与类型图例同源同色，单独一行属于重复信息。
        item {
            LegendRow(typeColor = typeColor)
        }

        // 月度视图专属：日历卡片。
        // 年度视图没有日历格子，不做任何日期着色。
        if (viewMode == StatsViewMode.MONTH) {
            item {
                CalendarCard(
                    selectedMonth = selectedMonth,
                    dayAgg = dayAgg,
                    typeColor = typeColor,
                    dayCategories = dayCategories,
                    dayBackground = dayBackground,
                    onDayClick = { day ->
                        detailDay = day
                        showDayDetail = true
                    }
                )
            }
        }

        // 月度统计
        if (viewMode == StatsViewMode.MONTH) {
            item {
                StatsCard(
                    title = "$selectedMonth 统计",
                    items = listOf(
                        "总时长" to (stats?.let { SalaryCalculator.formatHours(it.totalHours) } ?: "0小时"),
                        "预估薪资" to (stats?.let { SalaryCalculator.formatAmount(it.totalAmount) } ?: "¥0.00"),
                        "记录数" to "${stats?.recordCount ?: 0}条",
                        "工作日加班" to (stats?.let { SalaryCalculator.formatHours(it.workdayHours) } ?: "0小时"),
                        "休息日加班" to (stats?.let { SalaryCalculator.formatHours(it.weekendHours) } ?: "0小时"),
                        "节假日加班" to (stats?.let { SalaryCalculator.formatHours(it.holidayHours) } ?: "0小时")
                    )
                )
            }
        }

        // 年度统计
        item {
            StatsCard(
                title = if (viewMode == StatsViewMode.YEAR) "${selectedYear} 年度统计" else "${selectedMonth.split("-")[0]} 年度统计",
                items = listOf(
                    "年度总时长" to (yearlyStats?.let { SalaryCalculator.formatHours(it.totalHours) } ?: "0小时"),
                    "年度预估薪资" to (yearlyStats?.let { SalaryCalculator.formatAmount(it.totalAmount) } ?: "¥0.00")
                )
            )
        }

        // 年度视图：按月趋势 + 类型占比
        if (viewMode == StatsViewMode.YEAR) {
            item {
                YearTrendCard(yearTrend = yearTrend)
            }
            item {
                TypeRatioCard(
                    workday = yearTypeRatio.first,
                    weekend = yearTypeRatio.second,
                    holiday = yearTypeRatio.third,
                    typeColor = typeColor
                )
            }
        }
    }

    // 某天具体记录弹窗
    if (showDayDetail && detailDay != null) {
        val day = detailDay!!
        val dateLabel = run {
            val parts = selectedMonth.split("-")
            "${parts[0]}年${parts[1]}月${day}日"
        }
        BackHandler(enabled = showDayDetail) { showDayDetail = false }
        OverlayDialog(
            show = showDayDetail,
            title = "$dateLabel 记录明细",
            summary = if (detailRecords.isEmpty()) "当天暂无记录" else "共 ${detailRecords.size} 条",
            onDismissRequest = { showDayDetail = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                if (detailRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "当天无加班/请假记录",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                } else {
                    // 汇总
                    val dayNet = detailRecords.sumOf { it.durationHours }
                    val dayAmount = detailRecords.sumOf { it.amount }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "合计净时长: ${SalaryCalculator.formatHours(dayNet)}",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dayNet < 0) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                        )
                        Text(
                            text = SalaryCalculator.formatAmount(dayAmount),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dayAmount < 0) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    detailRecords.forEach { record ->
                        val typeLabel = if (record.isLeave) "请假" else record.type.label
                        val recColor = if (record.isLeave) {
                            MiuixTheme.colorScheme.error
                        } else {
                            typeColor(record.type)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            cornerRadius = AppDimens.cardCorner,
                            insideMargin = PaddingValues(12.dp),
                            onClick = { navController.navigate("edit_record/${record.id}") },
                            showIndication = true,
                            pressFeedbackType = PressFeedbackType.Sink
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(40.dp)
                                        .background(recColor, RoundedCornerShape(AppDimens.chartBarRadius))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = typeLabel,
                                            style = MiuixTheme.textStyles.body1,
                                            fontWeight = FontWeight.Medium,
                                            color = recColor
                                        )
                                        if (record.isLeave) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MiuixTheme.colorScheme.error.copy(alpha = 0.1f),
                                                shape = AppShapes.radiusSm
                                            ) {
                                                Text(
                                                    text = "扣薪",
                                                    style = MiuixTheme.textStyles.footnote1,
                                                    color = MiuixTheme.colorScheme.error,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (record.note.isNotBlank()) {
                                        Text(
                                            text = record.note,
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 2
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = SalaryCalculator.formatAmount(record.amount),
                                        style = MiuixTheme.textStyles.body1,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (record.isLeave) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = SalaryCalculator.formatHours(record.durationHours),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDayDetail = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun LegendRow(typeColor: (OvertimeType) -> Color) {
    // 四色块均分整行横向铺开（SpaceEvenly），比原先左对齐 + 固定 16dp 间距更舒展
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem(MiuixTheme.colorScheme.error, "请假")
        LegendItem(typeColor(OvertimeType.WORKDAY), "工作日")
        LegendItem(typeColor(OvertimeType.WEEKEND), "休息日")
        LegendItem(typeColor(OvertimeType.HOLIDAY), "节假日")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(AppDimens.chartBarRadius))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun YearTrendCard(yearTrend: List<Pair<Int, Double>>) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = AppDimens.cardCorner) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "按月趋势（净时长）",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            val hasData = yearTrend.any { abs(it.second) > 0.0 }
            if (!hasData) {
                // 全年无记录：画 12 根灰条是噪音，改为空态提示
                EmptyState(
                    icon = MiuixIcons.AppRecording,
                    title = "暂无加班趋势数据",
                    subtitle = "添加记录后这里会按月展示"
                )
            } else {
            val maxAbs = yearTrend.maxOfOrNull { abs(it.second) } ?: 0.0
            yearTrend.forEach { (month, hours) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${month}月",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.width(36.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .background(MiuixTheme.colorScheme.secondaryContainer, AppShapes.radiusSm)
                    ) {
                        if (maxAbs > 0) {
                            val fraction = (abs(hours) / maxAbs).toFloat().coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(14.dp)
                                    .background(
                                        if (hours < 0) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                                        AppShapes.radiusSm
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = compactHours(hours),
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (hours < 0) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.width(56.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun TypeRatioCard(
    workday: Double,
    weekend: Double,
    holiday: Double,
    typeColor: (OvertimeType) -> Color
) {
    val total = workday + weekend + holiday
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = AppDimens.cardCorner) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "类型占比（加班时长）",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (total <= 0) {
                // 与「按月趋势」空态统一为同一 EmptyState 组件，保持统计页无记录样式一致
                EmptyState(
                    icon = MiuixIcons.Months,
                    title = "本年暂无加班记录",
                    subtitle = "添加记录后这里会按类型展示占比"
                )
            } else {
                // 占比条
                Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                    Box(modifier = Modifier.fillMaxWidth((workday / total).toFloat()).background(typeColor(OvertimeType.WORKDAY)))
                    Box(modifier = Modifier.fillMaxWidth((weekend / total).toFloat()).background(typeColor(OvertimeType.WEEKEND)))
                    Box(modifier = Modifier.fillMaxWidth((holiday / total).toFloat()).background(typeColor(OvertimeType.HOLIDAY)))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RatioItem(typeColor(OvertimeType.WORKDAY), "工作日", workday, total)
                    RatioItem(typeColor(OvertimeType.WEEKEND), "休息日", weekend, total)
                    RatioItem(typeColor(OvertimeType.HOLIDAY), "节假日", holiday, total)
                }
            }
        }
    }
}

@Composable
private fun RatioItem(color: Color, label: String, value: Double, total: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = SalaryCalculator.formatHours(value),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = color
        )
        Text(
            text = "$label ${((value / total) * 100).toInt()}%",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun CalendarCard(
    selectedMonth: String,
    dayAgg: Map<Int, DayAggregate>,
    typeColor: (OvertimeType) -> Color,
    dayCategories: Map<Int, DayCategory>,
    dayBackground: (DayCategory) -> Color,
    onDayClick: (Int) -> Unit = {}
) {
    val parts = selectedMonth.split("-")
    val year = parts[0].toInt()
    val month = parts[1].toInt()

    val cal = Calendar.getInstance().apply {
        set(year, month - 1, 1)
    }
    val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) // 1=周日 .. 7=周六
    val leadingEmpty = firstWeekday - 1
    val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = Calendar.getInstance()
    val isCurrentMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) + 1 == month
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    // 构建单元格：前导空格 + 1..totalDays
    val cells = mutableListOf<Int?>()
    repeat(leadingEmpty) { cells.add(null) }
    for (d in 1..totalDays) cells.add(d)
    while (cells.size % 7 != 0) cells.add(null)

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppDimens.cardCorner
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { w ->
                    Text(
                        text = w,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day == null) {
                                Spacer(modifier = Modifier.height(40.dp))
                            } else {
                                val agg = dayAgg[day]
                                val category = dayCategories[day] ?: DayCategory.WORKDAY
                                val isToday = isCurrentMonth && day == todayDay
                                // 每一天都可点击查看当日明细：无记录时弹窗展示空态，
                                // 避免用户误以为该日期无法查看。底色按「日期属性」而非
                                // 「是否有记录」着色，所以无记录的休息日/节假日同样有底色。
                                // 今日改用主色描边 + 加粗日期文字，叠加在分类底色之上，
                                // 不再用主色填充（默认主色与工作日蓝同色相会混淆）。
                                Surface(
                                    onClick = { onDayClick(day) },
                                    color = dayBackground(category),
                                    shape = AppShapes.radiusSm,
                                    border = if (isToday) BorderStroke(
                                        AppDimens.dayTodayBorderWidth,
                                        MiuixTheme.colorScheme.primary
                                    ) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = day.toString(),
                                            style = MiuixTheme.textStyles.footnote1,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isToday) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.onSurface
                                        )
                                        if (agg != null && agg.maxAbs > 0) {
                                            // 展示当天净时长：加班为正、请假为负（如 -2h）
                                            // 底色已是实色类型色，文字固定黑色保证对比度
                                            // （请假负值仍用错误色，红黑区分正负）
                                            val net = agg.netHours
                                            Text(
                                                text = compactHours(net),
                                                style = MiuixTheme.textStyles.footnote1,
                                                fontWeight = FontWeight.Medium,
                                                color = if (net < 0) MiuixTheme.colorScheme.error
                                                else Color.Black
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private fun compactHours(hours: Double): String {
    val sign = if (hours < 0) "-" else ""
    val absH = abs(hours)
    val h = absH.toInt()
    val m = ((absH - h) * 60).toInt()
    return when {
        m <= 0 -> "$sign${h}h"
        h <= 0 -> "$sign${m}m"
        else -> "$sign${h}h${m}"
    }
}

@Composable
private fun StatsCard(title: String, items: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppDimens.cardCorner
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowItems.forEach { (label, value) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Text(
                                text = value,
                                style = AppTitle3Semibold(),
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (rowItems.size == 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
