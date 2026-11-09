package com.mars.overtime.ui.screen

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.dismissDeleteSnackbars
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.mars.overtime.ui.theme.AppBody1Medium
import com.mars.overtime.ui.theme.AppBody1Semibold
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes
import com.mars.overtime.ui.theme.AppHeadline2Bold
import com.mars.overtime.ui.theme.AppTitle3Semibold
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
    topReserve: Dp = 0.dp,
    bottomReserve: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState()
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
     * 连续删除的撤销批次：关闭二次确认后用户可能快速连点删除，
     * 这些记录统一交给「最新一条」撤销提示承载，撤销时一次性全部恢复。
     */
    val pendingDeletes = remember { mutableListOf<OvertimeRecord>() }
    // 串行化批次更新，避免多个删除协程交错导致提示残留或批次错乱
    val deleteMutex = remember { Mutex() }
    var undoJob by remember { mutableStateOf<Job?>(null) }
    var undoGeneration by remember { mutableStateOf(0) }

    /**
     * 删除并给出 4 秒撤销窗口。确认删除与「关闭二次确认」后的直接删除共用此逻辑。
     *
     * MIUIX 0.9.4 的 SnackbarHost 会渲染队列中的全部条目，showSnackbar 每次都是
     * 「新增一条」而非「替换当前条」，因此连续删除会逐条叠加通知条。这里改为：
     * 先取消并收起上一条，只显示最新的一条；连续删除的记录合并进同一批次，
     * 既不堆叠，也不会因为旧提示被顶掉而丢失撤销能力。
     */
    fun performDelete(record: OvertimeRecord) {
        scope.launch {
            deleteMutex.withLock {
                repository.delete(record)
                pendingDeletes.add(record)

                // 代次先递增：旧协程被 dismiss 唤醒后据此直接退出，不会误清新批次
                val generation = ++undoGeneration
                undoJob?.cancel()
                snackbarHostState.dismissDeleteSnackbars()

                val count = pendingDeletes.size
                undoJob = scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = if (count > 1) "已删除 $count 条记录" else "已删除记录",
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Custom(4000)
                    )
                    // 已被后续删除取代：批次交由最新的提示处理，本协程不触碰
                    if (undoGeneration != generation) return@launch
                    if (result == SnackbarResult.ActionPerformed) {
                        // 结果确定时立刻取走并清空批次，避免后续删除混入本次撤销
                        val batch = deleteMutex.withLock {
                            val snapshot = pendingDeletes.toList()
                            pendingDeletes.clear()
                            snapshot
                        }
                        batch.forEach { repository.insert(it.copy(id = 0)) }
                        snackbarHostState.showCustomToast(
                            if (batch.size > 1) "已撤销 ${batch.size} 条删除" else "已撤销删除"
                        )
                    } else {
                        deleteMutex.withLock { pendingDeletes.clear() }
                    }
                }
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
                MonthlyOverviewCard(repository, currentMonth, records)
            }

            // 搜索框（添加入口已收敛到底部 FAB，避免与悬浮加号重复）
            item {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索备注 / 月份",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
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
                    FilterChip("休息日", recordFilter is RecordFilter.Type && (recordFilter as RecordFilter.Type).t == OvertimeType.WEEKEND) {
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
                    HomeEmptyState(
                        onAdd = {
                            // 连点时避免把多个 add_record 实例压入回退栈
                            navController.navigate("add_record") { launchSingleTop = true }
                        }
                    )
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
                                    style = AppTitle3Semibold(),
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

    BackHandler(enabled = showDeleteDialog) { showDeleteDialog = false }
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
        shape = AppShapes.radiusLg
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
    var stats by remember { mutableStateOf<com.mars.overtime.data.repository.MonthlyStats?>(null) }

    // 以 month 与 records 为 key：当前月内新增/删除记录时自动重算概览
    LaunchedEffect(month, records) {
        stats = repository.getMonthlyStats(month)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        cornerRadius = AppDimens.cardCorner
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "$month 概览",
                style = AppTitle3Semibold(),
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
        cornerRadius = AppDimens.cardCorner,
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
                    .width(AppDimens.typeBarWidth)
                    .height(40.dp)
                    .background(typeColor, RoundedCornerShape(AppDimens.chartBarRadius))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateStr,
                        style = AppBody1Medium(),
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
                        maxLines = 1
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                // 请假金额为负扣减，用错误色突出
                Text(
                    text = SalaryCalculator.formatAmount(record.amount),
                    style = AppBody1Semibold(),
                    color = if (record.isLeave) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                )
                Text(
                    // 请假在库中存负时长用于统计抵扣，列表里按正数展示并标注「请假」
                    text = if (record.isLeave) {
                        "请假 " + SalaryCalculator.formatHours(kotlin.math.abs(record.durationHours))
                    } else {
                        SalaryCalculator.formatHours(record.durationHours)
                    },
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
