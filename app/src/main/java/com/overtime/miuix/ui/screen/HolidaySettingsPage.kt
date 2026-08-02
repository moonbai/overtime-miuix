package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Year

@Composable
fun HolidaySettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
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

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "节假日管理",
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
            if (status != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = status ?: "",
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.footnote1
                        )
                    }
                }
            }

            item {
                val sourceItems = remember {
                    HolidayDataSource.entries.map { DropdownItem(text = it.label) }
                }
                val sourceSelected = remember(dataSource) {
                    HolidayDataSource.entries.indexOfFirst { it.name == dataSource }.coerceAtLeast(0)
                }
                SettingsGroup(title = "数据源选择") {
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
                }
            }

            if (selectedSource == HolidayDataSource.MXNZP) {
                item {
                    SettingsGroup(title = "MXNZP 配置") {
                        TextField(
                            value = mxnzpAppIdText,
                            onValueChange = { mxnzpAppIdText = it; scope.launch { settingsRepository.setHolidayMxnzpAppId(it) } },
                            label = "App ID",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        TextField(
                            value = mxnzpAppSecretText,
                            onValueChange = { mxnzpAppSecretText = it; scope.launch { settingsRepository.setHolidayMxnzpAppSecret(it) } },
                            label = "App Secret",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        BasicComponent(
                            title = "忽略节假日",
                            summary = "仅标记周末，不标记法定节假日",
                            endActions = {
                                Switch(
                                    checked = ignoreHoliday,
                                    onCheckedChange = {
                                        scope.launch { settingsRepository.setHolidayIgnoreHoliday(it) }
                                    }
                                )
                            }
                        )
                    }
                }
            }

            if (selectedSource == HolidayDataSource.CUSTOM) {
                item {
                    SettingsGroup(title = "自定义 API 配置") {
                        Text(
                            text = "支持 {year} 或 \${year} 占位符自动替换年份。\n响应格式需兼容 Timor 或 MXNZP 格式。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = customUrlText,
                            onValueChange = { customUrlText = it; scope.launch { settingsRepository.setHolidayCustomUrl(it) } },
                            label = "API 地址",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    settingsRepository.setHolidayDataSource(selectedSource.name)
                                    settingsRepository.setHolidayCustomUrl(customUrlText)
                                    settingsRepository.setHolidayMxnzpAppId(mxnzpAppIdText)
                                    settingsRepository.setHolidayMxnzpAppSecret(mxnzpAppSecretText)
                                    status = "配置已保存"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("保存配置") }
                    }
                }
            }

            item {
                SettingsGroup(title = "数据更新") {
                    BasicComponent(
                        title = "手动更新 $currentYear 年节假日",
                        summary = "从所选数据源拉取最新节假日规则（当前缓存 ${HolidayManager.getCacheSize()} 条）",
                        startAction = { Icon(MiuixIcons.Refresh, contentDescription = null) },
                        onClick = {
                            if (isLoading) return@BasicComponent
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
                                scope.launch { snackbarHostState.showCustomToast(msg) }
                            }
                        }
                    )
                    BasicComponent(
                        title = "清除缓存",
                        summary = "清除本地缓存的节假日数据",
                        startAction = { Icon(MiuixIcons.Delete, contentDescription = null) },
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
}
