package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun HolidaySettingsPage(navController: NavHostController) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "节假日管理",
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
                SmallTitle(text = "数据管理")
                BasicComponent(
                    title = "更新节假日数据",
                    summary = "从云端同步最新节假日",
                    startAction = { Icon(MiuixIcons.Refresh, contentDescription = null) },
                    onClick = { }
                )
            }
            item {
                SmallTitle(text = "2026年节假日")
                BasicComponent(title = "元旦", summary = "1月1日", onClick = { })
                BasicComponent(title = "春节", summary = "2月14日 - 2月21日", onClick = { })
                BasicComponent(title = "清明节", summary = "4月4日 - 4月6日", onClick = { })
                BasicComponent(title = "劳动节", summary = "5月1日 - 5月3日", onClick = { })
                BasicComponent(title = "端午节", summary = "6月20日 - 6月22日", onClick = { })
                BasicComponent(title = "国庆节", summary = "10月1日 - 10月8日", onClick = { })
            }
        }
    }
}
