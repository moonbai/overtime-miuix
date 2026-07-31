package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.overtime.miuix.util.SalaryCalculator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddEditRecordPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository,
    recordId: Long? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = recordId != null

    val baseSalary by settingsRepository.baseSalary.collectAsState(initial = 2200.0)
    val workdayRate by settingsRepository.workdayRate.collectAsState(initial = 1.5)
    val weekendRate by settingsRepository.weekendRate.collectAsState(initial = 2.0)
    val holidayRate by settingsRepository.holidayRate.collectAsState(initial = 3.0)

    var selectedDate by remember { mutableStateOf(Date()) }
    var selectedType by remember { mutableStateOf(OvertimeType.WORKDAY) }
    var startTimeStr by remember { mutableStateOf("18:00") }
    var endTimeStr by remember { mutableStateOf("20:00") }
    var note by remember { mutableStateOf("") }
    var isLeave by remember { mutableStateOf(false) }

    var showTypePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
    val startPickerState = rememberTimePickerState(
        initialHour = startTimeStr.take(2).toIntOrNull() ?: 18,
        initialMinute = startTimeStr.takeLast(2).toIntOrNull() ?: 0
    )
    val endPickerState = rememberTimePickerState(
        initialHour = endTimeStr.take(2).toIntOrNull() ?: 20,
        initialMinute = endTimeStr.takeLast(2).toIntOrNull() ?: 0
    )

    val previewAmount = remember(selectedDate, selectedType, startTimeStr, endTimeStr, baseSalary, workdayRate, weekendRate, holidayRate, isLeave) {
        if (isLeave) {
            0.0
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
            val start = sdf.parse("$dateStr $startTimeStr")?.time ?: 0
            val end = sdf.parse("$dateStr $endTimeStr")?.time ?: 0
            val duration = if (end > start) SalaryCalculator.calculateDurationHours(start, end) else 0.0
            val rate = when (selectedType) {
                OvertimeType.WORKDAY -> workdayRate
                OvertimeType.WEEKEND -> weekendRate
                OvertimeType.HOLIDAY -> holidayRate
            }
            SalaryCalculator.calculateOvertimeAmount(baseSalary, selectedType, rate, duration)
        }
    }

    LaunchedEffect(recordId) {
        if (recordId != null) {
            val record = repository.getRecordById(recordId)
            record?.let {
                selectedDate = Date(it.date)
                selectedType = it.type
                val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                startTimeStr = timeSdf.format(Date(it.startTime))
                endTimeStr = timeSdf.format(Date(it.endTime))
                note = it.note
                isLeave = it.isLeave
            }
        }
    }

    fun triggerAfterSave(record: OvertimeRecord) {
        scope.launch {
            val settings = settingsRepository
            if (settings.pushEnabled.first()) {
                val channel = settings.pushChannel.first()
                if (channel != "none") {
                    PushManager.sendToSelectedChannel(channel, settings.exportSettingsMap(), record)
                }
            }
            if (settings.calendarSyncEnabled.first() && CalendarSyncManager.hasCalendarPermission(context)) {
                CalendarSyncManager.removeEvents(context, record)
                CalendarSyncManager.addEvent(context, record)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = if (isEdit) "编辑记录" else "添加记录",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val saved = saveRecord(
                                    repository,
                                    recordId,
                                    selectedDate,
                                    selectedType,
                                    startTimeStr,
                                    endTimeStr,
                                    baseSalary,
                                    when (selectedType) {
                                        OvertimeType.WORKDAY -> workdayRate
                                        OvertimeType.WEEKEND -> weekendRate
                                        OvertimeType.HOLIDAY -> holidayRate
                                    },
                                    note,
                                    isLeave
                                )
                                saved?.let { triggerAfterSave(it) }
                                navController.popBackStack()
                            }
                        }
                    ) {
                        Icon(MiuixIcons.Ok, contentDescription = "保存")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "预估薪资",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = SalaryCalculator.formatAmount(previewAmount),
                            style = MiuixTheme.textStyles.headline1,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                BasicComponent(
                    title = "日期",
                    summary = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(selectedDate),
                    onClick = { showDatePicker = true }
                )
            }

            item {
                BasicComponent(
                    title = "加班类型",
                    summary = selectedType.label,
                    onClick = { showTypePicker = true }
                )
            }

            item {
                BasicComponent(
                    title = "开始时间",
                    summary = startTimeStr,
                    onClick = { showStartPicker = true }
                )
            }

            item {
                BasicComponent(
                    title = "结束时间",
                    summary = endTimeStr,
                    onClick = { showEndPicker = true }
                )
            }

            item {
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                BasicComponent(
                    title = "请假记录",
                    endActions = {
                        Switch(
                            checked = isLeave,
                            onCheckedChange = { isLeave = it }
                        )
                    }
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    text = "确定",
                    onClick = {
                        selectedDate = Date(datePickerState.selectedDateMillis ?: selectedDate.time)
                        showDatePicker = false
                    }
                )
            },
            dismissButton = {
                TextButton(text = "取消", onClick = { showDatePicker = false })
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    text = "确定",
                    onClick = {
                        startTimeStr = String.format("%02d:%02d", startPickerState.hour, startPickerState.minute)
                        showStartPicker = false
                    }
                )
            },
            dismissButton = {
                TextButton(text = "取消", onClick = { showStartPicker = false })
            }
        ) {
            TimePicker(state = startPickerState)
        }
    }

    if (showEndPicker) {
        TimePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    text = "确定",
                    onClick = {
                        endTimeStr = String.format("%02d:%02d", endPickerState.hour, endPickerState.minute)
                        showEndPicker = false
                    }
                )
            },
            dismissButton = {
                TextButton(text = "取消", onClick = { showEndPicker = false })
            }
        ) {
            TimePicker(state = endPickerState)
        }
    }

    OverlayDialog(
        show = showTypePicker,
        title = "选择类型",
        onDismissRequest = { showTypePicker = false }
    ) {
        Column {
            OvertimeType.entries.forEach { type ->
                BasicComponent(
                    title = type.label,
                    onClick = {
                        selectedType = type
                        showTypePicker = false
                    }
                )
            }
        }
    }
}

private suspend fun saveRecord(
    repository: OvertimeRepository,
    recordId: Long?,
    date: Date,
    type: OvertimeType,
    startTimeStr: String,
    endTimeStr: String,
    baseSalary: Double,
    rate: Double,
    note: String,
    isLeave: Boolean
): OvertimeRecord? {
    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val startTime = sdf.parse("$dateStr $startTimeStr")?.time ?: date.time
    val endTime = sdf.parse("$dateStr $endTimeStr")?.time ?: date.time
    val duration = SalaryCalculator.calculateDurationHours(startTime, endTime)
    val amount = if (isLeave) 0.0 else SalaryCalculator.calculateOvertimeAmount(baseSalary, type, rate, duration)

    val record = OvertimeRecord(
        id = recordId ?: 0,
        date = date.time,
        type = type,
        startTime = startTime,
        endTime = endTime,
        durationHours = duration,
        baseSalary = baseSalary,
        rate = rate,
        amount = amount,
        note = note,
        isLeave = isLeave
    )

    return try {
        if (recordId != null) {
            repository.update(record)
            record
        } else {
            val id = repository.insert(record)
            record.copy(id = id)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
