package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.SettingsRepository
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun BackupSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "备份与恢复",
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
                SmallTitle(text = "本地备份")
                BasicComponent(
                    title = "导出数据",
                    summary = "导出为 JSON 文件",
                    startAction = { Icon(MiuixIcons.Download, contentDescription = null) },
                    onClick = { }
                )
                BasicComponent(
                    title = "导入数据",
                    summary = "从 JSON 文件恢复",
                    startAction = { Icon(MiuixIcons.Import, contentDescription = null) },
                    onClick = { }
                )
            }
            item {
                SmallTitle(text = "云端备份")
                BasicComponent(
                    title = "WebDAV 同步",
                    summary = "配置 WebDAV 服务器进行自动同步",
                    startAction = { Icon(MiuixIcons.CloudFill, contentDescription = null) },
                    onClick = { }
                )
            }
        }
    }
}
