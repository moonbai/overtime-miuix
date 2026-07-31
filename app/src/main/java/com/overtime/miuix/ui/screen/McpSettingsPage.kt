package com.overtime.miuix.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import java.net.Inet4Address
import java.net.NetworkInterface
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.mcp.McpHostService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
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

    val deviceIp = remember { getLocalIpAddress() }

    val configJson = remember(deviceIp, mcpPort) {
        """
{
  "mcpServers": {
    "overtime-note": {
      "url": "http://$deviceIp:$mcpPort/mcp"
    }
  }
}
        """.trimIndent()
    }
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "MCP 服务",
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Model Context Protocol (MCP) 服务允许 AI 助手通过标准协议访问您的加班记录数据。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            
            item {
                BasicComponent(
                    title = "启用 MCP 服务",
                    summary = "开启后可通过本地网络访问",
                    endActions = {
                        Switch(
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
                    onClick = {
                        scope.launch {
                            val port = portText.toIntOrNull() ?: 8080
                            settingsRepository.setMcpPort(port)
                        }
                    },
                    enabled = !mcpEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存端口设置")
                }
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
                            text = "访问地址: http://$deviceIp:$mcpPort/mcp",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "MCP 配置 JSON",
                                style = MiuixTheme.textStyles.title3
                            )
                            TextButton(
                                text = "复制",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("MCP Config", configJson)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "配置已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 8.dp
                        ) {
                            Text(
                                text = configJson,
                                style = MiuixTheme.textStyles.footnote1,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 获取设备当前的内网 / 局域网 IPv4 地址。
 * 优先返回私有网段（192.168.x / 10.x / 172.16~31.x），用于生成 MCP 配置中的可访问地址。
 */
private fun getLocalIpAddress(): String {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        val fallback = mutableListOf<String>()
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback || intf.isVirtual) continue
            for (addr in intf.inetAddresses) {
                if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                val ip = addr.hostAddress ?: continue
                if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                    return ip
                }
                fallback.add(ip)
            }
        }
        fallback.firstOrNull() ?: "127.0.0.1"
    } catch (e: Exception) {
        "127.0.0.1"
    }
}
