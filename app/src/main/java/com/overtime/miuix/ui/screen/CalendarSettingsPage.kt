package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun CalendarSettingsPage(navController: NavHostController) {
    var syncEnabled by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "日历同步",
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
                SmallTitle(text = "同步设置")
                BasicComponent(
                    title = "启用日历同步",
                    summary = "自动同步加班记录到系统日历",
                    endActions = {
                        Switch(
                            checked = syncEnabled,
                            onCheckedChange = { syncEnabled = it }
                        )
                    }
                )
            }
            item {
                SmallTitle(text = "日历选择")
                BasicComponent(
                    title = "选择日历账户",
                    summary = "请选择要同步到的日历",
                    onClick = { }
                )
            }
        }
    }
}
