package com.overtime.miuix.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.push.CalendarSyncManager
import com.overtime.miuix.push.PushManager
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import com.overtime.miuix.util.BackupManager
import com.overtime.miuix.util.HolidayDataSource
import com.overtime.miuix.util.HolidayManager
import com.overtime.miuix.util.SalaryCalculator
import com.overtime.miuix.util.WebDavManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomePage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val records by repository.getAllRecords().collectAsState(initial = emptyList())
    val currentMonth = remember { SalaryCalculator.getCurrentYearMonth() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    // 快速提报开关
    val quickSubmit by settingsRepository.quickSubmit.collectAsState(initial = false)

    // 节假日配置（用于自动判定加班类型）
    val holidayDataSource by settingsRepository.holidayDataSource.collectAsState(initial = "TIMOR")
    val holidayCustomUrl by settingsRepository.holidayCustomUrl.collectAsState(initial = "")
    val holidayMxnzpAppId by settingsRepository.holidayMxnzpAppId.collectAsState(initial = "")
    val holidayMxnzpAppSecret by settingsRepository.holidayMxnzpAppSecret.collectAsState(initial = "")
    val holidayIgnoreHoliday by settingsRepository.holidayIgnoreHoliday.collectAsState(initial = false)

    // 薪资配置
    val baseSalary by settingsRepository.baseSalary.collectAsState(initial = 2200.0)
    val workdayRate by settingsRepository.workdayRate.collectAsState(initial = 1.5)
    val weekendRate by settingsRepository.weekendRate.collectAsState(initial = 2.0)
    val holidayRate by settingsRepository.holidayRate.collectAsState(initial = 3.0)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<OvertimeRecord?>(null) }

    // 快速提报弹窗状态
    var showQuickSubmitDialog by remember { mutableStateOf(false) }

    // 同步节假日配置到 HolidayManager
    LaunchedEffect(holidayDataSource, holidayCustomUrl, holidayMxnzpAppId, holidayMxnzpAppSecret, holidayIgnoreHoliday) {
        HolidayManager.configure(
            dataSource = try { HolidayDataSource.valueOf(holidayDataSource) } catch (_: Exception) { HolidayDataSource.TIMOR },
            customUrl = holidayCustomUrl,
            mxnzpAppId = holidayMxnzpAppId,
            mxnzpAppSecret = holidayMxnzpAppSecret,
            ignoreHoliday = holidayIgnoreHoliday
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MonthlyOverviewCard(repository, currentMonth)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "全部记录",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = { navController.navigate("add_record") },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(MiuixIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加", style = MiuixTheme.textStyles.button, color = MiuixTheme.colorScheme.onPrimary)
                    }
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
            } else {
                items(records, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        onClick = { navController.navigate("edit_record/${record.id}") },
                        onDelete = {
                            selectedRecord = record
                            showDeleteDialog = true
                        }
                    )
                }
            }

            // 底部留白给 FAB 让路
            if (quickSubmit) {
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }

        // 快速提报浮动按钮
        if (quickSubmit) {
            FloatingActionButton(
                onClick = { showQuickSubmitDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp),
                containerColor = MiuixTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(
                    MiuixIcons.Add,
                    contentDescription = "快速提报",
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // 删除确认弹窗
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
                    scope.launch {
                        selectedRecord?.let { repository.delete(it) }
                    }
                    showDeleteDialog = false
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // 快速提报弹窗
    if (showQuickSubmitDialog) {
        QuickSubmitDialog(
            context = context,
            settingsRepository = settingsRepository,
            repository = repository,
            baseSalary = baseSalary,
            workdayRate = workdayRate,
            weekendRate = weekendRate,
            holidayRate = holidayRate,
            snackbarHostState = snackbarHostState,
            onDismiss = { showQuickSubmitDialog = false }
        )
    }
}

@Composable
private fun QuickSubmitDialog(
    context: Context,
    settingsRepository: SettingsRepository,
    repository: OvertimeRepository,
    baseSalary: Double,
    workdayRate: Double,
    weekendRate: Double,
    holidayRate: Double,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val now = remember { Date() }
    val todayStr = remember { dateFormat.format(now) }

    var detectedType by remember { mutableStateOf<OvertimeType?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // 自动检测今天的加班类型
    LaunchedEffect(Unit) {
        detectedType = HolidayManager.getOvertimeType(todayStr)
    }

    // 根据类型推导默认时间段：工作日 17:00-19:00，其余 18:00-20:00
    val quickStart = if (detectedType == OvertimeType.WORKDAY) "17:00" else "18:00"
    val quickEnd = if (detectedType == OvertimeType.WORKDAY) "19:00" else "20:00"

    val typeLabel = when (detectedType) {
        OvertimeType.WORKDAY -> "工作日加班"
        OvertimeType.WEEKEND -> "周末加班"
        OvertimeType.HOLIDAY -> "节假日加班"
        null -> "检测中…"
    }

    OverlayDialog(
        show = true,
        title = "快速提报",
        summary = "将自动使用当前日期和默认时间段（$quickStart-$quickEnd）提报一条加班记录。\n\n日期：${SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(now)}\n加班类型：$typeLabel（自动判定）\n时间段：$quickStart - $quickEnd",
        onDismissRequest = { if (!isSubmitting) onDismiss() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = "取消",
                onClick = { onDismiss() },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(
                text = if (isSubmitting) "提交中…" else "确认提报",
                onClick = {
                    if (isSubmitting) return@TextButton
                    isSubmitting = true
                    scope.launch {
                        try {
                            val type = detectedType ?: OvertimeType.WORKDAY
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val startTime = sdf.parse("$todayStr $quickStart")?.time ?: now.time
                            val endTime = sdf.parse("$todayStr $quickEnd")?.time ?: now.time
                            val duration = SalaryCalculator.calculateDurationHours(startTime, endTime)
                            val rate = when (type) {
                                OvertimeType.WORKDAY -> workdayRate
                                OvertimeType.WEEKEND -> weekendRate
                                OvertimeType.HOLIDAY -> holidayRate
                            }
                            val amount = SalaryCalculator.calculateOvertimeAmount(baseSalary, type, rate, duration)

                            val record = OvertimeRecord(
                                date = now.time,
                                type = type,
                                startTime = startTime,
                                endTime = endTime,
                                durationHours = duration,
                                baseSalary = baseSalary,
                                rate = rate,
                                amount = amount,
                                note = "快速提报"
                            )
                            val id = repository.insert(record)
                            val saved = record.copy(id = id)

                            // 触发推送/日历/备份
                            if (settingsRepository.pushEnabled.first()) {
                                val channel = settingsRepository.pushChannel.first()
                                if (channel != "none") {
                                    PushManager.sendToSelectedChannel(channel, settingsRepository.exportSettingsMap(), saved)
                                }
                            }
                            if (settingsRepository.calendarSyncEnabled.first() && CalendarSyncManager.hasCalendarPermission(context)) {
                                CalendarSyncManager.addEvent(context, saved)
                            }
                            val settingsMap = settingsRepository.exportSettingsMap()
                            val webdavEnabled = settingsMap["webdav_enabled"]?.toBoolean() ?: false
                            val location = settingsMap["auto_backup_location"] ?: "local"
                            val webdavConfig = if (webdavEnabled && location == "cloud") {
                                WebDavManager.WebDavConfig(
                                    baseUrl = settingsMap["webdav_url"] ?: "",
                                    username = settingsMap["webdav_username"] ?: "",
                                    password = settingsMap["webdav_password"] ?: "",
                                    remotePath = settingsMap["webdav_path"] ?: "/overtime_backup/"
                                )
                            } else null
                            val allRecords = repository.getAllRecords().first()
                            BackupManager.performAutoBackup(context, allRecords, settingsMap, webdavConfig)

                            snackbarHostState.showCustomToast("快速提报成功")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            snackbarHostState.showCustomToast("提报失败: ${e.message}")
                        } finally {
                            isSubmitting = false
                            onDismiss()
                        }
                    }
                },
                enabled = !isSubmitting && detectedType != null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MonthlyOverviewCard(repository: OvertimeRepository, month: String) {
    var stats by remember { mutableStateOf<com.overtime.miuix.data.repository.MonthlyStats?>(null) }

    LaunchedEffect(month) {
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
                fontWeight = FontWeight.SemiBold
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
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(record.date))
    val displayLabel = if (record.isLeave) "休息日加班" else record.type.label
    val typeColor = if (record.isLeave) {
        MiuixTheme.colorScheme.error
    } else {
        when (record.type) {
            OvertimeType.WORKDAY -> MiuixTheme.colorScheme.primary
            OvertimeType.WEEKEND -> MiuixTheme.colorScheme.secondary
            OvertimeType.HOLIDAY -> MiuixTheme.colorScheme.onTertiaryContainer
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 12.dp
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
                        text = displayLabel,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        color = typeColor
                    )
                    if (record.isLeave) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MiuixTheme.colorScheme.error.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "请假",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = dateStr,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
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
                Text(
                    text = SalaryCalculator.formatAmount(record.amount),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary
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
