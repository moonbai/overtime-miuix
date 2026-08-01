package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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

    var selectedType by remember { mutableStateOf(OvertimeType.WORKDAY) }
    var typeManuallyChanged by remember { mutableStateOf(false) }
    var autoDetected by remember { mutableStateOf(false) }
    var durationHours by remember { mutableStateOf(2.0) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    // 弹出时按当天日期自动判定加班类型
    LaunchedEffect(show) {
        if (show && !typeManuallyChanged) {
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

    val rate = when (selectedType) {
        OvertimeType.WORKDAY -> workdayRate
        OvertimeType.WEEKEND -> weekendRate
        OvertimeType.HOLIDAY -> holidayRate
    }
    val previewAmount = SalaryCalculator.calculateOvertimeAmount(baseSalary, selectedType, rate, durationHours)

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

            // 加班类型
            SmallTitle(text = if (autoDetected && !typeManuallyChanged) "加班类型（按日期自动判定）" else "加班类型")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OvertimeType.entries.forEach { type ->
                    val selected = selectedType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = selected,
                                onClick = {
                                    selectedType = type
                                    typeManuallyChanged = true
                                }
                            )
                    ) {
                        Surface(
                            color = if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.secondaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = type.label.replace("加班", ""),
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MiuixTheme.colorScheme.onPrimary
                                else MiuixTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 加班时长快捷选择
            SmallTitle(text = "加班时长：${SalaryCalculator.formatHours(durationHours)}")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1.0, 2.0, 3.0, 4.0).forEach { h ->
                    val selected = durationHours == h
                    Surface(
                        color = if (selected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.secondaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .selectable(selected = selected, onClick = { durationHours = h })
                    ) {
                        Text(
                            text = "${h.toInt()}h",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MiuixTheme.colorScheme.onPrimary
                            else MiuixTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            // 时长微调
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
                        if (saving) return@Button
                        saving = true
                        scope.launch {
                            try {
                                val amount = SalaryCalculator.calculateOvertimeAmount(
                                    baseSalary, selectedType, rate, durationHours
                                )
                                val record = OvertimeRecord(
                                    id = 0,
                                    date = today.time,
                                    type = selectedType,
                                    durationHours = durationHours,
                                    baseSalary = baseSalary,
                                    rate = rate,
                                    amount = amount,
                                    note = note,
                                    isLeave = false
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
                                saving = false
                                onSaved(
                                    RecordSyncHelper.calendarHint(calendarResult) ?: "已提报今日加班"
                                )
                                onDismiss()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (saving) "提交中..." else "提交", color = MiuixTheme.colorScheme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
