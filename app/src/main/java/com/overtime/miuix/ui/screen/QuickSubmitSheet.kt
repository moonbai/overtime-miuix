package com.overtime.miuix.ui.screen
import com.overtime.miuix.ui.theme.AppShapes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.util.HolidayDataSource
import com.overtime.miuix.util.HolidayManager
import com.overtime.miuix.util.RecordSyncHelper
import com.overtime.miuix.util.SalaryCalculator
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
 * - 提供常用时长快捷选项，也可微调
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

    val holidayDataSource by settingsRepository.holidayDataSource.collectAsState(initial = "TIMOR")
    val holidayCustomUrl by settingsRepository.holidayCustomUrl.collectAsState(initial = "")
    val holidayMxnzpAppId by settingsRepository.holidayMxnzpAppId.collectAsState(initial = "")
    val holidayMxnzpAppSecret by settingsRepository.holidayMxnzpAppSecret.collectAsState(initial = "")
    val holidayIgnoreHoliday by settingsRepository.holidayIgnoreHoliday.collectAsState(initial = false)

    val today = remember { Date() }
    val dateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today) }
    val dateLabel = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(today) }

    // 今日已有记录（用于「今日已记录」提示）
    val allRecords by repository.getAllRecords().collectAsState(initial = emptyList())
    val todayRecords = remember(allRecords, dateStr) {
        allRecords.filter { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.date)) == dateStr }
    }

    var selectedType by remember { mutableStateOf(OvertimeType.WORKDAY) }
    var typeManuallyChanged by remember { mutableStateOf(false) }
    var autoDetected by remember { mutableStateOf(false) }
    var durationHours by remember { mutableStateOf(2.0) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    // 一次性提交锁：本次弹窗会话只允许入库一次。成功入库后不解锁，
    // 覆盖「保存完成 → onDismiss → 弹窗退出动画」期间按钮仍可见可点的连点窗口；
    // 快速双击时重组尚未发生，靠这个快照状态拦截比按钮 enabled 更可靠。
    // 弹窗关闭后本组件仍留在组合树中（show 只控制显隐），下次打开时统一复位。
    var submitted by remember { mutableStateOf(false) }
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
            submitted = false
            if (!typeManuallyChanged) {
                val source = try { HolidayDataSource.valueOf(holidayDataSource) } catch (_: Exception) { HolidayDataSource.TIMOR }
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
            // 预估薪资
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
                            // 不能用 h.toInt()：0.5h 会被渲染成「0h」
                            text = if (h % 1.0 == 0.0) "${h.toInt()}h" else "${h}h",
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
            // 时长微调只对加班开放：请假固定半天 / 全天两档，
            // 保留 ±0.5h 会绕过选项产生 3.5h、8.5h 之类的时长。
            if (!isLeave) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { durationHours = (durationHours - 0.5).coerceAtLeast(0.5) },
                        modifier = Modifier.weight(1f)
                    ) { Text("-0.5h") }
                    Button(
                        onClick = { durationHours = (durationHours + 0.5).coerceAtMost(24.0) },
                        modifier = Modifier.weight(1f)
                    ) { Text("+0.5h") }
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
                        // 一次性锁：协程启动前就置位，连点在重组发生前也会被拦下；
                        // 成功入库后不解锁，覆盖弹窗退出动画期间按钮仍可见的窗口。
                        if (saving || submitted) return@Button
                        saving = true
                        submitted = true
                        scope.launch {
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
                                // 记录已入库，同步环节的失败不应影响提交结果，仅转为提示文案
                                val calendarResult = try {
                                    RecordSyncHelper.afterSave(
                                        context, repository, settingsRepository, record.copy(id = id)
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    null
                                }
                                onSaved(
                                    RecordSyncHelper.calendarHint(calendarResult) ?: "已提报今日加班"
                                )
                                onDismiss()
                                // 不复位 saving / submitted：弹窗关闭后本组件仍在组合树中，
                                // 状态由下次打开时的 LaunchedEffect(show) 统一复位。
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // 仅入库失败才允许重试；成功路径绝不解锁
                                saving = false
                                submitted = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    // 由 Button 透传内容色，不手写 onPrimary（否则在部分主题下文字发灰）
                    Text(if (saving) "提交中..." else "提交")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
