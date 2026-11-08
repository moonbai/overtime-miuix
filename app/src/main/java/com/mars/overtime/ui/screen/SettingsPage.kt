package com.mars.overtime.ui.screen

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.ui.component.AppFeaturePalette
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

/**
 * 设置首页：按「用户想做什么」而非技术模块分组，入口名称与二级页顶栏标题保持一致。
 * 所有入口都是普通的 [SettingsActionItem] 跳转行，不再有折叠的「开发者选项」层级，
 * 避免同一个设置页里出现两种交互方式。
 */
@Composable
fun SettingsPage(navController: NavHostController, bottomReserve: Dp = 0.dp) {
    val context = LocalContext.current
    val versionName = remember { getVersionName(context) }

    SettingsList(
        modifier = Modifier.fillMaxSize(),
        bottomReserve = bottomReserve
    ) {
        // 入口图标与「关于」页功能宫格同步：语义重新选配 + 共用 AppFeaturePalette 色板，
        // 旧版用 Update 表示日历同步、Alarm 表示推送、Background 表示外观，语义错配且全是黑白裸图标。
        item {
            SettingsGroup {
                SettingsActionItem(
                    title = "基础设置",
                    summary = "基础薪资、加班倍率与删除确认",
                    icon = MiuixIcons.BankCards,
                    iconTint = AppFeaturePalette.salary,
                    onClick = { navController.navigate("salary_settings") }
                )
                SettingsActionItem(
                    title = "类型设置",
                    summary = "日期判定数据源、缓存与调休规则",
                    icon = MiuixIcons.Scan,
                    iconTint = AppFeaturePalette.smart,
                    onClick = { navController.navigate("holiday_settings") }
                )
                SettingsActionItem(
                    title = "日历同步",
                    summary = "保存记录后自动写入系统日历",
                    icon = MiuixIcons.Months,
                    iconTint = AppFeaturePalette.calendar,
                    onClick = { navController.navigate("calendar_settings") }
                )
            }
        }

        item {
            SettingsGroup{
                SettingsActionItem(
                    title = "备份与恢复",
                    summary = "本地导入导出、自动备份与 WebDAV 同步",
                    icon = MiuixIcons.Backup,
                    iconTint = AppFeaturePalette.cloud,
                    onClick = { navController.navigate("backup_settings") }
                )
                SettingsActionItem(
                    title = "推送设置",
                    summary = "保存记录后推送到所选渠道",
                    icon = MiuixIcons.Send,
                    iconTint = AppFeaturePalette.push,
                    onClick = { navController.navigate("push_settings") }
                )
                SettingsActionItem(
                    title = "MCP 服务",
                    summary = "允许 AI 助手通过 MCP 读写加班记录",
                    icon = MiuixIcons.MindMap,
                    iconTint = AppFeaturePalette.mcp,
                    onClick = { navController.navigate("mcp_settings") }
                )
            }
        }


        item {
            SettingsGroup{
                SettingsActionItem(
                    title = "外观设置",
                    summary = "主题、强调色、底栏与快捷操作",
                    icon = MiuixIcons.Theme,
                    iconTint = AppFeaturePalette.appearance,
                    onClick = { navController.navigate("appearance_settings") }
                )
            }
        }

        item {
            SettingsGroup{
                SettingsActionItem(
                    title = "关于应用",
                    summary = "版本 $versionName",
                    icon = MiuixIcons.Info,
                    iconTint = AppFeaturePalette.about,
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
