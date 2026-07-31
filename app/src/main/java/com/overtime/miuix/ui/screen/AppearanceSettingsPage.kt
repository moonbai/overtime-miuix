package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.model.BottomBarStyle
import com.overtime.miuix.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun AppearanceSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val themeMode by settingsRepository.themeMode.collectAsState(initial = "system")
    val bottomBarStyle by settingsRepository.bottomBarStyle.collectAsState(initial = "ICON_TEXT")
    val quickSubmit by settingsRepository.quickSubmit.collectAsState(initial = false)
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "外观设置",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                SmallTitle(text = "主题")
                BasicComponent(
                    title = "跟随系统",
                    onClick = { scope.launch { settingsRepository.setThemeMode("system") } },
                    endActions = { RadioButton(selected = themeMode == "system", onClick = null) }
                )
                BasicComponent(
                    title = "浅色模式",
                    onClick = { scope.launch { settingsRepository.setThemeMode("light") } },
                    endActions = { RadioButton(selected = themeMode == "light", onClick = null) }
                )
                BasicComponent(
                    title = "深色模式",
                    onClick = { scope.launch { settingsRepository.setThemeMode("dark") } },
                    endActions = { RadioButton(selected = themeMode == "dark", onClick = null) }
                )
            }
            
            item {
                SmallTitle(text = "底栏样式")
                BottomBarStyle.entries.forEach { style ->
                    BasicComponent(
                        title = style.label,
                        onClick = { scope.launch { settingsRepository.setBottomBarStyle(style.name) } },
                        endActions = { RadioButton(selected = bottomBarStyle == style.name, onClick = null) }
                    )
                }
            }
            
            item {
                SmallTitle(text = "快捷功能")
                BasicComponent(
                    title = "快速提报模式",
                    summary = "首页显示快捷添加按钮",
                    endActions = {
                        Switch(
                            checked = quickSubmit,
                            onCheckedChange = { scope.launch { settingsRepository.setQuickSubmit(it) } }
                        )
                    }
                )
            }
        }
    }
}
