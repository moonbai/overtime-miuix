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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

/**
 * 设置首页：按「用户想做什么」而非技术模块分组，入口名称与二级页顶栏标题保持一致。
 * 所有入口都是普通的 [SettingsActionItem] 跳转行，不再有折叠的「开发者选项」层级，
 * 避免同一个设置页里出现两种交互方式。
 *
 * 图标规范：全部走 [SettingsActionItem] 的单色主题色渲染（默认 primary），
 * 与底栏导航、FAB 同一套语言；不再为每个入口套彩色底盒，
 * 避免列表里出现一排花花绿绿的色块。图标按语义选配，统一取色于主题。
 */
@Composable
fun SettingsPage(
    navController: NavHostController,
    topReserve: Dp = 0.dp,
    bottomReserve: Dp = 0.dp
) {
    val context = LocalContext.current
    val versionName = remember { getVersionName(context) }

    SettingsList(
        modifier = Modifier.fillMaxSize(),
        topReserve = topReserve,
        bottomReserve = bottomReserve
    ) {
        // 入口图标语义选配：基础设置=BankCards（薪资）、类型设置=Scan（智能判定）、
        // 日历同步=Months、备份与恢复=Backup、推送设置=Send、MCP 服务=MindMap、
        // 外观设置=Theme、关于应用=Info。统一单色主题色，不再各自配色。
        item {
            SettingsGroup {
                SettingsActionItem(
                    title = "基础设置",
                    summary = "基础薪资、加班倍率与删除确认",
                    icon = MiuixIcons.BankCards,
                    onClick = { navController.navigate("salary_settings") }
                )
                SettingsActionItem(
                    title = "类型设置",
                    summary = "日期判定数据源、缓存与调休规则",
                    icon = MiuixIcons.Scan,
                    onClick = { navController.navigate("holiday_settings") }
                )
                SettingsActionItem(
                    title = "日历同步",
                    summary = "保存记录后自动写入系统日历",
                    icon = MiuixIcons.Months,
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
                    onClick = { navController.navigate("backup_settings") }
                )
                SettingsActionItem(
                    title = "推送设置",
                    summary = "保存记录后推送到所选渠道",
                    icon = MiuixIcons.Send,
                    onClick = { navController.navigate("push_settings") }
                )
                SettingsActionItem(
                    title = "MCP 服务",
                    summary = "允许 AI 助手通过 MCP 读写加班记录",
                    icon = MiuixIcons.MindMap,
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
