package com.overtime.miuix.ui.screen

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置首页：按「用户想做什么」而非技术模块分组，入口名称与二级页顶栏标题保持一致。
 * 所有分组统一使用 [SettingsGroup] + [SettingsActionItem]，高级项折叠在同一张卡片内。
 */
@Composable
fun SettingsPage(navController: NavHostController, bottomReserve: Dp = 0.dp) {
    val context = LocalContext.current
    val versionName = remember { getVersionName(context) }
    // 折叠状态跨 tab 切换保留，避免用户每次回到设置页都要重新展开
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    SettingsList(
        modifier = Modifier.fillMaxSize(),
        bottomReserve = bottomReserve
    ) {
        item {
            SettingsGroup(title = "记录与计算") {
                SettingsActionItem(
                    title = "薪资与记录",
                    summary = "基础薪资、加班倍率与删除确认",
                    icon = MiuixIcons.BankCards,
                    onClick = { navController.navigate("salary_settings") }
                )
                SettingsActionItem(
                    title = "节假日管理",
                    summary = "日期判定数据源、缓存与调休规则",
                    icon = MiuixIcons.Months,
                    onClick = { navController.navigate("holiday_settings") }
                )
            }
        }

        item {
            SettingsGroup(title = "联动与通知") {
                SettingsActionItem(
                    title = "推送设置",
                    summary = "保存记录后推送到所选渠道",
                    icon = MiuixIcons.Alarm,
                    onClick = { navController.navigate("push_settings") }
                )
                SettingsActionItem(
                    title = "日历同步",
                    summary = "保存记录后自动写入系统日历",
                    icon = MiuixIcons.Update,
                    onClick = { navController.navigate("calendar_settings") }
                )
            }
        }

        item {
            SettingsGroup(title = "数据与备份") {
                SettingsActionItem(
                    title = "备份与恢复",
                    summary = "本地导入导出、自动备份与 WebDAV 同步",
                    icon = MiuixIcons.CloudFill,
                    onClick = { navController.navigate("backup_settings") }
                )
            }
        }

        item {
            SettingsGroup(title = "个性化") {
                SettingsActionItem(
                    title = "外观设置",
                    summary = "主题、强调色、底栏与快捷操作",
                    icon = MiuixIcons.Background,
                    onClick = { navController.navigate("appearance_settings") }
                )
            }
        }

        item {
            // 开发者选项默认折叠，折叠头与展开项同处一张卡片，避免「孤立行 + 独立卡片」的割裂感
            SettingsGroup(title = "高级") {
                BasicComponent(
                    title = "开发者选项",
                    summary = "MCP 服务等实验性功能",
                    startAction = { Icon(MiuixIcons.Settings, contentDescription = null) },
                    endActions = { DropdownArrowEndAction(MiuixTheme.colorScheme.primary) },
                    onClick = { showAdvanced = !showAdvanced }
                )
                if (showAdvanced) {
                    SettingsActionItem(
                        title = "MCP 服务",
                        summary = "配置 Model Context Protocol 局域网接入",
                        icon = MiuixIcons.Link,
                        onClick = { navController.navigate("mcp_settings") }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "关于") {
                SettingsActionItem(
                    title = "关于应用",
                    summary = "版本 $versionName",
                    icon = MiuixIcons.Info,
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
