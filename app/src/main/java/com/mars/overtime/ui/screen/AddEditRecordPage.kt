package com.mars.overtime.ui.screen

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.ui.component.AppConfirmFab
import com.mars.overtime.ui.component.LocalFabAnchor
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.util.HolidayDataSource
import com.mars.overtime.util.HolidayManager
import com.mars.overtime.util.RecordSyncHelper
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppShapes
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

    // 对齐参照：首页右下角「加号」FAB 的屏幕锚点（MainScreen 布局时发布）。
    // 确认按钮改用同一锚点 + 同一 end 边距 + 同一 56dp 直径，
    // 使「点加号 → 打开本页 → 确认」落在同一坐标，手指无需移动。
    // 旧位置为 MIUIX Scaffold 默认 FabPosition.End：右 12dp、底 12dp+inset、
    // 默认 60dp 直径，与入口加号（右 16dp、底 底栏高度+16dp、56dp）不一致。
    val fabAnchorBottom = LocalFabAnchor.current.bottom

    val baseSalary by settingsRepository.baseSalary.collectAsState(initial = 2200.0)
    val workdayRate by settingsRepository.workdayRate.collectAsState(initial = 1.5)
    val weekendRate by settingsRepository.weekendRate.collectAsState(initial = 2.0)
    val holidayRate by settingsRepository.holidayRate.collectAsState(initial = 3.0)

    var selectedDate by remember { mutableStateOf(Date()) }
    var selectedType by remember { mutableStateOf(OvertimeType.WORKDAY) }
    var durationHours by remember { mutableStateOf(2.0) }
    var note by remember { mutableStateOf("") }
    var isLeave by remember { mutableStateOf(false) }
    // 请假时长：只提供半天 4 小时 / 全天 8 小时两档，存负值表示工资扣减
    var leaveDuration by remember { mutableStateOf(-4.0) }
    // 一次性保存锁：JVM 级原子布尔，不依赖 Compose 状态快照与重组时机，
    // 连点在重组发生前派发也能可靠拦下。成功保存后立即返回首页（页面销毁，
    // 锁随实例消失），仅入库前失败时解锁允许重试。
    val saveGuard = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    // 用户是否手动改过加班类型：为 true 时不再按日期自动覆盖
    var typeManuallyChanged by remember { mutableStateOf(false) }
    // 当前日期自动判定得到的类型标签（用于展示提示）
    var autoTypeHint by remember { mutableStateOf<String?>(null) }

    // 请假时长选择弹窗显隐
    var showLeaveDurationPicker by remember { mutableStateOf(false) }

    // 请假时长选项：半天 -4.0、全天 -8.0
    val leaveOptions = remember { listOf(-4.0, -8.0) }
    var pickLeaveIndex by remember { mutableIntStateOf(0) } // 默认 -4.0（半天）

    // 节假日数据源配置
    val holidayDataSource by settingsRepository.holidayDataSource
        .collectAsState(initial = HolidayDataSource.UAPIS.name)
    val holidayCustomUrl by settingsRepository.holidayCustomUrl.collectAsState(initial = "")
    val holidayMxnzpAppId by settingsRepository.holidayMxnzpAppId.collectAsState(initial = "")
    val holidayMxnzpAppSecret by settingsRepository.holidayMxnzpAppSecret.collectAsState(initial = "")
    val holidayIgnoreHoliday by settingsRepository.holidayIgnoreHoliday.collectAsState(initial = false)

    // 同步配置到 HolidayManager，保证按日期判定时使用用户所选数据源
    LaunchedEffect(holidayDataSource, holidayCustomUrl, holidayMxnzpAppId, holidayMxnzpAppSecret, holidayIgnoreHoliday) {
        val source = try { HolidayDataSource.valueOf(holidayDataSource) } catch (_: Exception) { HolidayDataSource.UAPIS }
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

    // 时长选择器：以 0.5 小时为梯度；工作日最多 8h，休息日（休息日/节假日）最多 12h
    var pickDurationIndex by remember { mutableIntStateOf(3) }
    val maxDurationHours = maxDurationFor(selectedType)
    val durationOptions = remember(maxDurationHours) {
        (0 until (maxDurationHours / 0.5).toInt()).map { (it + 1) * 0.5 } // 0.5, 1.0, ..., maxDurationHours
    }

    fun indexOfDuration(hours: Double): Int {
        val idx = ((hours / 0.5).roundToLong() - 1).toInt()
        return idx.coerceIn(0, durationOptions.size - 1)
    }

    // 类型变化（手动切换/按日期自动判定）后，现有时长超过新上限时压到上限
    LaunchedEffect(maxDurationHours) {
        if (!isLeave && durationHours > maxDurationHours) durationHours = maxDurationHours
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
                SalaryCalculator.calculateLeaveDeduction(baseSalary, leaveDuration)
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
                // 已存请假时长就近吸附到半天 / 全天两档（>=6h 视为全天）
                if (it.isLeave) {
                    val wholeDay = kotlin.math.abs(it.durationHours) >= 6.0
                    leaveDuration = if (wholeDay) -8.0 else -4.0
                    pickLeaveIndex = if (wholeDay) 1 else 0
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

    // 统一保存逻辑：右下角悬浮确认按钮入口
    // compareAndSet 是原子操作：同一瞬间无论派发多少次点击，只有第一次能拿到锁，
    // 后续全部直接返回，不依赖 Compose 重组或状态快照的时序。
    fun performSave() {
        if (!saveGuard.compareAndSet(false, true)) return
        scope.launch {
            var inserted = false
            try {
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
                if (saved != null) {
                    // 记录已入库（新建 insert / 编辑 update 均成功），此后任何异常
                    // 都不得解锁，否则用户重试会再产生一条记录。
                    inserted = true
                    // 同步动作在当前协程内串行执行并整体兜底：
                    // 记录本身已入库，后续推送/日历/备份的任何异常都不得影响保存结果与页面跳转。
                    val calendarResult = try {
                        RecordSyncHelper.afterSave(context, repository, settingsRepository, saved, oldRecord)
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
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // 仅"入库前失败"解锁允许重试；成功路径页面已返回首页并销毁，
                // 锁随页面实例一起消失，无需复位。
                if (!inserted) saveGuard.set(false)
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // 外层 Box 与 MainScreen 承载入口加号 FAB 的 Box 同为 Scaffold 的满屏内容层，
        // 因此两边以 Alignment.BottomEnd + 相同 padding 计算出的屏幕坐标完全一致。
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                // 底部预留随加号锚点联动：确认按钮上移后，末项仍不会被遮挡
                contentPadding = PaddingValues(
                    start = AppDimens.pageHorizontal,
                    top = AppDimens.pageVertical,
                    end = AppDimens.pageHorizontal,
                    bottom = fabAnchorBottom + AppDimens.fabSize + AppDimens.pageVertical
                ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.groupSpacing)
            ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = AppDimens.cardCorner
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
                        onClick = {
                            // 打开前把选择器索引同步到当前值（超上限时就近压到上限档）
                            pickDurationIndex = indexOfDuration(durationHours)
                            showDurationPicker = true
                        }
                    )
                }

                // 编辑场景下曾手动改过类型：提供「恢复按日期自动判定」入口
                if (typeManuallyChanged) {
                    item {
                        TextButton(
                            text = "恢复按日期自动判定类型",
                            onClick = { typeManuallyChanged = false }
                        )
                    }
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
                    // 整行可点击切换；落在 Switch 上的点击由 Switch 自己消费
                    onClick = { isLeave = !isLeave },
                    endActions = {
                        Switch(
                            checked = isLeave,
                            onCheckedChange = { isLeave = it }
                        )
                    }
                )
            }

            // 请假时长选择（半天 4h / 全天 8h），仅在请假模式下显示，位于请假开关下方
            if (isLeave) {
                item {
                    BasicComponent(
                        title = "请假时长",
                        summary = SalaryCalculator.formatHours(kotlin.math.abs(leaveDuration)),
                        endActions = { DropdownArrowEndAction(MiuixTheme.colorScheme.primary) },
                        onClick = {
                            pickLeaveIndex = if (leaveDuration <= -6.0) 1 else 0
                            showLeaveDurationPicker = true
                        }
                    )
                }
            }
        }

        // 确认按钮：与首页「加号」FAB 同一屏幕坐标（同一锚点、同一 end 边距、同一 56dp 直径）。
        // 不再使用 Scaffold 的 floatingActionButton 槽位——MIUIX 默认 FabPosition.End
        // 会把按钮固定在「右 12dp / 底 12dp + inset / 60dp」，与入口加号错位。
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AppDimens.pageHorizontal, bottom = fabAnchorBottom)
        ) {
            AppConfirmFab(onClick = { performSave() })
        }

        // 以下弹窗必须位于 Scaffold 内容体内，才能访问 Scaffold 提供的弹窗宿主并显示
        if (showDatePicker) {
            BackHandler(enabled = true) { showDatePicker = false }
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
            BackHandler(enabled = true) { showDurationPicker = false }
            OverlayDialog(
                show = showDurationPicker,
                title = "选择加班时长",
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
                        label = { SalaryCalculator.formatHours(durationOptions[it]) },
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

        BackHandler(enabled = showTypePicker) { showTypePicker = false }
        OverlayDialog(
            show = showTypePicker,
            title = "选择类型",
            onDismissRequest = { showTypePicker = false }
        ) {
            // DropdownImpl 的背景是 drawRect 直角绘制，外层整体裁成圆角卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.radiusMd)
            ) {
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

        BackHandler(enabled = showLeaveDurationPicker) { showLeaveDurationPicker = false }
        OverlayDialog(
            show = showLeaveDurationPicker,
            title = "选择请假时长",
            onDismissRequest = { showLeaveDurationPicker = false }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberPicker(
                    value = pickLeaveIndex,
                    onValueChange = { pickLeaveIndex = it },
                    range = 0 until leaveOptions.size,
                    label = {
                        val h = kotlin.math.abs(leaveOptions[it])
                        "${SalaryCalculator.formatHours(h)}${if (h >= 8.0) "（全天）" else "（半天）"}"
                    },
                    modifier = Modifier.heightIn(min = 180.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        leaveDuration = leaveOptions[pickLeaveIndex]
                        showLeaveDurationPicker = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("确定")
                }
            }
        }
        } // 关闭承载确认按钮的满屏 Box
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

/** 加班时长上限：工作日最多 8h，休息日/节假日 最多 12h。 */
private fun maxDurationFor(type: OvertimeType): Double =
    if (type == OvertimeType.WORKDAY) 8.0 else 12.0

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
    leaveDuration: Double
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
