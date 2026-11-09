package com.mars.overtime.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.data.repository.SettingsRepository.Companion.DEFAULT_HOME_WIDGETS
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes
import com.mars.overtime.ui.theme.AppTitle3Semibold
import com.mars.overtime.ui.theme.AppHeadline2Bold
import com.mars.overtime.util.SalaryCalculator
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 首页：信息面板。
 * 结构 = 月度概览卡（总览 / 分类两种模式）+ 可自定义小组件网格（2 列），
 * 完整的记录浏览 / 搜索 / 筛选 / 删除已拆分到 [RecordsPage]（底栏「记录」）。
 *
 * 小组件的启用与排序在「设置 → 首页小组件」配置，
 * 概览卡右上角的「编辑」入口可直达该配置页。
 */
@Composable
fun HomePage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository,
    onOpenRecords: () -> Unit = {},
    topReserve: androidx.compose.ui.unit.Dp = 0.dp,
    bottomReserve: androidx.compose.ui.unit.Dp = 0.dp,
    listState: LazyListState = rememberLazyListState()
) {
    val records by repository.getAllRecords().collectAsState(initial = emptyList())
    val currentMonth = remember { SalaryCalculator.getCurrentYearMonth() }

    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = 0xFF3482FF.toInt())
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = 0xFF34C759.toInt())
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = 0xFFFF7043.toInt())
    val overviewMode by settingsRepository.homeOverviewMode.collectAsState(initial = "total")
    val widgetIds by settingsRepository.homeWidgets.collectAsState(initial = DEFAULT_HOME_WIDGETS)

    // 用户配置的类型色（与统计页同源），分类概览与组件色条共用
    val typeColor: (OvertimeType) -> Color = { type ->
        when (type) {
            OvertimeType.WORKDAY -> Color(typeColorWorkday)
            OvertimeType.WEEKEND -> Color(typeColorWeekend)
            OvertimeType.HOLIDAY -> Color(typeColorHoliday)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = AppDimens.pageHorizontal,
            top = AppDimens.pageVertical + topReserve,
            end = AppDimens.pageHorizontal,
            bottom = AppDimens.pageVertical + bottomReserve
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.groupSpacing)
    ) {
        item {
            MonthlyOverviewCard(
                repository = repository,
                month = currentMonth,
                records = records,
                mode = overviewMode,
                typeColor = typeColor,
                onEdit = { navController.navigate("home_widgets") }
            )
        }

        if (records.isEmpty()) {
            item {
                HomeEmptyState(
                    onAdd = {
                        // 连点时避免把多个 add_record 实例压入回退栈
                        navController.navigate("add_record") { launchSingleTop = true }
                    }
                )
            }
        } else {
            // 启用的小组件按配置顺序 2 列铺排；不足一行的保持左对齐
            val rows = widgetIds.chunked(2)
            rows.forEach { rowIds ->
                item(key = rowIds.joinToString("_")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.groupSpacing)
                    ) {
                        rowIds.forEach { id ->
                            HomeWidgetCard(
                                widgetId = id,
                                records = records,
                                month = currentMonth,
                                typeColor = typeColor,
                                modifier = Modifier.weight(1f),
                                onOpenStats = { navController.navigate("statistics") },
                                onOpenRecords = onOpenRecords
                            )
                        }
                        // 单数行补位，保持卡片等宽
                        if (rowIds.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyOverviewCard(
    repository: OvertimeRepository,
    month: String,
    records: List<OvertimeRecord>,
    mode: String,
    typeColor: (OvertimeType) -> Color,
    onEdit: () -> Unit
) {
    var stats by remember { mutableStateOf<com.mars.overtime.data.repository.MonthlyStats?>(null) }

    // 以 month 与 records 为 key：当前月内新增/删除记录时自动重算概览
    LaunchedEffect(month, records) {
        stats = repository.getMonthlyStats(month)
    }

    // 分类模式：本月三类加班工时（请假为负数的休息日，不单独分色，仅体现在净时长）
    val typeHours = remember(records, month) {
        val cal = Calendar.getInstance()
        var workday = 0.0; var weekend = 0.0; var holiday = 0.0; var leave = 0.0
        records.forEach { rec ->
            cal.timeInMillis = rec.date
            val m = "${cal.get(Calendar.YEAR)}-${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}"
            if (m == month) {
                if (rec.isLeave) leave += rec.durationHours
                else when (rec.type) {
                    OvertimeType.WORKDAY -> workday += rec.durationHours
                    OvertimeType.WEEKEND -> weekend += rec.durationHours
                    OvertimeType.HOLIDAY -> holiday += rec.durationHours
                }
            }
        }
        Triple(workday, weekend, holiday) to leave
    }
    val maxType = maxOf(typeHours.first.first, typeHours.first.second, typeHours.first.third, 0.001)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        cornerRadius = AppDimens.cardCorner
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$month 概览",
                    style = AppTitle3Semibold(),
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                // 小组件配置快捷入口：直达「设置 → 首页小组件」
                Surface(
                    onClick = onEdit,
                    color = MiuixTheme.colorScheme.secondaryContainer,
                    contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                    shape = AppShapes.radiusLg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            MiuixIcons.Settings,
                            contentDescription = "编辑小组件",
                            modifier = Modifier.size(AppIconSize.xs)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "编辑",
                            style = MiuixTheme.textStyles.footnote1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (mode == "by_type") {
                // 分类模式：净时长为主数字，三类加班以类型色条并列展示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        "净时长",
                        stats?.let { SalaryCalculator.formatHours(it.totalHours) } ?: "0小时"
                    )
                    StatItem("预估薪资", stats?.let { SalaryCalculator.formatAmount(it.totalAmount) } ?: "¥0.00")
                    StatItem("记录数", "${stats?.recordCount ?: 0}条")
                }
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    OvertimeType.WORKDAY to typeHours.first.first,
                    OvertimeType.WEEKEND to typeHours.first.second,
                    OvertimeType.HOLIDAY to typeHours.first.third
                ).forEach { (type, hours) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = type.label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = typeColor(type),
                            modifier = Modifier.width(48.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .background(MiuixTheme.colorScheme.secondaryContainer, AppShapes.radiusSm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((hours / maxType).toFloat().coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .background(typeColor(type), AppShapes.radiusSm)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = SalaryCalculator.formatHours(hours),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.width(56.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("总时长", stats?.let { SalaryCalculator.formatHours(it.totalHours) } ?: "0小时")
                    StatItem("预估薪资", stats?.let { SalaryCalculator.formatAmount(it.totalAmount) } ?: "¥0.00")
                    StatItem("记录数", "${stats?.recordCount ?: 0}条")
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 概览数字明显大于标签：headline2 大号加粗，与 footnote1 标签形成清晰主次
        Text(
            text = value,
            style = AppHeadline2Bold(),
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/** 小组件元信息：id → 标题，与 SettingsRepository.HomeWidgetIds 对应 */
private fun widgetTitle(id: String): String = when (id) {
    "month_hours" -> "当月工时"
    "last_month_hours" -> "上月工时"
    "month_salary" -> "本月薪资"
    "year_total" -> "年度累计"
    "month_leave" -> "当月请假"
    "latest_record" -> "最近记录"
    else -> id
}

/**
 * 单个小组件卡片。数据全部由 records 内存聚合（口径与统计页一致：请假为负抵扣），
 * 点击跳转：统计类 → 统计页；记录类 → 记录页。
 */
@Composable
private fun HomeWidgetCard(
    widgetId: String,
    records: List<OvertimeRecord>,
    month: String,
    typeColor: (OvertimeType) -> Color,
    modifier: Modifier = Modifier,
    onOpenStats: () -> Unit,
    onOpenRecords: () -> Unit
) {
    // 统一聚合：本月/上月/本年各类数据一次算好，各组件按需取用
    data class Agg(
        val monthNet: Double, val monthCount: Int,
        val lastMonthNet: Double, val lastMonthCount: Int,
        val monthAmount: Double,
        val yearNet: Double, val yearAmount: Double,
        val monthLeaveCount: Int, val monthLeaveDeduct: Double,
        val latest: OvertimeRecord?
    )
    val agg = remember(records, month) {
        val cal = Calendar.getInstance()
        val year = month.substringBefore("-").toIntOrNull() ?: cal.get(Calendar.YEAR)
        val mon = month.substringAfter("-").toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)
        val fmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        var mNet = 0.0; var mCount = 0; var lNet = 0.0; var lCount = 0
        var mAmount = 0.0; var mLeaveCount = 0; var mLeaveDeduct = 0.0
        var yNet = 0.0; var yAmount = 0.0
        records.forEach { rec ->
            cal.timeInMillis = rec.date
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            if (y == year && m == mon) {
                mNet += rec.durationHours
                mAmount += rec.amount
                mCount++
                if (rec.isLeave) {
                    mLeaveCount++
                    mLeaveDeduct += kotlin.math.abs(rec.amount)
                }
            } else if (y == year && m == mon - 1 || (mon == 1 && y == year - 1 && m == 12)) {
                lNet += rec.durationHours
                lCount++
            }
            if (y == year) {
                yNet += rec.durationHours
                yAmount += rec.amount
            }
        }
        Agg(mNet, mCount, lNet, lCount, mAmount, yNet, yAmount, mLeaveCount, mLeaveDeduct, records.maxByOrNull { it.date })
    }

    // 值 / 副文案 / 点击目标按组件类型分别取数
    val (value, sub, onClick) = when (widgetId) {
        "month_hours" -> Triple(
            SalaryCalculator.formatHours(agg.monthNet),
            when {
                agg.lastMonthNet <= 0.0 && agg.monthNet <= 0.0 -> "上月无记录"
                agg.monthNet >= agg.lastMonthNet -> "较上月 +${SalaryCalculator.formatHours(agg.monthNet - agg.lastMonthNet)}"
                else -> "较上月 -${SalaryCalculator.formatHours(agg.lastMonthNet - agg.monthNet)}"
            },
            onOpenStats
        )
        "last_month_hours" -> Triple(
            SalaryCalculator.formatHours(agg.lastMonthNet),
            "${agg.lastMonthCount}条记录",
            onOpenStats
        )
        "month_salary" -> Triple(
            SalaryCalculator.formatAmount(agg.monthAmount),
            "含请假扣款",
            onOpenStats
        )
        "year_total" -> Triple(
            SalaryCalculator.formatHours(agg.yearNet),
            SalaryCalculator.formatAmount(agg.yearAmount),
            onOpenStats
        )
        "month_leave" -> Triple(
            "${agg.monthLeaveCount}次",
            if (agg.monthLeaveCount > 0) "扣款 ${SalaryCalculator.formatAmount(agg.monthLeaveDeduct)}" else "本月无请假",
            onOpenRecords
        )
        "latest_record" -> {
            val latest = agg.latest
            val cal = Calendar.getInstance().apply { timeInMillis = latest?.date ?: 0L }
            Triple(
                if (latest != null) {
                    if (latest.isLeave) "请假" else latest.type.label
                } else "暂无记录",
                if (latest != null) {
                    "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 · " +
                        SalaryCalculator.formatHours(kotlin.math.abs(latest.durationHours))
                } else "去记录页添加",
                onOpenRecords
            )
        }
        else -> Triple("--", "", onOpenStats)
    }

    Card(
        modifier = modifier,
        cornerRadius = AppDimens.cardCorner,
        insideMargin = PaddingValues(14.dp),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Column {
            Text(
                text = widgetTitle(widgetId),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = AppHeadline2Bold(),
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sub,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 首页空状态：轻量插画 + 引导文案 + 主操作按钮。
 * 用「主题色图标嵌套淡色圆 + 两个点缀圆」构成一张克制的小插画，
 * 弱化了「无数据」的空旷感，并把首条记录的转化入口放在最显眼处。
 */
@Composable
private fun HomeEmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(156.dp),
            contentAlignment = Alignment.Center
        ) {
            // 主圆（淡色容器）
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(AppShapes.pill)
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
            )
            // 右上点缀（主题色浅块）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(42.dp)
                    .offset(x = 10.dp, y = (-8).dp)
                    .clip(AppShapes.pill)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            )
            // 左下点缀（次容器块）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(30.dp)
                    .offset(x = (-12).dp, y = 10.dp)
                    .clip(AppShapes.pill)
                    .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f))
            )
            // 主题色插画主体图标（闹钟：呼应加班/打卡）
            Icon(
                MiuixIcons.Alarm,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }
        Text(
            text = "还没有加班记录",
            style = AppTitle3Semibold(),
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = "记录第一笔加班，从这里开始",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = AppDimens.controlCorner,
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Icon(
                MiuixIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(AppIconSize.xs)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("添加记录", fontWeight = FontWeight.Medium)
        }
    }
}
