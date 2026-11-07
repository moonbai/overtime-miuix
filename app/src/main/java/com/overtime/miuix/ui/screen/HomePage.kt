package com.overtime.miuix.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.utils.PressFeedbackType
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
    // 「删除二次确认」设置项：关闭时直接删除，仅靠 Snackbar 撤销
    val deleteConfirmEnabled by settingsRepository.deleteConfirmEnabled.collectAsState(initial = true)

    // 搜索与筛选
    var searchQuery by remember { mutableStateOf("") }
    var recordFilter by remember { mutableStateOf<RecordFilter>(RecordFilter.All) }

    // 删除确认对话框
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<OvertimeRecord?>(null) }

    /**
     * 删除并给出 4 秒撤销窗口。确认删除与「关闭二次确认」后的直接删除共用此逻辑，
     * 保证两种路径的撤销行为完全一致。
     */
    fun performDelete(record: OvertimeRecord) {
        scope.launch {
            repository.delete(record)
            // 撤销由 MIUIX 原生 Snackbar 承载：Scaffold 的 SnackbarHost 统一渲染，
            // 避免自绘 Surface 的 shadowElevation 在 Android 17 上被绘制成底部黑块。
            val result = snackbarHostState.showSnackbar(
                message = "已删除记录",
                actionLabel = "撤销",
                duration = SnackbarDuration.Custom(4000)
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.insert(record.copy(id = 0))
                snackbarHostState.showCustomToast("已撤销删除")
            }
        }
    }

    // 过滤 + 按月分组（倒序）
    val monthFormatter = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()) }
    val grouped by remember(records, searchQuery, recordFilter) {
        derivedStateOf {
            val q = searchQuery.trim()
            records.filter { rec ->
                val currentFilter = recordFilter
                val matchFilter = when (currentFilter) {
                    RecordFilter.All -> true
                    RecordFilter.Leave -> rec.isLeave
                    is RecordFilter.Type -> !rec.isLeave && rec.type == currentFilter.t
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
                    Button(
                        onClick = { navController.navigate("add_record") },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        cornerRadius = 20.dp,
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            MiuixIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加", fontWeight = FontWeight.Medium)
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
                                if (deleteConfirmEnabled) {
                                    selectedRecord = record
                                    showDeleteDialog = true
                                } else {
                                    performDelete(record)
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
                    rec?.let { performDelete(it) }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // MIUIX 的 Surface(onClick) 重载自带标准按压反馈与内容色透传，
    // 替代原先「Surface + clickable + 手写文字色」的组合
    Surface(
        onClick = onClick,
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer,
        contentColor = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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

    // MIUIX Card 的 onClick 重载提供标准涟漪 + 按下下沉反馈，
    // 替代原先外挂 clickable（无视觉反馈、点击区域与卡片圆角不一致）
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(12.dp),
        onClick = onClick,
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
