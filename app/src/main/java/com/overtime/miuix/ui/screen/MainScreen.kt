package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Add
import top.yukonga.miuix.kmp.icon.icons.useful.Home
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import top.yukonga.miuix.kmp.icon.icons.useful.Statistics

@Composable
fun MainScreen(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val bottomBarStyle by settingsRepository.bottomBarStyle.collectAsState(initial = "ICON_TEXT")
    val quickSubmit by settingsRepository.quickSubmit.collectAsState(initial = false)
    
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
            when (bottomBarStyle) {
                "ICON_ONLY" -> NavigationBar(
                    items = listOf(
                        NavigationItem("首页", icon = MiuixIcons.Useful.Home),
                        NavigationItem("统计", icon = MiuixIcons.Useful.Statistics),
                        NavigationItem("设置", icon = MiuixIcons.Useful.Settings)
                    ),
                    selected = selectedTab,
                    onClick = { selectedTab = it },
                    showLabel = false
                )
                "TEXT_ONLY" -> NavigationBar(
                    items = listOf(
                        NavigationItem("首页"),
                        NavigationItem("统计"),
                        NavigationItem("设置")
                    ),
                    selected = selectedTab,
                    onClick = { selectedTab = it },
                    showIcon = false
                )
                else -> NavigationBar(
                    items = listOf(
                        NavigationItem("首页", icon = MiuixIcons.Useful.Home),
                        NavigationItem("统计", icon = MiuixIcons.Useful.Statistics),
                        NavigationItem("设置", icon = MiuixIcons.Useful.Settings)
                    ),
                    selected = selectedTab,
                    onClick = { selectedTab = it }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        if (quickSubmit) {
                            navController.navigate("add_record")
                        }
                    }
                ) {
                    Icon(MiuixIcons.Useful.Add, contentDescription = "添加记录")
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
                    repository = repository
                )
                2 -> SettingsPage(navController = navController)
            }
        }
    }
}
