package com.overtime.miuix.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.mcp.McpHostService
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

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

    // 读取服务实际监听的端口，而不是只看 DataStore 里的开关：
    // 端口占用、权限缺失、系统回收都会造成「开关已开启但端口无人监听」。
    var runningPort by remember { mutableStateOf<Int?>(McpHostService.runningPortOrNull()) }
    var deviceIp by remember { mutableStateOf(getLocalIpAddress()) }

    // 页面回到前台时刷新运行状态与内网 IP（网络可能在离开页面期间切换）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                runningPort = McpHostService.runningPortOrNull()
                deviceIp = getLocalIpAddress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    /** 启动请求发出后稍等片刻再读取真实状态，给服务 onStartCommand 留出绑定端口的时间。 */
    fun startService() {
        scope.launch {
            val requested = McpHostService.start(context, mcpPort)
            if (!requested) {
                settingsRepository.setMcpEnabled(false)
                snackbarHostState.showCustomToast("服务启动失败，请检查通知权限或更换端口")
                return@launch
            }
            settingsRepository.setMcpEnabled(true)
            delay(400)
            runningPort = McpHostService.runningPortOrNull()
            snackbarHostState.showCustomToast(
                if (runningPort != null) "MCP 服务已启动，端口 $mcpPort"
                else "服务未能监听端口，请更换端口后重试"
            )
        }
    }

    fun stopService() {
        McpHostService.stop(context)
        runningPort = null
        scope.launch { settingsRepository.setMcpEnabled(false) }
    }

    SettingsScreen(
        title = "MCP 服务",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    ) {
        item {
            SettingsGroup(title = "运行状态") {
                SettingsStatusText(
                    insideCard = true,
                    text = when (val port = runningPort) {
                        null -> if (mcpEnabled) {
                            "开关已开启，但服务未运行（可能启动失败或被回收），请点击下方重启"
                        } else {
                            "服务未运行"
                        }
                        else -> "服务运行中 · 监听端口 $port"
                    }
                )
                SettingsNote(text = "服务以常驻通知方式运行。局域网访问需要「本地网络」与「通知」权限；\n若客户端连不上，先确认此处显示「运行中」。")
                SettingsActionItem(
                    title = if (runningPort == null) "启动服务" else "重启服务",
                    summary = "按当前端口重新拉起并刷新状态",
                    onClick = { startService() }
                )
            }
        }

        item {
            SettingsGroup(title = "服务") {
                SettingsNote(text = "Model Context Protocol 服务允许 AI 助手通过标准协议读写加班记录；仅建议在可信网络下开启。")
                SettingsSwitchItem(
                    title = "启用 MCP 服务",
                    summary = "开启后可通过本地网络访问",
                    checked = mcpEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) startService() else stopService()
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
                SettingsNote(text = "可用工具：\n add_overtime_record（添加加班/请假记录）\n query_overtime_records（查询记录）\n get_monthly_stats（月度统计）。")
                SettingsPreviewText(text = "访问地址：http://$deviceIp:$mcpPort/mcp")
            }
        }

        item {
            SettingsGroup(title = "配置 JSON") {
                SettingsNote(text = "复制以下配置到 AI 客户端即可接入。\n手机与客户端需在同一局域网。")
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
