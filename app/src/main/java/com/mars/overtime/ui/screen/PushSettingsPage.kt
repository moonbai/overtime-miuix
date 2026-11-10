package com.mars.overtime.ui.screen

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.push.PushManager
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

@Composable
fun PushSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val pushChannel by settingsRepository.pushChannel.collectAsState(initial = "none")
    // 预览与测试推送使用用户当前配置，字段数值才有参考意义
    val baseSalary by settingsRepository.baseSalary.collectAsState(initial = 2200.0)
    val weekendRate by settingsRepository.weekendRate.collectAsState(initial = 2.0)

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

    // 选项第一项即「关闭推送」，选中后同时关闭推送总开关
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
        channelOptions.map { (_, label) -> DropdownItem(text = label) }
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

    /** 与真实推送走同一套载荷构造，保证预览即所得 */
    fun sampleRecord(): OvertimeRecord {
        val hours = 2.0
        return OvertimeRecord(
            id = 0,
            date = System.currentTimeMillis(),
            type = OvertimeType.WEEKEND,
            durationHours = hours,
            baseSalary = baseSalary,
            rate = weekendRate,
            amount = SalaryCalculator.calculateOvertimeAmount(
                baseSalary, OvertimeType.WEEKEND, weekendRate, hours
            ),
            note = "测试推送",
            isLeave = false
        )
    }

    val previewText = remember(baseSalary, weekendRate) {
        PushManager.buildText(sampleRecord())
    }

    SettingsScreen(
        title = "推送设置",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    ) {
        item {
            SettingsGroup(title = "推送渠道") {
                OverlaySpinnerPreference(
                    items = channelItems,
                    selectedIndex = selectedIndex,
                    title = "推送渠道",
                    summary = "选择渠道即启用",
                    onSelectedIndexChange = { index ->
                        val key = channelOptions[index].first
                        scope.launch {
                            settingsRepository.setPushChannel(key)
                            settingsRepository.setPushEnabled(key != "none")
                        }
                    }
                )
                SettingsNote(text = "配置修改即时生效。")
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
                SettingsGroup(title = "当前状态") {
                    SettingsNote(text = "未选择渠道，推送已关闭。")
                }
            }
        }

        item {
            SettingsGroup(title = "消息内容") {
                SettingsNote(text = "自定义 WebHook 默认发送 JSON；请求头 Content-Type: text/plain 时改为纯文本。")
                SettingsPreviewText(text = previewText)
            }
        }

        item {
            SettingsPrimaryButton(
                text = "发送测试推送",
                onClick = {
                    scope.launch {
                        if (pushChannel == "none") {
                            snackbarHostState.showCustomToast("请先选择推送渠道")
                            return@launch
                        }
                        val ok = PushManager.sendToSelectedChannel(pushChannel, buildConfigMap(), sampleRecord())
                        snackbarHostState.showCustomToast(if (ok) "测试推送成功" else "测试推送失败")
                    }
                }
            )
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
    fields.forEach { state ->
        SettingsTextField(
            value = state.value,
            onValueChange = state.onValueChange,
            label = state.label
        )
    }
}
