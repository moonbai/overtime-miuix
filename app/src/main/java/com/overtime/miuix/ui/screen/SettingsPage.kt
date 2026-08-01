package com.overtime.miuix.ui.screen

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun SettingsPage(navController: NavHostController) {
    val context = LocalContext.current
    val versionName = remember { getVersionName(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            SettingsGroup(title = "功能设置") {
                BasicComponent(
                    title = "基础设置",
                    summary = "基础薪资、加班倍率、默认时间",
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
        }

        item {
            SettingsGroup(title = "数据管理") {
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
        }

        item {
            SettingsGroup(title = "个性化") {
                BasicComponent(
                    title = "外观设置",
                    summary = "主题、强调色、底栏样式",
                    startAction = { Icon(MiuixIcons.Background, contentDescription = null) },
                    onClick = { navController.navigate("appearance_settings") }
                )
            }
        }

        item {
            SettingsGroup(title = "MCP 服务") {
                BasicComponent(
                    title = "MCP 服务设置",
                    summary = "配置 Model Context Protocol",
                    startAction = { Icon(MiuixIcons.CloudFill, contentDescription = null) },
                    onClick = { navController.navigate("mcp_settings") }
                )
            }
        }

        item {
            SettingsGroup(title = "关于") {
                BasicComponent(
                    title = "关于应用",
                    summary = "版本 $versionName",
                    startAction = { Icon(MiuixIcons.Info, contentDescription = null) },
                    onClick = { navController.navigate("about") }
                )
            }
        }
    }
}

private fun getVersionName(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: PackageManager.NameNotFoundException) { "1.0.0" }
}
