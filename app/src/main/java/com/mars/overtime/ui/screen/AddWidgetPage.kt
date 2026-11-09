package com.mars.overtime.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.data.repository.DEFAULT_HOME_LAYOUT
import com.mars.overtime.data.repository.WidgetPlacement
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 添加小组件页：以当前真实数据**实时预览**每种卡片样式，可选尺寸
 * （1×1 / 2×1 / 1×2 / 2×2）后添加；已添加的组件可在此移除。
 */
@Composable
fun AddWidgetPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val records by repository.getAllRecords().collectAsState(initial = emptyList())
    val layout by settingsRepository.homeLayout.collectAsState(initial = DEFAULT_HOME_LAYOUT)
    val currentMonth = remember { SalaryCalculator.getCurrentYearMonth() }
    val agg = remember(records, currentMonth) { aggregateHome(records, currentMonth) }

    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())
    val typeColor: (OvertimeType) -> Color = { type ->
        when (type) {
            OvertimeType.WORKDAY -> Color(typeColorWorkday)
            OvertimeType.WEEKEND -> Color(typeColorWeekend)
            OvertimeType.HOLIDAY -> Color(typeColorHoliday)
        }
    }

    fun persist(items: List<WidgetPlacement>) {
        scope.launch { settingsRepository.setHomeLayout(items) }
    }

    SettingsScreen(
        title = "添加组件",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    ) {
        item {
            SettingsNote("预览为当前数据实时渲染；选好尺寸后点「添加」，已添加的组件可在此移除。排序与缩放在首页长按组件直接完成。")
        }
        items(items = WIDGET_CATALOG, key = { it.id }) { def ->
            val present = layout.any { it.id == def.id }
            val presentSpan = layout.firstOrNull { it.id == def.id }
            val chosen = remember(def.id) {
                mutableStateOf(
                    (presentSpan?.spanX ?: def.defaultSpanX) to (presentSpan?.spanY ?: def.defaultSpanY)
                )
            }
            SettingsGroup(title = def.title) {
                BasicComponent(
                    title = def.desc,
                    summary = if (def.isChart) "图表组件" else "数值组件",
                    onClick = {}
                )
                // 实时预览：按所选尺寸缩放
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimens.spaceXs),
                    contentAlignment = Alignment.Center
                ) {
                    val cellW = (maxWidth - AppDimens.groupSpacing) / GRID_COLUMNS
                    val w = cellW * chosen.value.first + AppDimens.groupSpacing * (chosen.value.first - 1)
                    val h = CELL_HEIGHT * chosen.value.second + AppDimens.groupSpacing * (chosen.value.second - 1)
                    Box(
                        Modifier
                            .width(w)
                            .height(h)
                    ) {
                        PreviewWidgetCard(
                            id = def.id,
                            agg = agg,
                            month = currentMonth,
                            typeColor = typeColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // 尺寸选择（1×1 / 2×1 / 1×2 / 2×2）
                val sizes = listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.itemHorizontal, vertical = AppDimens.spaceXs),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs)
                ) {
                    for ((sx, sy) in sizes) {
                        SizeChip(
                            label = "${sx}×${sy}",
                            selected = chosen.value == (sx to sy),
                            onClick = { chosen.value = sx to sy }
                        )
                    }
                }
                // 添加 / 移除
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.itemHorizontal, vertical = AppDimens.spaceXs)
                ) {
                    Button(
                        onClick = {
                            if (present) {
                                persist(layout.filter { it.id != def.id })
                                scope.launch { snackbarHostState.showCustomToast("已移除「${def.title}」") }
                            } else {
                                persist(layout + WidgetPlacement(def.id, chosen.value.first, chosen.value.second))
                                scope.launch { snackbarHostState.showCustomToast("已添加「${def.title}」") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (present) MiuixIcons.Delete else MiuixIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(AppIconSize.xs)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (present) "移除" else "添加")
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SizeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline
    Box(
        Modifier
            .weight(1f)
            .clip(AppShapes.radiusMd)
            .border(1.dp, borderColor, AppShapes.radiusMd)
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
        )
    }
}
