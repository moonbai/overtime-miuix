package com.mars.overtime.ui.screen
import com.mars.overtime.ui.theme.AppShapes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.util.HolidayDataSource
import com.mars.overtime.util.HolidayManager
import com.mars.overtime.util.RecordSyncHelper
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.content.Context
import androidx.activity.compose.BackHandler
import java.text.SimpleDateFormat
import java.util.*

/**
 * 快速提报对话框：一键记录「今天」的加班。
 *
 * - 日期固定为当天
 * - 加班类型按当天日期自动判定（工作日/休息日/节假日），可手动切换
 * - 提供常用时长快捷选项，也可左右滑动微调（0.5h 步进）
 * - 时长上限与「添加记录」页一致：工作日最多 8h，休息日/节假日最多 12h
 * - 单次弹窗会话只允许提交一次，避免连点产生重复记录
 * - 保存后自动触发推送 / 日历同步 / 自动备份
 */
@Composable
fun QuickSubmitSheet(
    show: Boolean,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository,
    context: Context,
    onDismiss: () -> Unit,
    /** 保存完成回调；参数为需要展示的提示文案（日历同步失败时为具体原因）。 */
    onSaved: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    val baseSalary by settingsRepository.baseSalary.collectAsState(initial = 2200.0)
    val workdayRate by settingsRepository.workdayRate.collectAsState(initial = 1.5)
    val weekendRate by settingsRepository.weekendRate.collectAsState(initial = 2.0)
    val holidayRate by settingsRepository.holidayRate.collectAsState(initial = 3.0)

    val holidayDataSource by settingsRepository.holidayDataSource
        .collectAsState(initial = HolidayDataSource.UAPIS.name)
    val holidayCustomUrl by settingsRepository.holidayCustomUrl.collectAsState(initial = "")
    val holidayMxnzpAppId by settingsRepository.holidayMxnzpAppId.collectAsState(initial = "")
    val holidayMxnzpAppSecret by settingsRepository.holidayMxnzpAppSecret.collectAsState(initial = "")
    val holidayIgnoreHoliday by settingsRepository.holidayIgnoreHoliday.collectAsState(initial = false)

    val today = remember { Date() }
    // 复用同一个格式化器：旧写法在 filter 的每条记录里都 new 一个 SimpleDateFormat
    val dayFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val dateStr = remember { dayFormatter.format(today) }
    val dateLabel = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(today) }

    // 今日已有记录（用于「今日已记录」提示）
    val allRecords by repository.getAllRecords().collectAsState(initial = emptyList())
    val todayRecords = remember(allRecords, dateStr) {
        allRecords.filter { dayFormatter.format(Date(it.date)) == dateStr }
    }

    var selectedType by remember { mutableStateOf(OvertimeType.WORKDAY) }
    var typeManuallyChanged by remember { mutableStateOf(false) }
    var autoDetected by remember { mutableStateOf(false) }
    var durationHours by remember { mutableStateOf(2.0) }
    var note by remember { mutableStateOf("") }
    // 仅用于「提交中...」文案等 UI 反馈，不承担防重复职责
    var saving by remember { mutableStateOf(false) }
    // 一次性提交锁：JVM 级原子布尔，连点在重组前派发也只能有一次拿到锁。
    // 成功入库后不解锁，覆盖「保存完成 → onDismiss → 弹窗退出动画」期间
    // 按钮仍可见可点的窗口。弹窗关闭后本组件仍在组合树中，下次打开时复位。
    val submitGuard = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    // 请假模式：快速提报也支持记请假
    var isLeave by remember { mutableStateOf(false) }
    var leaveDuration by remember { mutableStateOf(-4.0) }
    // 整行与开关共用同一套切换逻辑，避免两处行为分叉
    val toggleLeave: (Boolean) -> Unit = { enabled ->
        isLeave = enabled
        // 切到请假时回到半天档，避免沿用上一次的微调值导致两个选项都不选中
        if (enabled) leaveDuration = -4.0
    }

    // 弹出时复位提交状态并按当天日期自动判定加班类型
    LaunchedEffect(show) {
        if (show) {
            saving = false
            submitGuard.set(false)
            if (!typeManuallyChanged) {
                val source = try { HolidayDataSource.valueOf(holidayDataSource) } catch (_: Exception) { HolidayDataSource.UAPIS }
                HolidayManager.configure(
                    dataSource = source,
                    customUrl = holidayCustomUrl,
                    mxnzpAppId = holidayMxnzpAppId,
                    mxnzpAppSecret = holidayMxnzpAppSecret,
                    ignoreHoliday = holidayIgnoreHoliday
                )
                selectedType = HolidayManager.getOvertimeType(dateStr)
                autoDetected = true
            }
        }
    }

    val rate = when (selectedType) {
        OvertimeType.WORKDAY -> workdayRate
        OvertimeType.WEEKEND -> weekendRate
        OvertimeType.HOLIDAY -> holidayRate
    }

    // 时长上限与「添加记录」页口径一致：工作日最多 8h，休息日/节假日最多 12h
    val maxDurationHours = if (selectedType == OvertimeType.WORKDAY) 8.0 else 12.0
    // 类型切换（手动或自动判定）后，现有时长超过新上限时压到上限
    LaunchedEffect(maxDurationHours) {
        if (!isLeave && durationHours > maxDurationHours) durationHours = maxDurationHours
    }
    val previewAmount = if (isLeave) {
        SalaryCalculator.calculateLeaveDeduction(baseSalary, leaveDuration)
    } else {
        SalaryCalculator.calculateOvertimeAmount(baseSalary, selectedType, rate, durationHours)
    }

    // 弹窗打开时拦截系统返回手势，关闭弹窗而非退出应用（与 MIUIX NavigationBackHandler 双保险）
    BackHandler(enabled = show && !saving) { if (!saving) onDismiss() }

    OverlayDialog(
        show = show,
        title = "快速提报",
        summary = "记录今天（$dateLabel）的加班",
        onDismissRequest = { if (!saving) onDismiss() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 加班薪资
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = SalaryCalculator.formatAmount(previewAmount),
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary
                )
            }

            // 今日已记录提示
            if (todayRecords.isNotEmpty()) {
                Surface(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                    shape = AppShapes.radiusMd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "今日已记录 ${todayRecords.size} 条，继续将新增一条",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            // 记为请假开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 整行可点击切换；落在 Switch 上的点击由 Switch 的 toggleable 消费
                    .clickable { toggleLeave(!isLeave) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "记为请假",
                    style = MiuixTheme.textStyles.body1,
                    color = if (isLeave) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isLeave,
                    onCheckedChange = toggleLeave
                )
            }

            // 加班类型（请假模式下隐藏）
            if (!isLeave) {
                SmallTitle(text = if (autoDetected && !typeManuallyChanged) "加班类型（按日期自动判定）" else "加班类型")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OvertimeType.entries.forEach { type ->
                        val selected = selectedType == type
                        // MIUIX Surface(onClick) 自带标准按压反馈与内容色透传，
                        // 替代原先「Box + selectable + 手写文字色」的组合
                        Surface(
                            onClick = {
                                selectedType = type
                                typeManuallyChanged = true
                            },
                            color = if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = if (selected) MiuixTheme.colorScheme.onPrimary
                            else MiuixTheme.colorScheme.onSecondaryContainer,
                            shape = AppShapes.radiusMd,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = type.label.replace("加班", ""),
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 时长快捷选择：请假只提供半天(4h) / 全天(8h)，加班保留 0.5h 梯度档位
            val durationOptions = if (isLeave) listOf(4.0, 8.0) else listOf(0.5, 1.0, 2.0, 3.0, 4.0)
            val currentDuration = if (isLeave) kotlin.math.abs(leaveDuration) else durationHours
            SmallTitle(
                text = if (isLeave) "请假时长：${SalaryCalculator.formatHours(kotlin.math.abs(leaveDuration))}"
                else "加班时长：${SalaryCalculator.formatHours(durationHours)}"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                durationOptions.forEach { h ->
                    val selected = currentDuration == h
                    Surface(
                        onClick = { if (isLeave) leaveDuration = -h else durationHours = h },
                        color = if (selected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = if (selected) MiuixTheme.colorScheme.onPrimary
                        else MiuixTheme.colorScheme.onSecondaryContainer,
                        shape = AppShapes.radiusMd,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = SalaryCalculator.formatHours(h),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            // 加班时长：左右拖动滑块选择，0.5h 步进吸附。
            // 上限与「添加记录」页一致：工作日最多 8h，休息日/节假日最多 12h。
            // 请假固定半天 / 全天两档，不提供滑块（避免时长与档位不一致）。
            if (!isLeave) {
                Slider(
                    value = durationHours.toFloat(),
                    onValueChange = { value ->
                        // 吸附到 0.5h 档位，避免出现 2.3h 这类与上方快捷档位不一致的值
                        durationHours = (kotlin.math.round(value.toDouble() * 2) / 2.0)
                            .coerceIn(0.5, maxDurationHours)
                    },
                    onValueChangeFinished = {},
                    valueRange = 0.5f..maxDurationHours.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = SalaryCalculator.formatHours(0.5),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = "最多${SalaryCalculator.formatHours(maxDurationHours)}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            // 备注
            TextField(
                value = note,
                onValueChange = { note = it },
                label = "备注（可选）",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = { if (!saving) onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        // 原子锁：同一瞬间无论派发多少次点击，只有第一次能拿到锁
                        if (!submitGuard.compareAndSet(false, true)) return@Button
                        saving = true
                        scope.launch {
                            var inserted = false
                            try {
                                val finalDuration = if (isLeave) leaveDuration else durationHours
                                val amount = if (isLeave) {
                                    SalaryCalculator.calculateLeaveDeduction(baseSalary, leaveDuration)
                                } else {
                                    SalaryCalculator.calculateOvertimeAmount(
                                        baseSalary, selectedType, rate, durationHours
                                    )
                                }
                                val record = OvertimeRecord(
                                    id = 0,
                                    date = today.time,
                                    type = selectedType,
                                    durationHours = finalDuration,
                                    baseSalary = baseSalary,
                                    rate = rate,
                                    amount = amount,
                                    note = note,
                                    isLeave = isLeave
                                )
                                val id = repository.insert(record)
                                inserted = true
                                // 记录已入库，同步环节的失败不应影响提交结果，仅转为提示文案
                                val failures = try {
                                    RecordSyncHelper.afterSave(
                                        context, repository, settingsRepository, record.copy(id = id)
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    listOf("联动同步未完成")
                                }
                                onSaved(RecordSyncHelper.formatHint("已提报今日加班", failures))
                                onDismiss()
                                // 不复位 saving / submitGuard：弹窗关闭后本组件仍在组合树中，
                                // 状态由下次打开时的 LaunchedEffect(show) 统一复位。
                            } catch (e: Exception) {
                                e.printStackTrace()
                                if (inserted) {
                                    // 已入库：即便提示/关闭回调异常也不解锁，绝不产生第二条记录；
                                    // 尝试关闭弹窗，失败也保持锁定直到下次打开时复位。
                                    try { onDismiss() } catch (_: Exception) {}
                                } else {
                                    // 入库前失败才允许重试
                                    saving = false
                                    submitGuard.set(false)
                                }
                            }
                        }
                    },
                    // MIUIX Button 默认是灰色次要配色，主操作按钮需显式指定主色
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (saving) "提交中..." else "提交")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
