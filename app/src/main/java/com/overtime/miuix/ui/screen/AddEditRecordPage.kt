package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
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
import kotlin.math.roundToLong

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
    var durationHours by remember { mutableStateOf(2.0) }
    var note by remember { mutableStateOf("") }
    var isLeave by remember { mutableStateOf(false) }
    // 请假时长：半天 = -4，全天 = -8（小时）
    var leaveDuration by remember { mutableStateOf(-4) }
    // 用户是否手动改过加班类型：为 true 时不再按日期自动覆盖
    var typeManuallyChanged by remember { mutableStateOf(false) }
    // 当前日期自动判定得到的类型标签（用于展示提示）
    var autoTypeHint by remember { mutableStateOf<String?>(null) }

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
    var showDurationPicker by remember { mutableStateOf(false) }

    // 日期选择器状态（年/月/日）
    var pickYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var pickMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var pickDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }

    // 时长选择器：以 0.5 小时为梯度，范围 0.5~24
    var pickDurationIndex by remember { mutableIntStateOf(3) }
    val durationOptions = remember { (0 until 48).map { (it + 1) * 0.5 } } // 0.5, 1.0, ..., 24.0

    fun indexOfDuration(hours: Double): Int {
        val idx = ((hours / 0.5).roundToLong() - 1).toInt()
        return idx.coerceIn(0, durationOptions.size - 1)
    }

    // 获取某年某月的天数
    fun daysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val previewAmount = remember(selectedDate, selectedType, durationHours, baseSalary, workdayRate, weekendRate, holidayRate, isLeave, leaveDuration) {
        if (isLeave) {
            // 请假：预估为工资扣减（负值）
            SalaryCalculator.calculateLeaveDeduction(baseSalary, leaveDuration.toDouble())
        } else {
            val rate = when (selectedType) {
                OvertimeType.WORKDAY -> workdayRate
                OvertimeType.WEEKEND -> weekendRate
                OvertimeType.HOLIDAY -> holidayRate
            }
            SalaryCalculator.calculateOvertimeAmount(baseSalary, selectedType, rate, durationHours)
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
                // 编辑模式下时长初始化
                durationHours = if (it.isLeave) Math.abs(it.durationHours) else it.durationHours.coerceAtLeast(0.5)
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
                pickDurationIndex = indexOfDuration(durationHours)
            }
        } else {
            // 新建模式：初始化为当前日期，默认时长 2.0h
            val cal = Calendar.getInstance()
            pickYear = cal.get(Calendar.YEAR)
            pickMonth = cal.get(Calendar.MONTH) + 1
            pickDay = cal.get(Calendar.DAY_OF_MONTH)
            pickDurationIndex = indexOfDuration(2.0)
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
                durationHours,
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
                // 同步动作在当前协程内串行执行并整体兜底：
                // 记录本身已入库，后续推送/日历/备份的任何异常都不得影响保存结果与页面跳转。
                val calendarResult = try {
                    RecordSyncHelper.afterSave(context, repository, settingsRepository, it, oldRecord)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                // 先返回首页，再显示吐司，确保吐司在首页上可见
                navController.navigate("main") {
                    popUpTo(0) { inclusive = true }
                }
                val baseMsg = if (recordId != null) "已更新记录" else "已保存记录"
                snackbarHostState.showCustomToast(
                    RecordSyncHelper.calendarHint(calendarResult) ?: baseMsg
                )
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
                        title = "加班时长",
                        summary = SalaryCalculator.formatHours(durationHours),
                        endActions = { DropdownArrowEndAction(MiuixTheme.colorScheme.primary) },
                        onClick = { showDurationPicker = true }
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

        if (showDurationPicker) {
            OverlayDialog(
                show = showDurationPicker,
                title = "选择加班时长（0.5 小时梯度）",
                onDismissRequest = { showDurationPicker = false }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NumberPicker(
                        value = pickDurationIndex,
                        onValueChange = { pickDurationIndex = it },
                        range = 0 until durationOptions.size,
                        label = {
                            val h = durationOptions[it]
                            val hi = h.toInt()
                            val m = ((h - hi) * 60).toInt()
                            if (m > 0) "${hi}h${m}m" else "${hi}h"
                        },
                        modifier = Modifier.heightIn(min = 180.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            durationHours = durationOptions[pickDurationIndex]
                            showDurationPicker = false
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
            Column(modifier = Modifier.fillMaxWidth()) {
                val typeEntries = OvertimeType.entries
                typeEntries.forEachIndexed { index, type ->
                    FullWidthDropdownItem(
                        text = type.label,
                        optionSize = typeEntries.size,
                        isSelected = selectedType == type,
                        index = index,
                        onSelected = {
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
            // 改用 Dropdown(O) 组件呈现请假时长选项：
            // 此前使用 SpinnerItemImpl，与「加班类型」弹窗风格不一致且宽度未撑满
            Column(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(-4 to "半天 (-4小时)", -8 to "全天 (-8小时)")
                options.forEachIndexed { index, option ->
                    FullWidthDropdownItem(
                        text = option.second,
                        optionSize = options.size,
                        isSelected = leaveDuration == option.first,
                        index = index,
                        onSelected = {
                            leaveDuration = options[it].first
                            showLeaveDurationPicker = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 撑满宽度的 Dropdown(O) 选项。
 *
 * 问题根因：miuix 0.9.0 的 [DropdownImpl] 内部 Row 未声明 fillMaxWidth，
 * 其宽度完全由文本内容撑开，因此放进弹窗后整组选项会贴在左侧、
 * 选中勾选标记紧挨文字，而不是右对齐到弹窗边缘。
 *
 * 解决方式：不改写官方组件，而是在测量阶段把 minWidth 抬到 maxWidth，
 * 强制内部 Row 占满可用宽度，其 Arrangement.SpaceBetween 便会把
 * 文本与勾选标记分别推向两端，得到标准下拉弹窗的排版。
 */
@Composable
private fun FullWidthDropdownItem(
    text: String,
    optionSize: Int,
    isSelected: Boolean,
    index: Int,
    onSelected: (Int) -> Unit
) {
    Layout(
        content = {
            DropdownImpl(
                text = text,
                optionSize = optionSize,
                isSelected = isSelected,
                index = index,
                onSelectedIndexChange = onSelected
            )
        }
    ) { measurables, constraints ->
        // 宽度无界时保持原测量行为，避免出现无限宽约束
        val childConstraints = if (constraints.hasBoundedWidth) {
            constraints.copy(minWidth = constraints.maxWidth)
        } else {
            constraints
        }
        val placeable = measurables.first().measure(childConstraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private suspend fun saveRecord(
    repository: OvertimeRepository,
    recordId: Long?,
    date: Date,
    type: OvertimeType,
    durationHours: Double,
    baseSalary: Double,
    rate: Double,
    note: String,
    isLeave: Boolean,
    leaveDuration: Int
): OvertimeRecord? {
    // 请假记录：时长取 leaveDuration（半天 -4 / 全天 -8），工资按标准日薪比例扣减（负值）
    val duration = if (isLeave) leaveDuration.toDouble() else durationHours
    val amount = if (isLeave) SalaryCalculator.calculateLeaveDeduction(baseSalary, duration)
    else SalaryCalculator.calculateOvertimeAmount(baseSalary, type, rate, duration)

    val record = OvertimeRecord(
        id = recordId ?: 0,
        date = date.time,
        type = type,
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
