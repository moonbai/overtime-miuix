package com.overtime.miuix.ui.screen

import android.widget.Toast
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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.*

@Composable
fun PushSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushEnabled by settingsRepository.pushEnabled.collectAsState(initial = false)
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

    val channels = listOf(
        "dingtalk" to "钉钉",
        "feishu" to "飞书",
        "wecom" to "企业微信",
        "wxpusher" to "WxPusher",
        "telegram" to "Telegram",
        "discord" to "Discord",
        "custom" to "自定义 WebHook"
    )

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
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 18); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 20)
        val end = cal.timeInMillis
        return OvertimeRecord(
            id = 0,
            date = now,
            type = OvertimeType.WORKDAY,
            startTime = start,
            endTime = end,
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                BasicComponent(
                    title = "启用推送",
                    summary = "开启后可在保存记录时推送加班统计",
                    endActions = {
                        Switch(
                            checked = pushEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setPushEnabled(it) } }
                        )
                    }
                )
            }

            item {
                Column {
                    SmallTitle(text = "推送渠道")
                    channels.forEach { (value, label) ->
                        BasicComponent(
                            title = label,
                            onClick = { scope.launch { settingsRepository.setPushChannel(value) } },
                            endActions = { RadioButton(selected = pushChannel == value, onClick = null) }
                        )
                    }
                }
            }

            item {
                Column {
                    SmallTitle(text = "${channels.firstOrNull { it.first == pushChannel }?.second ?: "渠道"} 配置")
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
                    else -> Box(modifier = Modifier.padding(16.dp)) {
                        Text("请先选择一个推送渠道", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                }
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            if (pushChannel == "none") {
                                Toast.makeText(context, "请先选择推送渠道", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val ok = PushManager.sendToSelectedChannel(pushChannel, buildConfigMap(), sampleRecord())
                            Toast.makeText(context, if (ok) "测试推送成功" else "测试推送失败", Toast.LENGTH_SHORT).show()
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
