package com.mars.overtime.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.mcp.McpHostService
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.util.FeaturePermissions
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

    /**
     * 权限只在用户主动启用 MCP 服务（开关 / 启动按钮）的那一刻按需申请：
     * 应用启动流程不会弹出任何权限对话框。权限被拒绝也不回滚开关，服务仍按用户意图启动，
     * 只提示影响（常驻通知不显示 / Android 17+ 局域网被系统丢包）。
     */
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // 结果为空表示用户直接取消了权限请求，视为放弃本次启用，不再拉起服务
        if (result.isEmpty()) return@rememberLauncherForActivityResult
        if (result.values.any { !it }) {
            scope.launch {
                snackbarHostState.showCustomToast(
                    "未授予通知 / 本地网络权限，常驻通知可能不显示、局域网可能无法连接"
                )
            }
        }
        startService()
    }

    fun enableMcpService() {
        val missing = FeaturePermissions.missing(context, FeaturePermissions.forMcpService())
        if (missing.isEmpty()) startService() else permissionLauncher.launch(missing)
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
                SettingsNote(text = "以常驻通知运行，首次启用按需申请权限。")
                SettingsActionItem(
                    title = if (runningPort == null) "启动服务" else "重启服务",
                    summary = "按当前端口重启",
                    onClick = { enableMcpService() }
                )
            }
        }

        item {
            SettingsGroup(title = "服务") {
                SettingsNote(text = "仅建议在可信网络下开启。")
                SettingsSwitchItem(
                    title = "启用 MCP 服务",
                    summary = "本地网络可访问",
                    checked = mcpEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) enableMcpService() else stopService()
                    }
                )
                SettingsTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                    label = "服务端口（1-65535）",
                    enabled = !mcpEnabled
                )
                SettingsNote(text = "服务运行中不可修改端口。")
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
                SettingsNote(text = McpHostService.TOOL_SUMMARY)
                SettingsPreviewText(text = "访问地址：http://$deviceIp:$mcpPort/mcp")
            }
        }

        item {
            SettingsGroup(title = "配置 JSON") {
                SettingsNote(text = "手机与客户端需在同一局域网。")
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
