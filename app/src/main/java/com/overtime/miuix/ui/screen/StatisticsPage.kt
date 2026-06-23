package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.util.SalaryCalculator
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StatisticsPage(
    navController: NavHostController,
    repository: OvertimeRepository
) {
    val yearMonths = remember { SalaryCalculator.getYearMonthList(12) }
    var selectedMonth by remember { mutableStateOf(yearMonths.first()) }
    var stats by remember { mutableStateOf<com.overtime.miuix.data.repository.MonthlyStats?>(null) }
    var yearlyStats by remember { mutableStateOf<com.overtime.miuix.data.repository.YearlyStats?>(null) }
    
    LaunchedEffect(selectedMonth) {
        stats = repository.getMonthlyStats(selectedMonth)
        val year = selectedMonth.split("-")[0]
        yearlyStats = repository.getYearlyStats(year)
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScrollableTabRow(
                selectedTabIndex = yearMonths.indexOf(selectedMonth),
                modifier = Modifier.fillMaxWidth()
            ) {
                yearMonths.forEachIndexed { index, month ->
                    Tab(
                        selected = selectedMonth == month,
                        onClick = { selectedMonth = month },
                        text = { Text(month) }
                    )
                }
            }
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
                                style = MiuixTheme.textStyles.caption,
                                color = MiuixTheme.colorScheme.onSurfaceVariant
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
