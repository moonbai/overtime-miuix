package com.overtime.miuix.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.util.SalaryCalculator
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

@Composable
fun StatisticsPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository,
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
    var stats by remember { mutableStateOf<com.overtime.miuix.data.repository.MonthlyStats?>(null) }
    var yearlyStats by remember { mutableStateOf<com.overtime.miuix.data.repository.YearlyStats?>(null) }
    var monthRecords by remember { mutableStateOf<List<OvertimeRecord>>(emptyList()) }

    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())

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
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 16.dp + bottomReserve
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部日期导航（点击切换月/年视图）
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
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

                    // 可点击的日期标题：切换月/年视图
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewMode = if (viewMode == StatsViewMode.MONTH) StatsViewMode.YEAR else StatsViewMode.MONTH
                            }
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

                    IconButton(onClick = {
                        if (viewMode == StatsViewMode.MONTH) shiftMonth(1) else shiftYear(1)
                    }) {
                        Icon(MiuixIcons.ChevronForward, contentDescription = "下${if (viewMode == StatsViewMode.MONTH) "一月" else "一年"}")
                    }
                }
            }
        }

        // 图例：类型颜色说明
        item {
            LegendRow(typeColor = typeColor)
        }

        // 月度视图专属：日历卡片
        if (viewMode == StatsViewMode.MONTH) {
            item {
                CalendarCard(
                    selectedMonth = selectedMonth,
                    dayAgg = dayAgg,
                    typeColor = typeColor,
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
                        "周末加班" to (stats?.let { SalaryCalculator.formatHours(it.weekendHours) } ?: "0小时"),
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
                                .padding(vertical = 4.dp)
                                .clickable { navController.navigate("edit_record/${record.id}") },
                            cornerRadius = 16.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(40.dp)
                                        .background(recColor, RoundedCornerShape(2.dp))
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
                                                shape = RoundedCornerShape(4.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegendItem(MiuixTheme.colorScheme.error, "请假")
        LegendItem(typeColor(OvertimeType.WORKDAY), "工作日")
        LegendItem(typeColor(OvertimeType.WEEKEND), "周末")
        LegendItem(typeColor(OvertimeType.HOLIDAY), "节假日")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
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
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "按月趋势（净时长）",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                            .background(MiuixTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                    ) {
                        if (maxAbs > 0) {
                            val fraction = (abs(hours) / maxAbs).toFloat().coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(14.dp)
                                    .background(
                                        if (hours < 0) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                                        RoundedCornerShape(4.dp)
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

@Composable
private fun TypeRatioCard(
    workday: Double,
    weekend: Double,
    holiday: Double,
    typeColor: (OvertimeType) -> Color
) {
    val total = workday + weekend + holiday
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "类型占比（加班时长）",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (total <= 0) {
                Text(
                    text = "本年暂无加班记录",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
                    RatioItem(typeColor(OvertimeType.WEEKEND), "周末", weekend, total)
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
        cornerRadius = 16.dp
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
                                val isToday = isCurrentMonth && day == todayDay
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isToday) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        // 每一天都可点击查看当日明细：无记录时弹窗展示空态，
                                        // 避免用户误以为该日期无法查看
                                        .clickable { onDayClick(day) }
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
                                        // 净时长为负时用错误色（请假占主导），否则用类型色
                                        val net = agg.netHours
                                        Text(
                                            text = compactHours(net),
                                            style = MiuixTheme.textStyles.footnote1,
                                            fontWeight = FontWeight.Medium,
                                            color = if (net < 0) MiuixTheme.colorScheme.error
                                            else typeColor(agg.type)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(16.dp))
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
        cornerRadius = 16.dp
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Text(
                                text = value,
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Medium
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
