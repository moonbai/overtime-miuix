package com.overtime.miuix.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
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
    val themeMode by settingsRepository.themeMode.collectAsState(initial = "system")
    val bottomBarStyle by settingsRepository.bottomBarStyle.collectAsState(initial = "ICON_TEXT")
    val quickSubmit by settingsRepository.quickSubmit.collectAsState(initial = false)
    val useFloatingNav by settingsRepository.useFloatingNav.collectAsState(initial = false)
    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())
    val accentColor by settingsRepository.accentColor.collectAsState(initial = 0xFF3482FF.toInt())

    val themeItems = remember { THEME_OPTIONS.map { DropdownItem(text = it.second) } }
    val themeSelected = remember(themeMode) {
        THEME_OPTIONS.indexOfFirst { it.first == themeMode }.coerceAtLeast(0)
    }

    val bottomBarItems = remember {
        BottomBarStyle.entries.map { DropdownItem(text = it.label) }
    }
    val bottomBarSelected = remember(bottomBarStyle) {
        BottomBarStyle.entries.indexOfFirst { it.name == bottomBarStyle }.coerceAtLeast(0)
    }

    SettingsScreen(
        title = "外观设置",
        onBack = { navController.popBackStack() }
    ) {
        item {
            SettingsGroup(title = "主题") {
                OverlaySpinnerPreference(
                    items = themeItems,
                    selectedIndex = themeSelected,
                    title = "主题模式",
                    summary = "跟随系统、浅色、深色及 Monet 取色浅色/深色",
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
            // 底栏样式、悬浮底栏、快速提报都属于「首页操作方式」，合并为一组避免碎片分组
            SettingsGroup(title = "导航与快捷操作") {
                OverlaySpinnerPreference(
                    items = bottomBarItems,
                    selectedIndex = bottomBarSelected,
                    title = "底栏样式",
                    summary = BottomBarStyle.entries.joinToString(" / ") { it.label },
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            settingsRepository.setBottomBarStyle(BottomBarStyle.entries[index].name)
                        }
                    }
                )
                SettingsSwitchItem(
                    title = "悬浮底栏",
                    summary = "使用悬浮式导航栏与毛玻璃背景",
                    checked = useFloatingNav,
                    onCheckedChange = { scope.launch { settingsRepository.setUseFloatingNav(it) } }
                )
                SettingsSwitchItem(
                    title = "快速提报模式",
                    summary = "首页显示一键记录今天的快捷按钮",
                    checked = quickSubmit,
                    onCheckedChange = { scope.launch { settingsRepository.setQuickSubmit(it) } }
                )
            }
        }

        item {
            // 强调色与各类型颜色是同一类配置，合并为一组
            SettingsGroup(title = "颜色") {
                ColorPickerRow(
                    label = "全局强调色",
                    selected = accentColor,
                    onSelect = { scope.launch { settingsRepository.setAccentColor(it) } }
                )
                ColorPickerRow(
                    label = "工作日加班",
                    selected = typeColorWorkday,
                    onSelect = { scope.launch { settingsRepository.setTypeColorWorkday(it) } }
                )
                ColorPickerRow(
                    label = "休息日加班",
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
                // MIUIX Surface(onClick) 统一承载色块、选中描边与标准按压反馈
                Surface(
                    onClick = { onSelect(c) },
                    color = Color(c),
                    shape = CircleShape,
                    border = if (isSelected) BorderStroke(3.dp, MiuixTheme.colorScheme.onSurface) else null,
                    modifier = Modifier.size(32.dp)
                ) {}
            }
        }
    }
}
