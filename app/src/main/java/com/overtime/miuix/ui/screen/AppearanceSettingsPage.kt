package com.overtime.miuix.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.model.BottomBarStyle
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 主题模式选项：value → label，其中 monet_* 映射到 ColorSchemeMode.MonetLight/MonetDark
private val THEME_OPTIONS = listOf(
    "system" to "跟随系统",
    "light" to "浅色模式",
    "dark" to "深色模式",
    "monet_light" to "Monet 浅色",
    "monet_dark" to "Monet 深色"
)

@Composable
fun AppearanceSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val themeMode by settingsRepository.themeMode.collectAsState(initial = "system")
    val bottomBarStyle by settingsRepository.bottomBarStyle.collectAsState(initial = "ICON_TEXT")
    val quickSubmit by settingsRepository.quickSubmit.collectAsState(initial = false)
    val useFloatingNav by settingsRepository.useFloatingNav.collectAsState(initial = false)
    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())

    // 主题 Dropdown
    val themeItems = remember { THEME_OPTIONS.map { SpinnerEntry(title = it.second) } }
    val themeSelected = remember(themeMode) {
        THEME_OPTIONS.indexOfFirst { it.first == themeMode }.coerceAtLeast(0)
    }

    // 底栏样式 Dropdown
    val bottomBarItems = remember {
        BottomBarStyle.entries.map { SpinnerEntry(title = it.label) }
    }
    val bottomBarSelected = remember(bottomBarStyle) {
        BottomBarStyle.entries.indexOfFirst { it.name == bottomBarStyle }.coerceAtLeast(0)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "外观设置",
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
                SettingsGroup(title = "主题") {
                    OverlaySpinnerPreference(
                        items = themeItems,
                        selectedIndex = themeSelected,
                        title = "主题模式",
                        summary = "包含系统跟随、浅色、深色及 Monet 取色浅色/深色",
                        onSelectedIndexChange = { index ->
                            val value = THEME_OPTIONS[index].first
                            scope.launch {
                                settingsRepository.setThemeMode(value)
                                // 同步 monet_enabled 用于备份兼容与主题逻辑
                                settingsRepository.setMonetEnabled(value.startsWith("monet_"))
                            }
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "底栏样式") {
                    OverlaySpinnerPreference(
                        items = bottomBarItems,
                        selectedIndex = bottomBarSelected,
                        title = "底栏样式",
                        summary = "图标 + 文字 / 仅图标 / 仅文字",
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                settingsRepository.setBottomBarStyle(BottomBarStyle.entries[index].name)
                            }
                        }
                    )
                    BasicComponent(
                        title = "悬浮底栏",
                        summary = "使用 FloatingNavigationBar 悬浮样式",
                        endActions = {
                            Switch(
                                checked = useFloatingNav,
                                onCheckedChange = { scope.launch { settingsRepository.setUseFloatingNav(it) } }
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "快捷功能") {
                    BasicComponent(
                        title = "快速提报模式",
                        summary = "首页显示快捷添加按钮",
                        endActions = {
                            Switch(
                                checked = quickSubmit,
                                onCheckedChange = { scope.launch { settingsRepository.setQuickSubmit(it) } }
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "加班类型颜色") {
                    Text(
                        text = "用于统计日历按类型区分颜色",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    ColorPickerRow(
                        label = "工作日加班",
                        selected = typeColorWorkday,
                        onSelect = { scope.launch { settingsRepository.setTypeColorWorkday(it) } }
                    )
                    ColorPickerRow(
                        label = "周末加班",
                        selected = typeColorWeekend,
                        onSelect = { scope.launch { settingsRepository.setTypeColorWeekend(it) } }
                    )
                    ColorPickerRow(
                        label = "节假日加班",
                        selected = typeColorHoliday,
                        onSelect = { scope.launch { settingsRepository.setTypeColorHoliday(it) } }
                    )
                }
            }
        }
    }
}

private val TYPE_COLOR_PALETTE = listOf(
    0xFF3482FF.toInt(),
    0xFF34C759.toInt(),
    0xFFFF7043.toInt(),
    0xFFE91E63.toInt(),
    0xFF9C27B0.toInt(),
    0xFF00BCD4.toInt(),
    0xFFF44336.toInt(),
    0xFFFFC107.toInt(),
    0xFF795548.toInt(),
    0xFF607D8B.toInt()
)

@Composable
private fun ColorPickerRow(
    label: String,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TYPE_COLOR_PALETTE.forEach { c ->
                val isSelected = selected == c
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(c), CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) MiuixTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onSelect(c) }
                )
            }
        }
    }
}
