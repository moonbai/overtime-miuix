package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import com.overtime.miuix.util.HolidayDataSource
import com.overtime.miuix.util.HolidayManager
import com.overtime.miuix.util.RecordSyncHelper
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
    val snackbarHostState = LocalSnackbarHostState.current
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
    // 请假时长：半天 = -4，全天 = -8（小时）
    var leaveDuration by remember { mutableStateOf(-4) }
    // 用户是否手动改过加班类型：为 true 时不再按日期自动覆盖
    var typeManuallyChanged by remember { mutableStateOf(false) }
    // 当前日期自动判定得到的类型标签（用于展示提示）
    var autoTypeHint by remember { mutableStateOf<String?>(null) }

    // 加班默认时间配置（来自基础设置）
    val defaultStartTime by settingsRepository.defaultStartTime.collectAsState(initial = "17:00")
    val endTimeAlign by settingsRepository.endTimeAlign.collectAsState(initial = "HALF")
    // 请假时长选择弹窗显隐
    var showLeaveDurationPicker by remember { mutableStateOf(false) }

    // 节假日数据源配置
    val holidayDataSource by settingsRepository.holidayDataSource.collectAsState(initial = "TIMOR")
    val holidayCustomUrl by settingsRepository.holidayCustomUrl.collectAsState(initial = "")
    val holidayMxnzpAppId by settingsRepository.holidayMxnzpAppId.collectAsState(initial = "")
    val holidayMxnzpAppSecret by settingsRepository.holidayMxnzpAppSecret.collectAsState(initial = "")
    val holidayIgnoreHoliday by settingsRepository.holidayIgnoreHoliday.collectAsState(initial = false)

    // 同步配置到 HolidayManager，保证按日期判定时使用用户所选数据源
    LaunchedEffect(holidayDataSource, holidayCustomUrl, holidayMxnzpAppId, holidayMxnzpAppSecret, holidayIgnoreHoliday) {
        val source = try { HolidayDataSource.valueOf(holidayDataSource) } catch (_: Exception) { HolidayDataSource.TIMOR }
        HolidayManager.configure(
            dataSource = source,
            customUrl = holidayCustomUrl,
            mxnzpAppId = holidayMxnzpAppId,
            mxnzpAppSecret = holidayMxnzpAppSecret,
            ignoreHoliday = holidayIgnoreHoliday
        )
    }

    // 依据所选日期自动判定加班类型（除非用户已手动更改、或为请假记录）
    LaunchedEffect(selectedDate, isLeave, typeManuallyChanged) {
        if (isLeave || typeManuallyChanged) return@LaunchedEffect
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
        val autoType = HolidayManager.getOvertimeType(dateStr)
        selectedType = autoType
        autoTypeHint = autoType.label
    }

    var showTypePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // 日期选择器状态（年/月/日）
    var pickYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var pickMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var pickDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }

    // 时间选择器状态（小时/分钟）
    var startHour by remember { mutableIntStateOf(startTimeStr.take(2).toIntOrNull() ?: 18) }
    var startMinute by remember { mutableIntStateOf(startTimeStr.takeLast(2).toIntOrNull() ?: 0) }
    var endHour by remember { mutableIntStateOf(endTimeStr.take(2).toIntOrNull() ?: 20) }
    var endMinute by remember { mutableIntStateOf(endTimeStr.takeLast(2).toIntOrNull() ?: 0) }

    // 获取某年某月的天数
    fun daysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val previewAmount = remember(selectedDate, selectedType, startTimeStr, endTimeStr, baseSalary, workdayRate, weekendRate, holidayRate, isLeave, leaveDuration) {
        if (isLeave) {
            // 请假：预估为工资扣减（负值）
            SalaryCalculator.calculateLeaveDeduction(baseSalary, leaveDuration.toDouble())
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
                // 编辑已有记录：沿用其原类型，不再按日期自动覆盖
                typeManuallyChanged = true
                selectedDate = Date(it.date)
                selectedType = it.type
                val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                startTimeStr = timeSdf.format(Date(it.startTime))
                endTimeStr = timeSdf.format(Date(it.endTime))
                note = it.note
                isLeave = it.isLeave
                // 请假时长按已存时长初始化（-4 半天 / -8 全天）
                if (it.isLeave) {
                    leaveDuration = if (it.durationHours <= -8) -8 else -4
                }
                // 同步选择器状态
                val cal = Calendar.getInstance().apply { time = Date(it.date) }
                pickYear = cal.get(Calendar.YEAR)
                pickMonth = cal.get(Calendar.MONTH) + 1
                pickDay = cal.get(Calendar.DAY_OF_MONTH)
                startHour = startTimeStr.take(2).toIntOrNull() ?: 18
                startMinute = startTimeStr.takeLast(2).toIntOrNull() ?: 0
                endHour = endTimeStr.take(2).toIntOrNull() ?: 20
                endMinute = endTimeStr.takeLast(2).toIntOrNull() ?: 0
            }
        } else {
            // 新建模式：初始化为当前日期，并按基础设置填充默认开始时间 / 结束时间
            val cal = Calendar.getInstance()
            pickYear = cal.get(Calendar.YEAR)
            pickMonth = cal.get(Calendar.MONTH) + 1
            pickDay = cal.get(Calendar.DAY_OF_MONTH)
            // 开始时间取基础设置默认值（如 17:00）
            startTimeStr = defaultStartTime
            // 结束时间取“当前打开时间”并按对齐粒度取整：HALF=30分，HOUR=整点
            val now = Calendar.getInstance()
            val roundedMinute = if (endTimeAlign == "HOUR") 0 else (now.get(Calendar.MINUTE) / 30) * 30
            now.set(Calendar.MINUTE, roundedMinute)
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)
            endTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
            startHour = defaultStartTime.take(2).toIntOrNull() ?: 17
            startMinute = defaultStartTime.takeLast(2).toIntOrNull() ?: 0
            endHour = now.get(Calendar.HOUR_OF_DAY)
            endMinute = roundedMinute
        }
    }

    fun triggerAfterSave(record: OvertimeRecord, oldRecord: OvertimeRecord? = null) {
        scope.launch {
            // 统一走 RecordSyncHelper：推送 / 日历同步 / 自动备份，与快速提报行为一致
            RecordSyncHelper.afterSave(context, repository, settingsRepository, record, oldRecord)
        }
    }

    // 统一保存逻辑：顶栏与右下角悬浮按钮共用
    fun performSave() {
        scope.launch {
            val oldRecord = if (recordId != null) repository.getRecordById(recordId) else null
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
                isLeave,
                leaveDuration
            )
            saved?.let {
                triggerAfterSave(it, oldRecord)
                // 先返回首页，再显示吐司，确保吐司在首页上可见
                navController.navigate("main") {
                    popUpTo(0) { inclusive = true }
                }
                val msg = if (recordId != null) "已更新记录" else "已保存记录"
                snackbarHostState.showCustomToast(msg)
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 提交按钮改到右下角悬浮，方便单手点击
            FloatingActionButton(
                onClick = { performSave() },
                containerColor = MiuixTheme.colorScheme.primary
            ) {
                Icon(MiuixIcons.Ok, contentDescription = "保存", tint = MiuixTheme.colorScheme.onPrimary)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            // 底部预留空间，避免最后一项被右下角悬浮保存按钮遮挡
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
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
                            text = if (isLeave) "预计扣减" else "预估薪资",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = SalaryCalculator.formatAmount(previewAmount),
                            style = MiuixTheme.textStyles.headline1,
                            color = if (isLeave) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
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

            if (!isLeave) {
                item {
                    BasicComponent(
                        title = "加班类型",
                        summary = if (!typeManuallyChanged && autoTypeHint != null) {
                            "${selectedType.label}（按日期自动判定）"
                        } else {
                            selectedType.label
                        },
                        endActions = { DropdownArrowEndAction(MiuixTheme.colorScheme.primary) },
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
                    summary = if (isLeave) "本记录为请假" else "普通加班记录",
                    endActions = {
                        Switch(
                            checked = isLeave,
                            onCheckedChange = { isLeave = it }
                        )
                    }
                )
            }

            // 请假时长选择（Dropdown (O)），仅在请假模式下显示，位于请假开关下方
            if (isLeave) {
                item {
                    BasicComponent(
                        title = "请假时长",
                        summary = if (leaveDuration == -8) "全天 (-8小时)" else "半天 (-4小时)",
                        endActions = { DropdownArrowEndAction(MiuixTheme.colorScheme.primary) },
                        onClick = { showLeaveDurationPicker = true }
                    )
                }
            }
        }

        // 以下弹窗必须位于 Scaffold 内容体内，才能访问 Scaffold 提供的弹窗宿主并显示
        if (showDatePicker) {
            OverlayDialog(
                show = showDatePicker,
                title = "选择日期",
                onDismissRequest = { showDatePicker = false }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val maxDay = daysInMonth(pickYear, pickMonth)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberPicker(
                            value = pickYear,
                            onValueChange = { pickYear = it },
                            range = 2020..2035,
                            modifier = Modifier.weight(1f)
                        )
                        NumberPicker(
                            value = pickMonth.coerceAtMost(12),
                            onValueChange = { pickMonth = it },
                            range = 1..12,
                            label = { "${it}月" },
                            modifier = Modifier.weight(1f)
                        )
                        NumberPicker(
                            value = pickDay.coerceAtMost(maxDay),
                            onValueChange = { pickDay = it },
                            range = 1..maxDay,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                set(pickYear, pickMonth - 1, pickDay.coerceAtMost(daysInMonth(pickYear, pickMonth)), 12, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            selectedDate = cal.time
                            showDatePicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("确定")
                    }
                }
            }
        }

        if (showStartPicker) {
            OverlayDialog(
                show = showStartPicker,
                title = "开始时间",
                onDismissRequest = { showStartPicker = false }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberPicker(
                            value = startHour,
                            onValueChange = { startHour = it },
                            range = 0..23,
                            label = { it.toString().padStart(2, '0') },
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = ":", fontWeight = FontWeight.Bold)
                        NumberPicker(
                            value = startMinute,
                            onValueChange = { startMinute = it },
                            range = 0..59,
                            label = { it.toString().padStart(2, '0') },
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            startTimeStr = String.format("%02d:%02d", startHour, startMinute)
                            showStartPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("确定")
                    }
                }
            }
        }

        if (showEndPicker) {
            OverlayDialog(
                show = showEndPicker,
                title = "结束时间",
                onDismissRequest = { showEndPicker = false }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberPicker(
                            value = endHour,
                            onValueChange = { endHour = it },
                            range = 0..23,
                            label = { it.toString().padStart(2, '0') },
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = ":", fontWeight = FontWeight.Bold)
                        NumberPicker(
                            value = endMinute,
                            onValueChange = { endMinute = it },
                            range = 0..59,
                            label = { it.toString().padStart(2, '0') },
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            endTimeStr = String.format("%02d:%02d", endHour, endMinute)
                            showEndPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("确定")
                    }
                }
            }
        }

        OverlayDialog(
            show = showTypePicker,
            title = "选择类型",
            onDismissRequest = { showTypePicker = false }
        ) {
            Column {
                val typeEntries = OvertimeType.entries
                typeEntries.forEachIndexed { index, type ->
                    DropdownImpl(
                        text = type.label,
                        optionSize = typeEntries.size,
                        isSelected = selectedType == type,
                        index = index,
                        onSelectedIndexChange = {
                            selectedType = typeEntries[it]
                            typeManuallyChanged = true
                            showTypePicker = false
                        }
                    )
                }
            }
        }

        OverlayDialog(
            show = showLeaveDurationPicker,
            title = "选择请假时长",
            onDismissRequest = { showLeaveDurationPicker = false }
        ) {
            Column {
                val options = listOf(-4 to "半天 (-4小时)", -8 to "全天 (-8小时)")
                options.forEachIndexed { index, (value, label) ->
                    DropdownImpl(
                        text = label,
                        optionSize = options.size,
                        isSelected = leaveDuration == value,
                        index = index,
                        onSelectedIndexChange = {
                            leaveDuration = options[it].first
                            showLeaveDurationPicker = false
                        }
                    )
                }
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
    isLeave: Boolean,
    leaveDuration: Int
): OvertimeRecord? {
    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val startTime = sdf.parse("$dateStr $startTimeStr")?.time ?: date.time
    val endTime = sdf.parse("$dateStr $endTimeStr")?.time ?: date.time
    // 请假记录：时长取 leaveDuration（半天 -4 / 全天 -8），工资按标准日薪比例扣减（负值）
    val duration = if (isLeave) leaveDuration.toDouble() else SalaryCalculator.calculateDurationHours(startTime, endTime)
    val amount = if (isLeave) SalaryCalculator.calculateLeaveDeduction(baseSalary, duration)
    else SalaryCalculator.calculateOvertimeAmount(baseSalary, type, rate, duration)

    val record = OvertimeRecord(
        id = recordId ?: 0,
        date = date.time,
        type = type,
        startTime = if (isLeave) date.time else startTime,
        endTime = if (isLeave) date.time else endTime,
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
