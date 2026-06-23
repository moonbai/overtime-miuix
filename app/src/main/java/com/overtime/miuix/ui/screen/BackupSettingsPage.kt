package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun BackupSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    
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
                PreferenceGroup(title = "本地备份") {
                    ListItem(
                        title = "导出数据",
                        summary = "导出为 JSON 文件",
                        leftAction = { Icon(MiuixIcons.Send, contentDescription = null) },
                        onClick = { }
                    )
                    ListItem(
                        title = "导入数据",
                        summary = "从 JSON 文件导入",
                        leftAction = { Icon(MiuixIcons.Download, contentDescription = null) },
                        onClick = { }
                    )
                }
            }
            
            item {
                PreferenceGroup(title = "云端备份") {
                    ListItem(
                        title = "WebDAV 同步",
                        summary = "配置 WebDAV 服务器",
                        leftAction = { Icon(MiuixIcons.CloudFill, contentDescription = null) },
                        onClick = { }
                    )
                }
            }
        }
    }
}
