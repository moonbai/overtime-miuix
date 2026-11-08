package com.mars.overtime.ui.screen
import com.mars.overtime.ui.theme.AppShapes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 基础设置：本页是设置中唯一需要「整组数值一起校验」的页面，因此保留显式保存，
 * 并在分组内明确提示；其余设置项一律即改即存。
 */
@Composable
fun SalarySettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val baseSalary by settingsRepository.baseSalary.collectAsState(initial = 2200.0)
    val workdayRate by settingsRepository.workdayRate.collectAsState(initial = 1.5)
    val weekendRate by settingsRepository.weekendRate.collectAsState(initial = 2.0)
    val holidayRate by settingsRepository.holidayRate.collectAsState(initial = 3.0)
    val deleteConfirmEnabled by settingsRepository.deleteConfirmEnabled.collectAsState(initial = true)
    val workingDaysPerMonth by settingsRepository.workingDaysPerMonth
        .collectAsState(initial = SalaryCalculator.DEFAULT_WORKING_DAYS_PER_MONTH)
    val hoursPerDay by settingsRepository.hoursPerDay
        .collectAsState(initial = SalaryCalculator.DEFAULT_HOURS_PER_DAY)

    var salaryText by remember { mutableStateOf(baseSalary.toString()) }
    var workdayText by remember { mutableStateOf(workdayRate.toString()) }
    var weekendText by remember { mutableStateOf(weekendRate.toString()) }
    var holidayText by remember { mutableStateOf(holidayRate.toString()) }
    var daysText by remember { mutableStateOf(workingDaysPerMonth.toString()) }
    var hoursText by remember { mutableStateOf(hoursPerDay.toString()) }

    // DataStore 值被外部（如备份恢复）改变时同步回输入框
    LaunchedEffect(baseSalary) { salaryText = baseSalary.toString() }
    LaunchedEffect(workdayRate) { workdayText = workdayRate.toString() }
    LaunchedEffect(weekendRate) { weekendText = weekendRate.toString() }
    LaunchedEffect(holidayRate) { holidayText = holidayRate.toString() }
    LaunchedEffect(workingDaysPerMonth) { daysText = workingDaysPerMonth.toString() }
    LaunchedEffect(hoursPerDay) { hoursText = hoursPerDay.toString() }

    // 校验：基础薪资与倍率均须为正；非法输入拦截保存
    fun parsePositive(text: String): Double? = text.toDoubleOrNull()?.takeIf { it > 0 }
    val salaryValid = parsePositive(salaryText) != null
    val workdayValid = parsePositive(workdayText) != null
    val weekendValid = parsePositive(weekendText) != null
    val holidayValid = parsePositive(holidayText) != null
    val daysValid = parsePositive(daysText) != null
    val hoursValid = parsePositive(hoursText) != null
    val allValid = salaryValid && workdayValid && weekendValid && holidayValid &&
            daysValid && hoursValid

    val isDirty = salaryText != baseSalary.toString()
            || workdayText != workdayRate.toString()
            || weekendText != weekendRate.toString()
            || holidayText != holidayRate.toString()
            || daysText != workingDaysPerMonth.toString()
            || hoursText != hoursPerDay.toString()

    // 按当前输入框草稿实时试算：口径与 SalaryCalculator 保持一致，
    // 让用户调整基础薪资/倍率/计薪口径时立刻看到结果，无需先保存再回记录页验证。
    val preview = remember(salaryText, workdayText, weekendText, holidayText, daysText, hoursText) {
        val salary = salaryText.toDoubleOrNull()
        val days = daysText.toDoubleOrNull()
        val hours = hoursText.toDoubleOrNull()
        if (salary == null || days == null || hours == null ||
            salary <= 0 || days <= 0 || hours <= 0
        ) null else {
            val hourly = salary / days / hours
            val rateWorkday = workdayText.toDoubleOrNull() ?: 0.0
            val rateWeekend = weekendText.toDoubleOrNull() ?: 0.0
            val rateHoliday = holidayText.toDoubleOrNull() ?: 0.0
            PreviewResult(
                hourly = hourly,
                daily = salary / days,
                workday = hourly * rateWorkday,
                weekend = hourly * rateWeekend,
                holiday = hourly * rateHoliday
            )
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }

    fun tryExit() {
        if (isDirty) showDiscardDialog = true else navController.popBackStack()
    }

    SettingsScreen(
        title = "基础设置",
        onBack = { tryExit() },
        snackbarHostState = snackbarHostState,
        dialogs = {
            // 未保存修改时返回需二次确认
            OverlayDialog(
                show = showDiscardDialog,
                title = "放弃修改",
                summary = "当前有未保存的修改，确定要放弃吗？",
                onDismissRequest = { showDiscardDialog = false }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showDiscardDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(
                        text = "放弃",
                        onClick = {
                            showDiscardDialog = false
                            navController.popBackStack()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) {
        item {
            SettingsGroup(title = "基础薪资与倍率") {
                SettingsNote(text = "小时薪资 = 基础薪资 ÷ 月计薪天数 ÷ 每日工时\n以下数据修改后需点击底部「保存设置」才会生效。")
                SettingsTextField(
                    value = salaryText,
                    onValueChange = { salaryText = it },
                    label = "基础薪资（元）"
                )
                SettingsTextField(
                    value = workdayText,
                    onValueChange = { workdayText = it },
                    label = "工作日倍率"
                )
                SettingsTextField(
                    value = weekendText,
                    onValueChange = { weekendText = it },
                    label = "休息日倍率"
                )
                SettingsTextField(
                    value = holidayText,
                    onValueChange = { holidayText = it },
                    label = "节假日倍率"
                )
                SettingsTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = "月计薪天数（默认 21.75）"
                )
                SettingsTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = "每日工时（默认 8）"
                )
                if (!allValid) {
                    SettingsNote(text = "基础薪资、各项倍率、计薪天数与每日工时都必须大于 0", isError = true)
                }
                SalaryPreviewCard(preview)
            }
        }

        item {
            SettingsGroup{
                SettingsSwitchItem(
                    title = "记录删除确认",
                    summary = if (deleteConfirmEnabled) {
                        "删除前弹窗二次确认，防止误删"
                    } else {
                        "点删除后立即删除，4 秒内可撤销"
                    },
                    checked = deleteConfirmEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setDeleteConfirmEnabled(enabled) }
                    }
                )
            }
        }

        item {
            SettingsPrimaryButton(
                text = "保存设置",
                onClick = {
                    if (!allValid) {
                        scope.launch { snackbarHostState.showCustomToast("请填写大于 0 的合法数值") }
                    } else {
                        scope.launch {
                            settingsRepository.setBaseSalary(salaryText.toDouble())
                            settingsRepository.setWorkdayRate(workdayText.toDouble())
                            settingsRepository.setWeekendRate(weekendText.toDouble())
                            settingsRepository.setHolidayRate(holidayText.toDouble())
                            settingsRepository.setWorkingDaysPerMonth(daysText.toDouble())
                            settingsRepository.setHoursPerDay(hoursText.toDouble())
                            // 立即同步纯 Kotlin 计算工具，无需等 DataStore Flow 回填
                            SalaryCalculator.configure(daysText.toDouble(), hoursText.toDouble())
                            snackbarHostState.showCustomToast("已保存基础设置")
                            navController.popBackStack()
                        }
                    }
                }
            )
        }
    }

    // 系统返回键同样走二次确认
    BackHandler(enabled = true) { tryExit() }
}

private data class PreviewResult(
    val hourly: Double,
    val daily: Double,
    val workday: Double,
    val weekend: Double,
    val holiday: Double
)

/** 设置页内的实时薪资试算：与记录保存使用同一公式，输入变化即刻刷新。 */
@Composable
private fun SalaryPreviewCard(preview: PreviewResult?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(AppShapes.radiusMd)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Text(
            text = "实时计算",
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (preview == null) {
            Text(
                text = "填写有效数值后显示计算结果",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        } else {
            Text(
                text = "小时薪资 ${SalaryCalculator.formatAmount(preview.hourly)}"
                        + " · 日薪 ${SalaryCalculator.formatAmount(preview.daily)}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "1 小时加班：工作日 ${SalaryCalculator.formatAmount(preview.workday)}"
                        + " / 休息日 ${SalaryCalculator.formatAmount(preview.weekend)}"
                        + " / 节假日 ${SalaryCalculator.formatAmount(preview.holiday)}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "按当前输入值计算，保存后仅对后续新增记录生效",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}
