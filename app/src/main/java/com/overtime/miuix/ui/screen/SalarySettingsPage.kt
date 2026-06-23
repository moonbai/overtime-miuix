package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.ArrowLeft

@Composable
fun SalarySettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val baseSalary by settingsRepository.baseSalary.collectAsState(initial = 2200.0)
    val workdayRate by settingsRepository.workdayRate.collectAsState(initial = 1.5)
    val weekendRate by settingsRepository.weekendRate.collectAsState(initial = 2.0)
    val holidayRate by settingsRepository.holidayRate.collectAsState(initial = 3.0)
    
    var salaryText by remember { mutableStateOf(baseSalary.toString()) }
    var workdayText by remember { mutableStateOf(workdayRate.toString()) }
    var weekendText by remember { mutableStateOf(weekendRate.toString()) }
    var holidayText by remember { mutableStateOf(holidayRate.toString()) }
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "薪资设置",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(MiuixIcons.Useful.ArrowLeft, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                Button(
                    text = "保存设置",
                    onClick = {
                        scope.launch {
                            settingsRepository.setBaseSalary(salaryText.toDoubleOrNull() ?: 2200.0)
                            settingsRepository.setWorkdayRate(workdayText.toDoubleOrNull() ?: 1.5)
                            settingsRepository.setWeekendRate(weekendText.toDoubleOrNull() ?: 2.0)
                            settingsRepository.setHolidayRate(holidayText.toDoubleOrNull() ?: 3.0)
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
