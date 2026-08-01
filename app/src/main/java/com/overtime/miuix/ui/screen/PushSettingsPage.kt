package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.push.PushManager
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.*

@Composable
fun PushSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val pushChannel by settingsRepository.pushChannel.collectAsState(initial = "none")

    val dingtalk by settingsRepository.pushDingTalk.collectAsState(initial = "")
    val dingtalkSecret by settingsRepository.pushDingTalkSecret.collectAsState(initial = "")
    val feishu by settingsRepository.pushFeishu.collectAsState(initial = "")
    val feishuSecret by settingsRepository.pushFeishuSecret.collectAsState(initial = "")
    val wecom by settingsRepository.pushWeCom.collectAsState(initial = "")
    val wecomSecret by settingsRepository.pushWeComSecret.collectAsState(initial = "")
    val wxpusher by settingsRepository.pushWxPusher.collectAsState(initial = "")
    val telegram by settingsRepository.pushTelegram.collectAsState(initial = "")
    val telegramChatId by settingsRepository.pushTelegramChatId.collectAsState(initial = "")
    val discord by settingsRepository.pushDiscord.collectAsState(initial = "")
    val discordUsername by settingsRepository.pushDiscordUsername.collectAsState(initial = "")
    val custom by settingsRepository.pushCustom.collectAsState(initial = "")
    val customHeaders by settingsRepository.pushCustomHeaders.collectAsState(initial = "")

    var dingtalkText by remember { mutableStateOf(dingtalk) }
    var dingtalkSecretText by remember { mutableStateOf(dingtalkSecret) }
    var feishuText by remember { mutableStateOf(feishu) }
    var feishuSecretText by remember { mutableStateOf(feishuSecret) }
    var wecomText by remember { mutableStateOf(wecom) }
    var wecomSecretText by remember { mutableStateOf(wecomSecret) }
    var wxpusherText by remember { mutableStateOf(wxpusher) }
    var telegramText by remember { mutableStateOf(telegram) }
    var telegramChatIdText by remember { mutableStateOf(telegramChatId) }
    var discordText by remember { mutableStateOf(discord) }
    var discordUsernameText by remember { mutableStateOf(discordUsername) }
    var customText by remember { mutableStateOf(custom) }
    var customHeadersText by remember { mutableStateOf(customHeaders) }

    LaunchedEffect(dingtalk) { dingtalkText = dingtalk }
    LaunchedEffect(dingtalkSecret) { dingtalkSecretText = dingtalkSecret }
    LaunchedEffect(feishu) { feishuText = feishu }
    LaunchedEffect(feishuSecret) { feishuSecretText = feishuSecret }
    LaunchedEffect(wecom) { wecomText = wecom }
    LaunchedEffect(wecomSecret) { wecomSecretText = wecomSecret }
    LaunchedEffect(wxpusher) { wxpusherText = wxpusher }
    LaunchedEffect(telegram) { telegramText = telegram }
    LaunchedEffect(telegramChatId) { telegramChatIdText = telegramChatId }
    LaunchedEffect(discord) { discordText = discord }
    LaunchedEffect(discordUsername) { discordUsernameText = discordUsername }
    LaunchedEffect(custom) { customText = custom }
    LaunchedEffect(customHeaders) { customHeadersText = customHeaders }

    // 推送渠道选项：第一项为“关闭推送”（对应 none），不选即默认关闭
    val channelOptions = listOf(
        "none" to "关闭推送",
        "dingtalk" to "钉钉",
        "feishu" to "飞书",
        "wecom" to "企业微信",
        "wxpusher" to "WxPusher",
        "telegram" to "Telegram",
        "discord" to "Discord",
        "custom" to "自定义 WebHook"
    )
    val channelItems = remember(channelOptions) {
        channelOptions.map { (_, label) -> SpinnerEntry(title = label) }
    }
    val selectedIndex = remember(pushChannel) {
        channelOptions.indexOfFirst { it.first == pushChannel }.coerceAtLeast(0)
    }

    fun buildConfigMap(): Map<String, String> = mapOf(
        "push_dingtalk" to dingtalkText,
        "push_dingtalk_secret" to dingtalkSecretText,
        "push_feishu" to feishuText,
        "push_feishu_secret" to feishuSecretText,
        "push_wecom" to wecomText,
        "push_wecom_secret" to wecomSecretText,
        "push_wxpusher" to wxpusherText,
        "push_telegram" to telegramText,
        "push_telegram_chatid" to telegramChatIdText,
        "push_discord" to discordText,
        "push_discord_username" to discordUsernameText,
        "push_custom" to customText,
        "push_custom_headers" to customHeadersText
    )

    fun sampleRecord(): OvertimeRecord {
        val now = System.currentTimeMillis()
        return OvertimeRecord(
            id = 0,
            date = now,
            type = OvertimeType.WORKDAY,
            durationHours = 2.0,
            baseSalary = 2200.0,
            rate = 1.5,
            amount = 0.0,
            note = "测试推送",
            isLeave = false
        )
    }

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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                SettingsGroup(title = "推送渠道") {
                    // 使用 SpinnerPref(O)：直接选择渠道，不选（关闭推送）默认关闭推送功能
                    OverlaySpinnerPreference(
                        items = channelItems,
                        selectedIndex = selectedIndex,
                        title = "推送渠道",
                        summary = "不选则默认关闭推送",
                        onSelectedIndexChange = { index ->
                            val key = channelOptions[index].first
                            scope.launch {
                                settingsRepository.setPushChannel(key)
                                settingsRepository.setPushEnabled(key != "none")
                            }
                        }
                    )
                }
            }

            if (pushChannel != "none") {
                item {
                    SettingsGroup(title = "${channelOptions.first { it.first == pushChannel }.second} 配置") {
                        when (pushChannel) {
                        "dingtalk" -> PushConfigFields(
                            listOf(
                                TextFieldState("Webhook 地址", dingtalkText) { dingtalkText = it; scope.launch { settingsRepository.setPushDingTalk(it) } },
                                TextFieldState("加签密钥(可选)", dingtalkSecretText) { dingtalkSecretText = it; scope.launch { settingsRepository.setPushDingTalkSecret(it) } }
                            )
                        )
                        "feishu" -> PushConfigFields(
                            listOf(
                                TextFieldState("Webhook 地址", feishuText) { feishuText = it; scope.launch { settingsRepository.setPushFeishu(it) } },
                                TextFieldState("签名密钥(可选)", feishuSecretText) { feishuSecretText = it; scope.launch { settingsRepository.setPushFeishuSecret(it) } }
                            )
                        )
                        "wecom" -> PushConfigFields(
                            listOf(
                                TextFieldState("Webhook 地址", wecomText) { wecomText = it; scope.launch { settingsRepository.setPushWeCom(it) } },
                                TextFieldState("加签密钥(可选)", wecomSecretText) { wecomSecretText = it; scope.launch { settingsRepository.setPushWeComSecret(it) } }
                            )
                        )
                        "wxpusher" -> PushConfigFields(
                            listOf(
                                TextFieldState("API 地址(含 token)", wxpusherText) { wxpusherText = it; scope.launch { settingsRepository.setPushWxPusher(it) } }
                            )
                        )
                        "telegram" -> PushConfigFields(
                            listOf(
                                TextFieldState("API 地址", telegramText) { telegramText = it; scope.launch { settingsRepository.setPushTelegram(it) } },
                                TextFieldState("Chat ID", telegramChatIdText) { telegramChatIdText = it; scope.launch { settingsRepository.setPushTelegramChatId(it) } }
                            )
                        )
                        "discord" -> PushConfigFields(
                            listOf(
                                TextFieldState("Webhook 地址", discordText) { discordText = it; scope.launch { settingsRepository.setPushDiscord(it) } },
                                TextFieldState("显示名称(可选)", discordUsernameText) { discordUsernameText = it; scope.launch { settingsRepository.setPushDiscordUsername(it) } }
                            )
                        )
                        "custom" -> PushConfigFields(
                            listOf(
                                TextFieldState("请求地址", customText) { customText = it; scope.launch { settingsRepository.setPushCustom(it) } },
                                TextFieldState("自定义请求头(每行 Header: Value)", customHeadersText) { customHeadersText = it; scope.launch { settingsRepository.setPushCustomHeaders(it) } }
                            )
                        )
                        }
                    }
                }
            } else {
                item {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "未选择推送渠道，推送功能已关闭。点击上方选择一种渠道以开启。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            if (pushChannel == "none") {
                                snackbarHostState.showCustomToast("请先选择推送渠道")
                                return@launch
                            }
                            val ok = PushManager.sendToSelectedChannel(pushChannel, buildConfigMap(), sampleRecord())
                            snackbarHostState.showCustomToast(if (ok) "测试推送成功" else "测试推送失败")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) { Text("测试推送") }
            }
        }
    }
}

private data class TextFieldState(
    val label: String,
    val value: String,
    val onValueChange: (String) -> Unit
)

@Composable
private fun PushConfigFields(fields: List<TextFieldState>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        fields.forEach { state ->
            TextField(
                value = state.value,
                onValueChange = state.onValueChange,
                label = state.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}
