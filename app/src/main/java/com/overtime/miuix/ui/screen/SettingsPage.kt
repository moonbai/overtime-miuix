package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.*

@Composable
fun SettingsPage(navController: NavHostController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            PreferenceGroup(title = "功能设置") {
                ListItem(
                    title = "薪资设置",
                    summary = "基础薪资、加班倍率",
                    leftAction = { Icon(MiuixIcons.Useful.Wallet, contentDescription = null) },
                    onClick = { navController.navigate("salary_settings") }
                )
                ListItem(
                    title = "推送设置",
                    summary = "配置推送渠道",
                    leftAction = { Icon(MiuixIcons.Useful.Notification, contentDescription = null) },
                    onClick = { navController.navigate("push_settings") }
                )
                ListItem(
                    title = "日历同步",
                    summary = "同步到系统日历",
                    leftAction = { Icon(MiuixIcons.Useful.Calendar, contentDescription = null) },
                    onClick = { navController.navigate("calendar_settings") }
                )
            }
        }
        
        item {
            PreferenceGroup(title = "数据管理") {
                ListItem(
                    title = "备份与恢复",
                    summary = "本地/云端备份",
                    leftAction = { Icon(MiuixIcons.Useful.Cloud, contentDescription = null) },
                    onClick = { navController.navigate("backup_settings") }
                )
                ListItem(
                    title = "节假日管理",
                    summary = "更新节假日规则",
                    leftAction = { Icon(MiuixIcons.Useful.Date, contentDescription = null) },
                    onClick = { navController.navigate("holiday_settings") }
                )
            }
        }
        
        item {
            PreferenceGroup(title = "个性化") {
                ListItem(
                    title = "外观设置",
                    summary = "主题、强调色、底栏样式",
                    leftAction = { Icon(MiuixIcons.Useful.Theme, contentDescription = null) },
                    onClick = { navController.navigate("appearance_settings") }
                )
            }
        }
        
        item {
            PreferenceGroup(title = "MCP 服务") {
                ListItem(
                    title = "MCP 服务设置",
                    summary = "配置 Model Context Protocol",
                    leftAction = { Icon(MiuixIcons.Useful.Server, contentDescription = null) },
                    onClick = { navController.navigate("mcp_settings") }
                )
            }
        }
        
        item {
            PreferenceGroup(title = "关于") {
                ListItem(
                    title = "关于应用",
                    summary = "版本 1.0.0",
                    leftAction = { Icon(MiuixIcons.Useful.Info, contentDescription = null) },
                    onClick = { navController.navigate("about") }
                )
            }
        }
    }
}
