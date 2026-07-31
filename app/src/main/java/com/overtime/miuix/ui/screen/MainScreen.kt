package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun MainScreen(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val bottomBarStyle by settingsRepository.bottomBarStyle.collectAsState(initial = "ICON_TEXT")
    
    val navMode = when (bottomBarStyle) {
        "ICON_ONLY" -> NavigationBarDisplayMode.IconOnly
        "TEXT_ONLY" -> NavigationBarDisplayMode.IconWithSelectedLabel
        else -> NavigationBarDisplayMode.IconAndText
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = when (selectedTab) {
                    0 -> "加班记录"
                    1 -> "统计"
                    2 -> "设置"
                    else -> "加班记录"
                }
            )
        },
        bottomBar = {
            NavigationBar(mode = navMode) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = MiuixIcons.All,
                    label = "首页"
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = MiuixIcons.Months,
                    label = "统计"
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = MiuixIcons.Settings,
                    label = "设置"
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate("add_record")
                    }
                ) {
                    Icon(MiuixIcons.Add, contentDescription = "添加记录")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> HomePage(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository
                )
                1 -> StatisticsPage(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository
                )
                2 -> SettingsPage(navController = navController)
            }
        }
    }
}
