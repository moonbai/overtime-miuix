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
fun HolidaySettingsPage(navController: NavHostController) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "节假日管理",
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
                PreferenceGroup(title = "节假日数据") {
                    ListItem(
                        title = "更新节假日数据",
                        summary = "从服务器获取最新节假日规则",
                        leftAction = { Icon(MiuixIcons.Useful.Refresh, contentDescription = null) },
                        onClick = { }
                    )
                }
            }
            
            item {
                PreferenceGroup(title = "2026年节假日") {
                    ListItem(
                        title = "元旦",
                        summary = "2026-01-01",
                        onClick = { }
                    )
                    ListItem(
                        title = "春节",
                        summary = "2026-02-14 至 2026-02-21",
                        onClick = { }
                    )
                    ListItem(
                        title = "清明节",
                        summary = "2026-04-04 至 2026-04-06",
                        onClick = { }
                    )
                    ListItem(
                        title = "劳动节",
                        summary = "2026-05-01 至 2026-05-03",
                        onClick = { }
                    )
                    ListItem(
                        title = "端午节",
                        summary = "2026-06-20 至 2026-06-22",
                        onClick = { }
                    )
                    ListItem(
                        title = "国庆节",
                        summary = "2026-10-01 至 2026-10-08",
                        onClick = { }
                    )
                }
            }
        }
    }
}
