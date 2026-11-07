package com.overtime.miuix.ui.screen

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import com.overtime.miuix.util.HolidayDataSource
import com.overtime.miuix.util.HolidayManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import java.time.Year

/**
 * 节假日管理：所有配置项即改即存，不再放置误导性的「保存配置」按钮。
 * 「忽略节假日」属于日期判定规则，对全部数据源生效，因此放在通用分组中。
 */
@Composable
fun HolidaySettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val dataSource by settingsRepository.holidayDataSource.collectAsState(initial = "TIMOR")
    val customUrl by settingsRepository.holidayCustomUrl.collectAsState(initial = "")
    val mxnzpAppId by settingsRepository.holidayMxnzpAppId.collectAsState(initial = "")
    val mxnzpAppSecret by settingsRepository.holidayMxnzpAppSecret.collectAsState(initial = "")
    val ignoreHoliday by settingsRepository.holidayIgnoreHoliday.collectAsState(initial = false)

    var customUrlText by remember { mutableStateOf(customUrl) }
    var mxnzpAppIdText by remember { mutableStateOf(mxnzpAppId) }
    var mxnzpAppSecretText by remember { mutableStateOf(mxnzpAppSecret) }
    var status by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val selectedSource = remember(dataSource) {
        try { HolidayDataSource.valueOf(dataSource) } catch (_: Exception) { HolidayDataSource.TIMOR }
    }

    LaunchedEffect(customUrl) { customUrlText = customUrl }
    LaunchedEffect(mxnzpAppId) { mxnzpAppIdText = mxnzpAppId }
    LaunchedEffect(mxnzpAppSecret) { mxnzpAppSecretText = mxnzpAppSecret }

    // 同步配置到 HolidayManager
    LaunchedEffect(dataSource, customUrl, mxnzpAppId, mxnzpAppSecret, ignoreHoliday) {
        HolidayManager.configure(
            dataSource = selectedSource,
            customUrl = customUrl,
            mxnzpAppId = mxnzpAppId,
            mxnzpAppSecret = mxnzpAppSecret,
            ignoreHoliday = ignoreHoliday
        )
    }

    val currentYear = Year.now().value.toString()

    SettingsScreen(
        title = "节假日管理",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    ) {
        status?.let { message ->
            item { SettingsStatusText(text = message) }
        }

        item {
            val sourceItems = remember {
                HolidayDataSource.entries.map { DropdownItem(text = it.label) }
            }
            val sourceSelected = remember(dataSource) {
                HolidayDataSource.entries.indexOfFirst { it.name == dataSource }.coerceAtLeast(0)
            }
            SettingsGroup(title = "数据源") {
                OverlaySpinnerPreference(
                    items = sourceItems,
                    selectedIndex = sourceSelected,
                    title = "数据源",
                    summary = when (selectedSource) {
                        HolidayDataSource.TIMOR -> "timor.tech 免费 API，无需配置"
                        HolidayDataSource.MXNZP -> "MXNZP API，需配置 App ID 和 Secret"
                        HolidayDataSource.CUSTOM -> "自定义 API 地址，支持 {year} 占位符"
                    },
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            settingsRepository.setHolidayDataSource(HolidayDataSource.entries[index].name)
                        }
                    }
                )
                SettingsNote(text = "配置修改后立即生效，无需手动保存。")
            }
        }

        if (selectedSource == HolidayDataSource.MXNZP) {
            item {
                SettingsGroup(title = "MXNZP 配置") {
                    SettingsTextField(
                        value = mxnzpAppIdText,
                        onValueChange = {
                            mxnzpAppIdText = it
                            scope.launch { settingsRepository.setHolidayMxnzpAppId(it) }
                        },
                        label = "App ID"
                    )
                    SettingsTextField(
                        value = mxnzpAppSecretText,
                        onValueChange = {
                            mxnzpAppSecretText = it
                            scope.launch { settingsRepository.setHolidayMxnzpAppSecret(it) }
                        },
                        label = "App Secret"
                    )
                }
            }
        }

        if (selectedSource == HolidayDataSource.CUSTOM) {
            item {
                SettingsGroup(title = "自定义 API 配置") {
                    SettingsNote(text = "支持 {year} 或 \${year} 占位符自动替换年份，响应格式需兼容 Timor 或 MXNZP。")
                    SettingsTextField(
                        value = customUrlText,
                        onValueChange = {
                            customUrlText = it
                            scope.launch { settingsRepository.setHolidayCustomUrl(it) }
                        },
                        label = "API 地址"
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "日期判定") {
                SettingsSwitchItem(
                    title = "忽略节假日",
                    summary = "只按周末判定加班类型，不读取法定节假日规则",
                    checked = ignoreHoliday,
                    onCheckedChange = {
                        scope.launch { settingsRepository.setHolidayIgnoreHoliday(it) }
                    }
                )
            }
        }

        item {
            SettingsGroup(title = "数据更新") {
                SettingsActionItem(
                    title = "更新 $currentYear 年节假日",
                    summary = "从所选数据源重新拉取（当前缓存 ${HolidayManager.getCacheSize()} 条）",
                    icon = MiuixIcons.Refresh,
                    onClick = {
                        if (!isLoading) {
                            isLoading = true
                            scope.launch {
                                HolidayManager.clearCache()
                                val success = HolidayManager.fetchHolidays(currentYear)
                                isLoading = false
                                val msg = if (success) {
                                    "$currentYear 年节假日规则已更新（${HolidayManager.getCacheSize()} 条）"
                                } else {
                                    "更新失败，请检查网络连接"
                                }
                                status = msg
                                snackbarHostState.showCustomToast(msg)
                            }
                        }
                    }
                )
                SettingsActionItem(
                    title = "清除缓存",
                    summary = "清除本地缓存的节假日数据",
                    icon = MiuixIcons.Delete,
                    onClick = {
                        HolidayManager.clearCache()
                        val msg = "节假日缓存已清除"
                        status = msg
                        scope.launch { snackbarHostState.showCustomToast(msg) }
                    }
                )
            }
        }
    }
}
