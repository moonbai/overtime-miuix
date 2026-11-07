package com.overtime.miuix.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import java.net.Inet4Address
import java.net.NetworkInterface
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.mcp.McpHostService
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import kotlinx.coroutines.launch

@Composable
fun McpSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val mcpEnabled by settingsRepository.mcpEnabled.collectAsState(initial = false)
    val mcpPort by settingsRepository.mcpPort.collectAsState(initial = 8080)
    var portText by remember { mutableStateOf(mcpPort.toString()) }

    val deviceIp = remember { getLocalIpAddress() }

    val lanConfigJson = remember(deviceIp, mcpPort) {
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

    val localConfigJson = remember(mcpPort) {
        """
{
  "mcpServers": {
    "overtime-note": {
      "url": "http://127.0.0.1:$mcpPort/mcp"
    }
  }
}
        """.trimIndent()
    }

    fun copyConfig(label: String, json: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MCP Config", json)
        clipboard.setPrimaryClip(clip)
        scope.launch { snackbarHostState.showCustomToast("${label}已复制到剪贴板") }
    }

    SettingsScreen(
        title = "MCP 服务",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    ) {
        item {
            SettingsGroup(title = "服务") {
                SettingsNote(text = "Model Context Protocol 服务允许 AI 助手通过标准协议读取加班记录；仅建议在可信网络下开启。")
                SettingsSwitchItem(
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
                SettingsTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                    label = "服务端口（1-65535）",
                    enabled = !mcpEnabled
                )
                SettingsNote(text = "端口需在服务停止时修改并点击保存，服务运行中不可更改。")
            }
        }

        item {
            SettingsPrimaryButton(
                text = "保存端口设置",
                enabled = !mcpEnabled,
                onClick = {
                    val port = portText.toIntOrNull()?.takeIf { it in 1..65535 }
                    if (port == null) {
                        scope.launch { snackbarHostState.showCustomToast("端口需为 1-65535 的整数") }
                    } else {
                        scope.launch {
                            settingsRepository.setMcpPort(port)
                            snackbarHostState.showCustomToast("端口已保存：$port")
                        }
                    }
                }
            )
        }

        item {
            SettingsGroup(title = "接入信息") {
                SettingsNote(text = "可用工具：add_overtime_record（添加记录）、query_overtime_records（查询记录）、get_monthly_stats（月度统计）。")
                SettingsPreviewText(text = "访问地址：http://$deviceIp:$mcpPort/mcp")
            }
        }

        item {
            SettingsGroup(title = "配置 JSON") {
                SettingsNote(text = "复制以下配置到 AI 客户端即可接入。")
                SettingsButtonRow(
                    firstText = "复制本机配置",
                    onFirstClick = { copyConfig("本机配置", localConfigJson) },
                    secondText = "复制局域网配置",
                    onSecondClick = { copyConfig("局域网配置", lanConfigJson) }
                )
                SettingsPreviewText(
                    text = "本机：http://127.0.0.1:$mcpPort/mcp\n局域网：http://$deviceIp:$mcpPort/mcp"
                )
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
