package com.mars.overtime.ui.screen

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import kotlin.math.roundToInt
import androidx.navigation.NavHostController
import com.mars.overtime.data.model.BottomBarStyle
import com.mars.overtime.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 页面返回转场（含预测性返回手势预览），name 持久化到 DataStore。 */
enum class BackTransition(val label: String) {
    SCALE_FADE("缩放淡入"),
    SLIDE_HORIZONTAL("横向滑动"),
    SLIDE_VERTICAL("纵向滑动"),
    FADE("纯淡入淡出"),
    NONE("无动画")
}

/**
 * 横向返回的退出方向，仅在动画类型为「横向滑动」时显示并生效。
 * FOLLOW_GESTURE 跟随手势来源边缘（默认，与历史行为一致）；
 * ALWAYS_RIGHT / ALWAYS_LEFT 无论从哪侧滑入，当前页都恒定向右 / 恒定向左退出。
 */
enum class BackExitDirection(val label: String) {
    FOLLOW_GESTURE("跟随手势"),
    ALWAYS_RIGHT("恒定向右"),
    ALWAYS_LEFT("恒定向左");

    companion object {
        fun fromValueOrDefault(value: String) = entries.find { it.name == value } ?: FOLLOW_GESTURE
    }
}

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
    val floatingNavBottomOffset by settingsRepository.floatingNavBottomOffset
        .collectAsState(initial = 5)
    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())
    val accentColor by settingsRepository.accentColor.collectAsState(initial = 0xFF3482FF.toInt())
    val backTransition by settingsRepository.predictiveBackTransition
        .collectAsState(initial = BackTransition.SCALE_FADE.name)
    val backExitDirection by settingsRepository.backExitDirection
        .collectAsState(initial = BackExitDirection.FOLLOW_GESTURE.name)
    val predictiveBackEnabled by settingsRepository.predictiveBackEnabled
        .collectAsState(initial = true)

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

    val backTransitionItems = remember {
        BackTransition.entries.map { DropdownItem(text = it.label) }
    }
    val backTransitionSelected = remember(backTransition) {
        BackTransition.entries.indexOfFirst { it.name == backTransition }.coerceAtLeast(0)
    }

    val backExitDirectionItems = remember {
        BackExitDirection.entries.map { DropdownItem(text = it.label) }
    }
    val backExitDirectionSelected = remember(backExitDirection) {
        BackExitDirection.entries.indexOfFirst { it.name == backExitDirection }.coerceAtLeast(0)
    }

    SettingsScreen(
        title = "外观设置",
        onBack = { navController.popBackStack() }
    ) {
        item {
            SettingsGroup {
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
            SettingsGroup {
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
                if (useFloatingNav) {
                    // 拖动时只更新本地状态，松手才落盘，避免每个像素都写一次 DataStore
                    var sliderValue by remember(floatingNavBottomOffset) {
                        mutableFloatStateOf(floatingNavBottomOffset.toFloat())
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "悬浮底栏离底距离",
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${sliderValue.roundToInt()} dp",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                scope.launch {
                                    settingsRepository.setFloatingNavBottomOffset(
                                        sliderValue.roundToInt()
                                    )
                                }
                            },
                            valueRange = 0f..64f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                        Text(
                            text = "悬浮底栏与屏幕底部的距离，系统手势条安全区会自动叠加",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                SettingsSwitchItem(
                    title = "快速提报模式",
                    summary = "首页显示一键记录今天的快捷按钮",
                    checked = quickSubmit,
                    onCheckedChange = { scope.launch { settingsRepository.setQuickSubmit(it) } }
                )
            }
        }

        item {
            SettingsGroup {
                SettingsSwitchItem(
                    title = "返回预测",
                    summary = "开启后，系统返回手势会实时预览目标页面并显示返回动画",
                    checked = predictiveBackEnabled,
                    onCheckedChange = { scope.launch { settingsRepository.setPredictiveBackEnabled(it) } }
                )
                if (predictiveBackEnabled) {
                    OverlaySpinnerPreference(
                        items = backTransitionItems,
                        selectedIndex = backTransitionSelected,
                        title = "返回动画",
                        summary = "返回、手势返回预览与页面退出时使用的动画",
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                settingsRepository.setPredictiveBackTransition(
                                    BackTransition.entries[index].name
                                )
                            }
                        }
                    )
                    // 退出方向只对横向滑动有意义，其余动画类型隐藏避免误导
                    AnimatedVisibility(
                        visible = backTransition == BackTransition.SLIDE_HORIZONTAL.name
                    ) {
                        OverlaySpinnerPreference(
                            items = backExitDirectionItems,
                            selectedIndex = backExitDirectionSelected,
                            title = "退出方向",
                            summary = "横向滑动时当前页面的退出方向",
                            onSelectedIndexChange = { index ->
                                scope.launch {
                                    settingsRepository.setBackExitDirection(
                                        BackExitDirection.entries[index].name
                                    )
                                }
                            }
                        )
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        SettingsNote(
                            text = "Android 14 及以上支持手势实时预览；当前系统将在松手后播放动画"
                        )
                    }
                } else {
                    SettingsNote(text = "返回预测已关闭，返回时不做任何动画（瞬时返回）")
                }
            }
        }

        item {
            // 强调色与各类型颜色是同一类配置，合并为一组
            SettingsGroup {
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
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(Color(selected)) }

    // 单行入口：标题 + 当前色值 + 色点，点击弹出 MIUIX 原生取色器
    Surface(
        onClick = {
            draft = Color(selected)
            showDialog = true
        },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "#${"%06X".format(selected and 0xFFFFFF)}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(selected))
            )
        }
    }

    // 取色器打开时拦截系统返回手势，关闭弹窗而非退出应用
    BackHandler(enabled = showDialog) { showDialog = false }

    OverlayDialog(
        show = showDialog,
        title = label,
        summary = "拖动滑块自由取色，或点击预设色快速选择",
        onDismissRequest = { showDialog = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ColorPicker(
                color = draft,
                onColorChanged = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                colorSpace = ColorSpace.HSV
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TYPE_COLOR_PALETTE.forEach { c ->
                    Surface(
                        onClick = { draft = Color(c) },
                        color = Color(c),
                        shape = CircleShape,
                        border = if (draft.toArgb() == c) {
                            BorderStroke(2.dp, MiuixTheme.colorScheme.onSurface)
                        } else null,
                        modifier = Modifier.size(28.dp)
                    ) {}
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "取消",
                    onClick = { showDialog = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = "确定",
                    onClick = {
                        // 统一存为不透明 ARGB，避免取色器 alpha 影响主题配色
                        onSelect(draft.toArgb() or 0xFF000000.toInt())
                        showDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
