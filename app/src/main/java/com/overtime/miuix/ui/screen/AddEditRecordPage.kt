package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.util.SalaryCalculator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
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
    
    val previewAmount = remember(selectedDate, selectedType, startTimeStr, endTimeStr, baseSalary) {
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
                                saveRecord(
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
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "预估薪资",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariant
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
                ListItem(
                    title = "日期",
                    summary = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(selectedDate),
                    onClick = { }
                )
            }
            
            item {
                ListItem(
                    title = "加班类型",
                    summary = selectedType.label,
                    onClick = { showTypePicker = true }
                )
            }
            
            item {
                ListItem(
                    title = "开始时间",
                    summary = startTimeStr,
                    onClick = { }
                )
            }
            
            item {
                ListItem(
                    title = "结束时间",
                    summary = endTimeStr,
                    onClick = { }
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
                SwitchItem(
                    title = "请假记录",
                    checked = isLeave,
                    onCheckedChange = { isLeave = it }
                )
            }
        }
    }
    
    if (showTypePicker) {
        AlertDialog(
            onDismissRequest = { showTypePicker = false },
            title = "选择类型",
            text = {
                Column {
                    OvertimeType.entries.forEach { type ->
                        ListItem(
                            title = type.label,
                            onClick = {
                                selectedType = type
                                showTypePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
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
) {
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
    
    if (recordId != null) {
        repository.update(record)
    } else {
        repository.insert(record)
    }
}
