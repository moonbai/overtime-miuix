package com.overtime.miuix.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.mcp.McpHostService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.ArrowLeft
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun McpSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val mcpEnabled by settingsRepository.mcpEnabled.collectAsState(initial = false)
    val mcpPort by settingsRepository.mcpPort.collectAsState(initial = 8080)
    var portText by remember { mutableStateOf(mcpPort.toString()) }
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "MCP 服务",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(MiuixIcons.Useful.ArrowLeft, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Model Context Protocol (MCP) 服务允许 AI 助手通过标准协议访问您的加班记录数据。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariant
                )
            }
            
            item {
                SwitchItem(
                    title = "启用 MCP 服务",
                    summary = "开启后可通过本地网络访问",
                    checked = mcpEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setMcpEnabled(enabled)
                            if (enabled) {
                                McpHostService.start(context, mcpPort)
                            } else {
                                McpHostService.stop(context)
                            }
                        }
                    }
                )
            }
            
            item {
                TextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                    label = "服务端口",
                    enabled = !mcpEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Button(
                    text = "保存端口设置",
                    onClick = {
                        scope.launch {
                            val port = portText.toIntOrNull() ?: 8080
                            settingsRepository.setMcpPort(port)
                        }
                    },
                    enabled = !mcpEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "可用工具",
                            style = MiuixTheme.textStyles.title3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• add_overtime_record - 添加加班记录")
                        Text("• query_overtime_records - 查询记录")
                        Text("• get_monthly_stats - 获取月度统计")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "访问地址: http://<设备IP>:$mcpPort/mcp",
                            style = MiuixTheme.textStyles.caption,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
