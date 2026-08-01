package com.overtime.miuix.ui.screen

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
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
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

    // 加班默认时间配置
    val defaultStartTime by settingsRepository.defaultStartTime.collectAsState(initial = "17:00")
    val endTimeAlign by settingsRepository.endTimeAlign.collectAsState(initial = "HALF")

    var salaryText by remember { mutableStateOf(baseSalary.toString()) }
    var workdayText by remember { mutableStateOf(workdayRate.toString()) }
    var weekendText by remember { mutableStateOf(weekendRate.toString()) }
    var holidayText by remember { mutableStateOf(holidayRate.toString()) }
    var startText by remember { mutableStateOf(defaultStartTime) }

    // Sync local state when DataStore values change externally
    LaunchedEffect(baseSalary) { salaryText = baseSalary.toString() }
    LaunchedEffect(workdayRate) { workdayText = workdayRate.toString() }
    LaunchedEffect(weekendRate) { weekendText = weekendRate.toString() }
    LaunchedEffect(holidayRate) { holidayText = holidayRate.toString() }
    LaunchedEffect(defaultStartTime) { startText = defaultStartTime }

    // 结束时间对齐选项：HALF=30分对齐，HOUR=整点对齐
    val alignOptions = listOf("HALF" to "30分对齐", "HOUR" to "整点对齐")
    val alignItems = remember(alignOptions) { alignOptions.map { SpinnerEntry(title = it.second) } }
    val alignSelected = remember(endTimeAlign) { alignOptions.indexOfFirst { it.first == endTimeAlign }.coerceAtLeast(0) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "基础设置",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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

            item {
                SmallTitle(text = "加班默认时间")
            }
            item {
                TextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = "默认开始时间（HH:mm，如 17:00）",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OverlaySpinnerPreference(
                    items = alignItems,
                    selectedIndex = alignSelected,
                    title = "结束时间对齐",
                    summary = "新建记录时结束时间按此粒度取整",
                    onSelectedIndexChange = { index ->
                        scope.launch { settingsRepository.setEndTimeAlign(alignOptions[index].first) }
                    }
                )
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            // 校验开始时间格式 HH:mm
                            val startValid = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(startText.trim())
                            settingsRepository.setBaseSalary(salaryText.toDoubleOrNull() ?: 2200.0)
                            settingsRepository.setWorkdayRate(workdayText.toDoubleOrNull() ?: 1.5)
                            settingsRepository.setWeekendRate(weekendText.toDoubleOrNull() ?: 2.0)
                            settingsRepository.setHolidayRate(holidayText.toDoubleOrNull() ?: 3.0)
                            settingsRepository.setDefaultStartTime(if (startValid) startText.trim() else "17:00")
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
}
