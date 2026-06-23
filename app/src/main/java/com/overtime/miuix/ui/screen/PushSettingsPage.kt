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
fun PushSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val pushEnabled by settingsRepository.pushEnabled.collectAsState(initial = false)
    val pushChannel by settingsRepository.pushChannel.collectAsState(initial = "none")
    
    val channels = listOf(
        "none" to "不启用",
        "dingtalk" to "钉钉",
        "feishu" to "飞书",
        "wxpusher" to "WxPusher",
        "telegram" to "Telegram",
        "discord" to "Discord",
        "custom" to "自定义 WebHook"
    )
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "推送设置",
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
                SwitchItem(
                    title = "启用推送",
                    summary = "开启后可在设置时间推送加班统计",
                    checked = pushEnabled,
                    onCheckedChange = { scope.launch { settingsRepository.setPushEnabled(it) } }
                )
            }
            
            item {
                PreferenceGroup(title = "推送渠道") {
                    channels.forEach { (value, label) ->
                        ListItem(
                            title = label,
                            onClick = { scope.launch { settingsRepository.setPushChannel(value) } },
                            rightAction = { RadioButton(selected = pushChannel == value, onClick = null) }
                        )
                    }
                }
            }
        }
    }
}
