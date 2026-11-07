package com.overtime.miuix.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    // 删除二次确认开关状态（默认开启），关闭后首页点删除立即移除并提供撤销
    val deleteConfirmEnabled by settingsRepository.deleteConfirmEnabled.collectAsState(initial = true)

    var salaryText by remember { mutableStateOf(baseSalary.toString()) }
    var workdayText by remember { mutableStateOf(workdayRate.toString()) }
    var weekendText by remember { mutableStateOf(weekendRate.toString()) }
    var holidayText by remember { mutableStateOf(holidayRate.toString()) }

    // Sync local state when DataStore values change externally
    LaunchedEffect(baseSalary) { salaryText = baseSalary.toString() }
    LaunchedEffect(workdayRate) { workdayText = workdayRate.toString() }
    LaunchedEffect(weekendRate) { weekendText = weekendRate.toString() }
    LaunchedEffect(holidayRate) { holidayText = holidayRate.toString() }

    // 校验：基础薪资与倍率均须为正；非法输入触发保存拦截
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

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "基础设置",
                navigationIcon = {
                    IconButton(onClick = { tryExit() }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SmallTitle(text = "薪资与倍率")
            }
            item {
                TextField(
                    value = salaryText,
                    onValueChange = { salaryText = it },
                    label = "基础薪资（元）",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                TextField(
                    value = workdayText,
                    onValueChange = { workdayText = it },
                    label = "工作日倍率",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                TextField(
                    value = weekendText,
                    onValueChange = { weekendText = it },
                    label = "周末倍率",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                TextField(
                    value = holidayText,
                    onValueChange = { holidayText = it },
                    label = "节假日倍率",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!allValid) {
                item {
                    Text(
                        text = "基础薪资与各项倍率都必须大于 0",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error
                    )
                }
            }

            item {
                SmallTitle(text = "交互")
            }
            item {
                BasicComponent(
                    title = "删除二次确认",
                    summary = if (deleteConfirmEnabled) {
                        "删除前弹窗确认，防止误删"
                    } else {
                        "已关闭：点删除立即移除，6 秒内可撤销"
                    },
                    endActions = {
                        Switch(
                            checked = deleteConfirmEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsRepository.setDeleteConfirmEnabled(enabled) }
                            }
                        )
                    }
                )
            }

            item {
                Button(
                    onClick = {
                        if (!allValid) {
                            scope.launch { snackbarHostState.showCustomToast("请填写大于 0 的合法数值") }
                            return@Button
                        }
                        scope.launch {
                            settingsRepository.setBaseSalary(salaryText.toDouble())
                            settingsRepository.setWorkdayRate(workdayText.toDouble())
                            settingsRepository.setWeekendRate(weekendText.toDouble())
                            settingsRepository.setHolidayRate(holidayText.toDouble())
                            snackbarHostState.showCustomToast("已保存基础设置")
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存设置")
                }
            }
        }
    }

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

    // 系统返回键同样走二次确认
    BackHandler(enabled = true) { tryExit() }
}
