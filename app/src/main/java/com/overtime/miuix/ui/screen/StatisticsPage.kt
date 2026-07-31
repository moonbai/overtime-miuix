package com.overtime.miuix.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private data class DayAggregate(
    var totalHours: Double = 0.0,
    var type: OvertimeType = OvertimeType.WORKDAY,
    var isLeave: Boolean = false,
    var maxAbs: Double = 0.0
)

@Composable
fun StatisticsPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    val yearMonths = remember { SalaryCalculator.getYearMonthList(12) }
    var selectedMonth by remember { mutableStateOf(yearMonths.first()) }
    var stats by remember { mutableStateOf<com.overtime.miuix.data.repository.MonthlyStats?>(null) }
    var yearlyStats by remember { mutableStateOf<com.overtime.miuix.data.repository.YearlyStats?>(null) }
    var monthRecords by remember { mutableStateOf<List<OvertimeRecord>>(emptyList()) }

    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())

    LaunchedEffect(selectedMonth) {
        stats = repository.getMonthlyStats(selectedMonth)
        val year = selectedMonth.split("-")[0]
        yearlyStats = repository.getYearlyStats(year)
        monthRecords = repository.getMonthRecords(selectedMonth)
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

    // 按日聚合当月记录，用于日历显示
    val dayAgg = remember(monthRecords) {
        val map = mutableMapOf<Int, DayAggregate>()
        for (r in monthRecords) {
            val cal = Calendar.getInstance().apply { time = Date(r.date) }
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val agg = map.getOrPut(day) { DayAggregate() }
            agg.totalHours += abs(r.durationHours)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    IconButton(onClick = { shiftMonth(-1) }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "上一月")
                    }
                    val parts = selectedMonth.split("-")
                    Text(
                        text = "${parts[0]}年${parts[1]}月",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { shiftMonth(1) }) {
                        Icon(MiuixIcons.ChevronForward, contentDescription = "下一月")
                    }
                }
            }
        }

        item {
            CalendarCard(
                selectedMonth = selectedMonth,
                dayAgg = dayAgg,
                typeColor = typeColor
            )
        }

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

        item {
            val year = selectedMonth.split("-")[0]
            StatsCard(
                title = "$year 年度统计",
                items = listOf(
                    "年度总时长" to (yearlyStats?.let { SalaryCalculator.formatHours(it.totalHours) } ?: "0小时"),
                    "年度预估薪资" to (yearlyStats?.let { SalaryCalculator.formatAmount(it.totalAmount) } ?: "¥0.00")
                )
            )
        }
    }
}

@Composable
private fun CalendarCard(
    selectedMonth: String,
    dayAgg: Map<Int, DayAggregate>,
    typeColor: (OvertimeType) -> Color
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
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = day.toString(),
                                        style = MiuixTheme.textStyles.footnote1,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface
                                    )
                                    if (agg != null && agg.totalHours > 0) {
                                        Text(
                                            text = compactHours(agg.totalHours),
                                            style = MiuixTheme.textStyles.footnote1,
                                            fontWeight = FontWeight.Medium,
                                            color = typeColor(agg.type)
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
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    return when {
        m <= 0 -> "${h}h"
        h <= 0 -> "${m}m"
        else -> "${h}h${m}"
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
