package com.overtime.miuix.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import com.overtime.miuix.util.SalaryCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.*

/** 首页记录筛选：全部 / 请假 / 按类型 */
private sealed class RecordFilter {
    object All : RecordFilter()
    object Leave : RecordFilter()
    data class Type(val t: OvertimeType) : RecordFilter()
}

@Composable
fun HomePage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository,
    bottomReserve: Dp = 0.dp
) {
    val records by repository.getAllRecords().collectAsState(initial = emptyList())
    val currentMonth = remember { SalaryCalculator.getCurrentYearMonth() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    // 用户配置的加班类型配色，用于首页记录标题与统计保持一致
    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())

    // 搜索与筛选
    var searchQuery by remember { mutableStateOf("") }
    var recordFilter by remember { mutableStateOf<RecordFilter>(RecordFilter.All) }

    // 删除确认对话框
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<OvertimeRecord?>(null) }

    // 撤销：确认删除后已写入数据库，暂存副本用于撤销重插；超时（4s）后彻底清除
    var pendingUndo by remember { mutableStateOf<OvertimeRecord?>(null) }
    LaunchedEffect(pendingUndo) {
        if (pendingUndo != null) {
            delay(4000)
            pendingUndo = null
        }
    }

    // 过滤 + 按月分组（倒序）
    val monthFormatter = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()) }
    val grouped by remember(records, searchQuery, recordFilter) {
        derivedStateOf {
            val q = searchQuery.trim()
            records.filter { rec ->
                val matchFilter = when (recordFilter) {
                    RecordFilter.All -> true
                    RecordFilter.Leave -> rec.isLeave
                    is RecordFilter.Type -> !rec.isLeave && rec.type == recordFilter.t
                }
                val matchQuery = q.isEmpty() || rec.note.contains(q, ignoreCase = true)
                        || monthFormatter.format(Date(rec.date)).contains(q)
                        || SalaryCalculator.formatHours(rec.durationHours).contains(q)
                matchFilter && matchQuery
            }.groupBy { monthFormatter.format(Date(it.date)) }
                .toSortedMap(compareByDescending { it })
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp + bottomReserve
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MonthlyOverviewCard(repository, currentMonth, records)
            }

            // 搜索框 + 添加按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = "搜索备注 / 月份",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Surface(
                        color = MiuixTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.clickable { navController.navigate("add_record") }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                MiuixIcons.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "添加",
                                style = MiuixTheme.textStyles.button,
                                color = MiuixTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 筛选 chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip("全部", recordFilter is RecordFilter.All) { recordFilter = RecordFilter.All }
                    FilterChip("工作日", recordFilter is RecordFilter.Type && (recordFilter as RecordFilter.Type).t == OvertimeType.WORKDAY) {
                        recordFilter = RecordFilter.Type(OvertimeType.WORKDAY)
                    }
                    FilterChip("周末", recordFilter is RecordFilter.Type && (recordFilter as RecordFilter.Type).t == OvertimeType.WEEKEND) {
                        recordFilter = RecordFilter.Type(OvertimeType.WEEKEND)
                    }
                    FilterChip("节假日", recordFilter is RecordFilter.Type && (recordFilter as RecordFilter.Type).t == OvertimeType.HOLIDAY) {
                        recordFilter = RecordFilter.Type(OvertimeType.HOLIDAY)
                    }
                    FilterChip("请假", recordFilter is RecordFilter.Leave) { recordFilter = RecordFilter.Leave }
                }
            }

            if (records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无加班记录\n点击添加按钮记录",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            } else if (grouped.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "没有匹配的记录",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            } else {
                grouped.forEach { (month, list) ->
                    val parts = month.split("-")
                    stickyHeader {
                        Surface(
                            color = MiuixTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${parts[0]}年${parts[1]}月",
                                    style = MiuixTheme.textStyles.title3,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${list.size}条",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                    items(list, key = { it.id }) { record ->
                        RecordCard(
                            record = record,
                            typeColorWorkday = typeColorWorkday,
                            typeColorWeekend = typeColorWeekend,
                            typeColorHoliday = typeColorHoliday,
                            onClick = { navController.navigate("edit_record/${record.id}") },
                            onDelete = {
                                selectedRecord = record
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        // 撤销条：确认删除后展示，4 秒内可撤销
        if (pendingUndo != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已删除记录",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        TextButton(
                            text = "撤销",
                            onClick = {
                                val rec = pendingUndo
                                pendingUndo = null
                                rec?.let {
                                    scope.launch {
                                        repository.insert(it.copy(id = 0))
                                        snackbarHostState.showCustomToast("已撤销删除")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = showDeleteDialog,
        title = "确认删除",
        summary = "确定要删除这条记录吗？",
        onDismissRequest = { showDeleteDialog = false }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = "取消",
                onClick = { showDeleteDialog = false },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(
                text = "删除",
                onClick = {
                    val rec = selectedRecord
                    showDeleteDialog = false
                    rec?.let {
                        scope.launch { repository.delete(it) }
                        pendingUndo = it
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun MonthlyOverviewCard(
    repository: OvertimeRepository,
    month: String,
    records: List<OvertimeRecord>
) {
    var stats by remember { mutableStateOf<com.overtime.miuix.data.repository.MonthlyStats?>(null) }

    // 以 month 与 records 为 key：当前月内新增/删除记录时自动重算概览
    LaunchedEffect(month, records) {
        stats = repository.getMonthlyStats(month)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "$month 概览",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("总时长", stats?.let { SalaryCalculator.formatHours(it.totalHours) } ?: "0小时")
                StatItem("预估薪资", stats?.let { SalaryCalculator.formatAmount(it.totalAmount) } ?: "¥0.00")
                StatItem("记录数", "${stats?.recordCount ?: 0}条")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "金额按记录保存时的薪资规则计算，修改基础设置仅影响之后的新记录",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun RecordCard(
    record: OvertimeRecord,
    typeColorWorkday: Int,
    typeColorWeekend: Int,
    typeColorHoliday: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    // 日期格式：同年显示 MM-dd，跨年显示 yyyy-MM-dd
    val cal = Calendar.getInstance().apply { time = Date(record.date) }
    val now = Calendar.getInstance()
    val datePattern = if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "MM-dd" else "yyyy-MM-dd"
    val dateStr = SimpleDateFormat(datePattern, Locale.getDefault()).format(Date(record.date))
    val displayLabel = if (record.isLeave) "请假" else record.type.label
    val typeColor = if (record.isLeave) {
        MiuixTheme.colorScheme.error
    } else {
        when (record.type) {
            OvertimeType.WORKDAY -> Color(typeColorWorkday)
            OvertimeType.WEEKEND -> Color(typeColorWeekend)
            OvertimeType.HOLIDAY -> Color(typeColorHoliday)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(typeColor, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateStr,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayLabel,
                        style = MiuixTheme.textStyles.footnote1,
                        color = typeColor
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
                        maxLines = 1
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                // 请假金额为负扣减，用错误色突出
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

            IconButton(onClick = onDelete) {
                Icon(MiuixIcons.Delete, contentDescription = "删除", tint = MiuixTheme.colorScheme.error)
            }
        }
    }
}
