package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.ArrowLeft

@Composable
fun CalendarSettingsPage(navController: NavHostController) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "日历同步",
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
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                SwitchItem(
                    title = "同步到系统日历",
                    summary = "将加班记录同步到系统日历",
                    checked = false,
                    onCheckedChange = { }
                )
            }
            
            item {
                PreferenceGroup(title = "同步设置") {
                    ListItem(
                        title = "同步账户",
                        summary = "选择日历账户",
                        onClick = { }
                    )
                }
            }
        }
    }
}
