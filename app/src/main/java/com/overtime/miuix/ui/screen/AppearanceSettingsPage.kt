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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppearanceSettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val themeMode by settingsRepository.themeMode.collectAsState(initial = "system")
    val bottomBarStyle by settingsRepository.bottomBarStyle.collectAsState(initial = "ICON_TEXT")
    val quickSubmit by settingsRepository.quickSubmit.collectAsState(initial = false)
    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())
    
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Column {
                    SmallTitle(text = "主题")
                    BasicComponent(
                        title = "跟随系统",
                        onClick = { scope.launch { settingsRepository.setThemeMode("system") } },
                        endActions = { RadioButton(selected = themeMode == "system", onClick = null) }
                    )
                    BasicComponent(
                        title = "浅色模式",
                        onClick = { scope.launch { settingsRepository.setThemeMode("light") } },
                        endActions = { RadioButton(selected = themeMode == "light", onClick = null) }
                    )
                    BasicComponent(
                        title = "深色模式",
                        onClick = { scope.launch { settingsRepository.setThemeMode("dark") } },
                        endActions = { RadioButton(selected = themeMode == "dark", onClick = null) }
                    )
                }
            }
            
            item {
                Column {
                    SmallTitle(text = "底栏样式")
                    BottomBarStyle.entries.forEach { style ->
                        BasicComponent(
                            title = style.label,
                            onClick = { scope.launch { settingsRepository.setBottomBarStyle(style.name) } },
                            endActions = { RadioButton(selected = bottomBarStyle == style.name, onClick = null) }
                        )
                    }
                }
            }
            
            item {
                Column {
                    SmallTitle(text = "快捷功能")
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
                Column {
                    SmallTitle(text = "加班类型颜色")
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
