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
fun SettingsPage(navController: NavHostController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            SmallTitle(text = "功能设置")
            BasicComponent(
                title = "薪资设置",
                summary = "基础薪资、加班倍率",
                startAction = { Icon(MiuixIcons.BankCards, contentDescription = null) },
                onClick = { navController.navigate("salary_settings") }
            )
            BasicComponent(
                title = "推送设置",
                summary = "配置推送渠道",
                startAction = { Icon(MiuixIcons.Alarm, contentDescription = null) },
                onClick = { navController.navigate("push_settings") }
            )
            BasicComponent(
                title = "日历同步",
                summary = "同步到系统日历",
                startAction = { Icon(MiuixIcons.Months, contentDescription = null) },
                onClick = { navController.navigate("calendar_settings") }
            )
        }
        
        item {
            SmallTitle(text = "数据管理")
            BasicComponent(
                title = "备份与恢复",
                summary = "本地/云端备份",
                startAction = { Icon(MiuixIcons.CloudFill, contentDescription = null) },
                onClick = { navController.navigate("backup_settings") }
            )
            BasicComponent(
                title = "节假日管理",
                summary = "更新节假日规则",
                startAction = { Icon(MiuixIcons.Months, contentDescription = null) },
                onClick = { navController.navigate("holiday_settings") }
            )
        }
        
        item {
            SmallTitle(text = "个性化")
            BasicComponent(
                title = "外观设置",
                summary = "主题、强调色、底栏样式",
                startAction = { Icon(MiuixIcons.Background, contentDescription = null) },
                onClick = { navController.navigate("appearance_settings") }
            )
        }
        
        item {
            SmallTitle(text = "MCP 服务")
            BasicComponent(
                title = "MCP 服务设置",
                summary = "配置 Model Context Protocol",
                startAction = { Icon(MiuixIcons.CloudFill, contentDescription = null) },
                onClick = { navController.navigate("mcp_settings") }
            )
        }
        
        item {
            SmallTitle(text = "关于")
            BasicComponent(
                title = "关于应用",
                summary = "版本 1.0.0",
                startAction = { Icon(MiuixIcons.Info, contentDescription = null) },
                onClick = { navController.navigate("about") }
            )
        }
    }
}
