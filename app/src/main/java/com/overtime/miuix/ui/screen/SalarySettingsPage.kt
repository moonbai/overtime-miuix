package com.overtime.miuix.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 薪资与记录：本页是设置中唯一需要「整组数值一起校验」的页面，因此保留显式保存，
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

    var salaryText by remember { mutableStateOf(baseSalary.toString()) }
    var workdayText by remember { mutableStateOf(workdayRate.toString()) }
    var weekendText by remember { mutableStateOf(weekendRate.toString()) }
    var holidayText by remember { mutableStateOf(holidayRate.toString()) }

    // DataStore 值被外部（如备份恢复）改变时同步回输入框
    LaunchedEffect(baseSalary) { salaryText = baseSalary.toString() }
    LaunchedEffect(workdayRate) { workdayText = workdayRate.toString() }
    LaunchedEffect(weekendRate) { weekendText = weekendRate.toString() }
    LaunchedEffect(holidayRate) { holidayText = holidayRate.toString() }

    // 校验：基础薪资与倍率均须为正；非法输入拦截保存
    fun parsePositive(text: String): Double? = text.toDoubleOrNull()?.takeIf { it > 0 }
    val salaryValid = parsePositive(salaryText) != null
    val workdayValid = parsePositive(workdayText) != null
    val weekendValid = parsePositive(weekendText) != null
    val holidayValid = parsePositive(holidayText) != null
    val allValid = salaryValid && workdayValid && weekendValid && holidayValid

    val isDirty = salaryText != baseSalary.toString()
            || workdayText != workdayRate.toString()
            || weekendText != weekendRate.toString()
            || holidayText != holidayRate.toString()

    var showDiscardDialog by remember { mutableStateOf(false) }

    fun tryExit() {
        if (isDirty) showDiscardDialog = true else navController.popBackStack()
    }

    SettingsScreen(
        title = "薪资与记录",
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
            SettingsGroup(title = "薪资与倍率") {
                SettingsNote(text = "按 21.75 天 / 8 小时折算小时薪资；本组数值需点击底部「保存设置」才会生效。")
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
                if (!allValid) {
                    SettingsNote(text = "基础薪资与各项倍率都必须大于 0", isError = true)
                }
            }
        }

        item {
            SettingsGroup(title = "记录交互") {
                SettingsSwitchItem(
                    title = "删除二次确认",
                    summary = if (deleteConfirmEnabled) {
                        "删除前弹窗确认，防止误删"
                    } else {
                        "已关闭：点删除立即移除，4 秒内可撤销"
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
