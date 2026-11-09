package com.mars.overtime.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.ui.component.rememberAppRecords
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.util.AppDateFormats
import com.mars.overtime.util.AppDefaults
import com.mars.overtime.ui.component.AppEmptyState
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.dismissDeleteSnackbars
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes
import com.mars.overtime.ui.theme.AppTitle3Semibold
import com.mars.overtime.ui.theme.AppBody1Medium
import com.mars.overtime.ui.theme.AppBody1Semibold
import com.mars.overtime.ui.theme.AppFootnote1Bold
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 记录筛选：全部 / 请假 / 按加班类型 */
sealed class RecordFilter {
    object All : RecordFilter()
    object Leave : RecordFilter()
    data class Type(val t: OvertimeType) : RecordFilter()
}

/** 搜索词中的日期条件；null 表示该维度不限（「8月」不限年份，匹配所有 8 月记录）。 */
private data class RecordDateQuery(val year: Int?, val month: Int?, val day: Int?)

private val CN_DIGIT_MAP = mapOf(
    '〇' to 0, '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
    '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
)
private val CN_NUMBER_REGEX = Regex("[〇零一两二三四五六七八九十]+")
private val DATE_UNIT_REGEX = Regex("[年月日号份/-]")
private val MULTI_DASH_REGEX = Regex("-+")
private val DATE_SHAPE_REGEX = Regex("^\\d+(?:-\\d+)*$")

/** 中文数字串转阿拉伯数字：八→8、十一→11、二十三→23、二〇二六→2026。 */
private fun chineseNumberToArabic(text: String): String? {
    if ('十' !in text) {
        val digits = StringBuilder()
        for (ch in text) digits.append(CN_DIGIT_MAP[ch] ?: return null)
        return digits.toString()
    }
    val parts = text.split('十', limit = 2)
    val tensText = parts[0]
    val onesText = parts.getOrNull(1).orEmpty()
    val tens = when {
        tensText.isEmpty() -> 1
        tensText.length == 1 -> CN_DIGIT_MAP[tensText[0]] ?: return null
        else -> return null
    }
    val ones = when {
        onesText.isEmpty() -> 0
        onesText.length == 1 -> CN_DIGIT_MAP[onesText[0]] ?: return null
        else -> return null
    }
    return (tens * 10 + ones).toString()
}

/**
 * 把日期类搜索词解析成结构化条件。支持：
 * 8月 / 八月 / 8月份 / 8月5日 / 20日 / 2026年8月 / 2026-08 /
 * 2026-08-15 / 2026/8/5 / 8-15（不带年份时匹配所有年份的同一天/同月）。
 *
 * 纯数字（如「8」「2026」）不解析，继续走备注 / 月份文本 / 时长的包含匹配，
 * 避免把普通数字或小数时长误判为日期；解析失败同样回退到包含匹配。
 */
private fun parseRecordDateQuery(raw: String): RecordDateQuery? {
    // 无单位、无分隔符的输入不当作日期条件
    if (!raw.any { it in "年月日号份/-／－" }) return null
    val dayOnly = raw.trim().let { it.endsWith("日") || it.endsWith("号") }

    val normalized = buildString {
        for (ch in raw) when {
            ch in '０'..'９' -> append((ch.code - '０'.code + '0'.code).toChar())
            ch == '／' -> append('/')
            ch == '－' -> append('-')
            ch.isWhitespace() -> Unit
            else -> append(ch)
        }
    }.let { CN_NUMBER_REGEX.replace(it) { m -> chineseNumberToArabic(m.value) ?: m.value } }
        .let { DATE_UNIT_REGEX.replace(it, "-") }
        .let { MULTI_DASH_REGEX.replace(it, "-") }
        .trim('-')

    if (!DATE_SHAPE_REGEX.matches(normalized)) return null
    val parts = normalized.split("-").map { it.toIntOrNull() ?: return null }
    return when (parts.size) {
        1 -> when {
            parts[0] in 1900..2999 -> RecordDateQuery(parts[0], null, null)
            dayOnly && parts[0] in 1..31 -> RecordDateQuery(null, null, parts[0])
            parts[0] in 1..12 -> RecordDateQuery(null, parts[0], null)
            else -> null
        }
        2 -> when {
            parts[0] in 1900..2999 && parts[1] in 1..12 ->
                RecordDateQuery(parts[0], parts[1], null)
            parts[0] in 1..12 && parts[1] in 1..31 ->
                RecordDateQuery(null, parts[0], parts[1])
            else -> null
        }
        3 -> if (parts[0] in 1900..2999 && parts[1] in 1..12 && parts[2] in 1..31) {
            RecordDateQuery(parts[0], parts[1], parts[2])
        } else null
        else -> null
    }
}

/**
 * 每条记录的搜索索引：在记录变化时预计算一次，输入时只做字符串 / 整数比较，
 * 不再每次按键都对全表做日期格式化、时长格式化。
 */
private data class RecordSearchIndex(
    val monthKey: String,
    val hoursText: String,
    val hoursNumber: String,
    val year: Int,
    val month: Int,
    val day: Int
)

private fun matchesRecordDate(index: RecordSearchIndex, q: RecordDateQuery): Boolean =
    (q.year == null || index.year == q.year) &&
            (q.month == null || index.month == q.month) &&
            (q.day == null || index.day == q.day)

/** 时长的纯数字写法（8.5 → "8.5"），让数字输入也能命中「8.5H」。 */
private fun durationNumberText(hours: Double): String {
    val v = kotlin.math.abs(hours)
    return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

/**
 * 记录页：全部加班/请假记录的搜索、筛选、按月分组浏览与删除。
 * 由首页拆分而来——首页专注信息概览，完整的记录管理收敛到本页。
 * 删除/撤销逻辑与原首页完全一致，仅整体迁移不改语义。
 */
@Composable
fun RecordsPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository,
    topReserve: androidx.compose.ui.unit.Dp = 0.dp,
    bottomReserve: androidx.compose.ui.unit.Dp = 0.dp,
    listState: LazyListState = rememberLazyListState()
) {
    // 「是否已加载」与「记录内容」取自同一份快照：initial=null 表示尚未加载。
    // 旧写法用 initial=emptyList() + 独立 LaunchedEffect 打标记，两者更新不同步时
    // 会出现 loaded=true 但 records 仍为空的一帧，切 tab 时恰好闪一下「还没有记录」。
    val recordsSnapshot by rememberAppRecords(repository)
    val recordsLoaded = recordsSnapshot != null
    val records = recordsSnapshot.orEmpty()

    // 用户配置的加班类型配色，用于记录标题与统计保持一致
    val typeColorWorkday by settingsRepository.typeColorWorkday.collectAsState(initial = AppDefaults.PRIMARY_ARGB)
    val typeColorWeekend by settingsRepository.typeColorWeekend.collectAsState(initial = AppDefaults.WEEKEND_ARGB)
    val typeColorHoliday by settingsRepository.typeColorHoliday.collectAsState(initial = AppDefaults.HOLIDAY_ARGB)
    // 「删除二次确认」设置项：关闭时直接删除，仅靠 Snackbar 撤销
    val deleteConfirmEnabled by settingsRepository.deleteConfirmEnabled.collectAsState(initial = true)

    // 搜索与筛选：输入框实时回显，过滤条件去抖后再触发一次全表扫描
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    // 每次输入重启协程并取消上一次：等价去抖，且不依赖 FlowPreview 的 debounce
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) delay(150L)
        debouncedQuery = searchQuery
    }
    var recordFilter by remember { mutableStateOf<RecordFilter>(RecordFilter.All) }

    // 删除确认对话框
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<OvertimeRecord?>(null) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    /**
     * 连续删除的撤销批次：关闭二次确认后用户可能快速连点删除，
     * 这些记录统一交给「最新一条」撤销提示承载，撤销时一次性全部恢复。
     */
    val pendingDeletes = remember { mutableListOf<OvertimeRecord>() }
    // 串行化批次更新，避免多个删除协程交错导致提示残留或批次错乱
    val deleteMutex = remember { Mutex() }
    var undoJob by remember { mutableStateOf<Job?>(null) }
    var undoGeneration by remember { mutableStateOf(0) }

    /**
     * 删除并给出 4 秒撤销窗口。确认删除与「关闭二次确认」后的直接删除共用此逻辑。
     *
     * MIUIX 0.9.4 的 SnackbarHost 会渲染队列中的全部条目，showSnackbar 每次都是
     * 「新增一条」而非「替换当前条」，因此连续删除会逐条叠加通知条。这里改为：
     * 先取消并收起上一条，只显示最新的一条；连续删除的记录合并进同一批次，
     * 既不堆叠，也不会因为旧提示被顶掉而丢失撤销能力。
     */
    fun performDelete(record: OvertimeRecord) {
        scope.launch {
            deleteMutex.withLock {
                repository.delete(record)
                pendingDeletes.add(record)

                // 代次先递增：旧协程被 dismiss 唤醒后据此直接退出，不会误清新批次
                val generation = ++undoGeneration
                undoJob?.cancel()
                snackbarHostState.dismissDeleteSnackbars()

                val count = pendingDeletes.size
                undoJob = scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = if (count > 1) "已删除 $count 条记录" else "已删除记录",
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Custom(4000)
                    )
                    // 已被后续删除取代：批次交由最新的提示处理，本协程不触碰
                    if (undoGeneration != generation) return@launch
                    if (result == SnackbarResult.ActionPerformed) {
                        // 结果确定时立刻取走并清空批次，避免后续删除混入本次撤销
                        val batch = deleteMutex.withLock {
                            val snapshot = pendingDeletes.toList()
                            pendingDeletes.clear()
                            snapshot
                        }
                        batch.forEach { repository.insert(it.copy(id = 0)) }
                        snackbarHostState.showCustomToast(
                            if (batch.size > 1) "已撤销 ${batch.size} 条删除" else "已撤销删除"
                        )
                    } else {
                        deleteMutex.withLock { pendingDeletes.clear() }
                    }
                }
            }
        }
    }

    // 搜索索引按 records 变化重算一次；过滤 + 按月分组（倒序）也合成一次遍历。
    // 旧写法每次按键都对全表做日期 / 时长格式化，再 filter + groupBy + 排序。
    val searchIndex = remember(records) {
        records.map { rec ->
            val (year, month, day) = AppDateFormats.dateParts(rec.date)
            RecordSearchIndex(
                monthKey = AppDateFormats.monthKey(rec.date),
                hoursText = SalaryCalculator.formatHours(rec.durationHours),
                hoursNumber = durationNumberText(rec.durationHours),
                year = year,
                month = month,
                day = day
            )
        }
    }
    val grouped: Map<String, List<OvertimeRecord>> = remember(
        records, searchIndex, debouncedQuery, recordFilter
    ) {
        val q = debouncedQuery.trim()
        val dateQuery = parseRecordDateQuery(q)
        val currentFilter = recordFilter
        val buckets = java.util.TreeMap<String, MutableList<OvertimeRecord>>(compareByDescending { it })
        for (i in records.indices) {
            val rec = records[i]
            val matchFilter = when (currentFilter) {
                RecordFilter.All -> true
                RecordFilter.Leave -> rec.isLeave
                is RecordFilter.Type -> !rec.isLeave && rec.type == currentFilter.t
            }
            if (!matchFilter) continue
            val index = searchIndex[i]
            val matchQuery = q.isEmpty() || rec.note.contains(q, ignoreCase = true)
                || index.monthKey.contains(q)
                || index.hoursText.contains(q)
                || index.hoursNumber.contains(q)
                || (dateQuery != null && matchesRecordDate(index, dateQuery))
            if (matchQuery) buckets.getOrPut(index.monthKey) { mutableListOf() }.add(rec)
        }
        buckets
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(
            start = AppDimens.pageHorizontal,
            top = AppDimens.pageVertical + topReserve,
            end = AppDimens.pageHorizontal,
            bottom = AppDimens.pageVertical + bottomReserve
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.groupSpacing)
    ) {
        // 搜索框：备注 / 时长模糊匹配 + 日期条件（如「8月」「8月5日」「2026年8月」「2026-08-15」）
        item {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "搜索备注 / 日期 / 时长",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // 筛选 chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("全部", recordFilter is RecordFilter.All) { recordFilter = RecordFilter.All }
                FilterChip("工作日", recordFilter is RecordFilter.Type && (recordFilter as RecordFilter.Type).t == OvertimeType.WORKDAY) {
                    recordFilter = RecordFilter.Type(OvertimeType.WORKDAY)
                }
                FilterChip("休息日", recordFilter is RecordFilter.Type && (recordFilter as RecordFilter.Type).t == OvertimeType.WEEKEND) {
                    recordFilter = RecordFilter.Type(OvertimeType.WEEKEND)
                }
                FilterChip("节假日", recordFilter is RecordFilter.Type && (recordFilter as RecordFilter.Type).t == OvertimeType.HOLIDAY) {
                    recordFilter = RecordFilter.Type(OvertimeType.HOLIDAY)
                }
                FilterChip("请假", recordFilter is RecordFilter.Leave) { recordFilter = RecordFilter.Leave }
            }
        }

        if (!recordsLoaded) {
            item { RecordsLoadingPlaceholder() }
        } else if (records.isEmpty()) {
            item {
                RecordsEmptyState(
                    onAdd = {
                        // 连点时避免把多个 add_record 实例压入回退栈
                        navController.navigate("add_record") { launchSingleTop = true }
                    }
                )
            }
        } else if (grouped.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有匹配的记录",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            grouped.forEach { (month, list) ->
                val parts = month.split("-")
                stickyHeader(key = month) {
                    Surface(
                        color = MiuixTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = AppDimens.spaceXxs2,
                                    horizontal = AppDimens.spaceXxs
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${parts[0]}年${parts[1]}月",
                                style = AppTitle3Semibold(),
                                color = MiuixTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${list.size}条",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
                items(list, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        typeColorWorkday = typeColorWorkday,
                        typeColorWeekend = typeColorWeekend,
                        typeColorHoliday = typeColorHoliday,
                        onClick = { navController.navigate("edit_record/${record.id}") },
                        onDelete = {
                            if (deleteConfirmEnabled) {
                                selectedRecord = record
                                showDeleteDialog = true
                            } else {
                                performDelete(record)
                            }
                        }
                    )
                }
            }
        }
    }

    BackHandler(enabled = showDeleteDialog) { showDeleteDialog = false }
    OverlayDialog(
        show = showDeleteDialog,
        title = "确认删除",
        summary = "确定要删除这条记录吗？",
        onDismissRequest = { showDeleteDialog = false }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = "取消",
                onClick = { showDeleteDialog = false },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(
                text = "删除",
                onClick = {
                    val rec = selectedRecord
                    showDeleteDialog = false
                    rec?.let { performDelete(it) }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // MIUIX 的 Surface(onClick) 重载自带标准按压反馈与内容色透传，
    // 替代原先「Surface + clickable + 手写文字色」的组合
    Surface(
        onClick = onClick,
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer,
        contentColor = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer,
        shape = AppShapes.radiusLg
    ) {
        Text(
            text = text,
            style = if (selected) AppFootnote1Bold() else MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(
                horizontal = AppDimens.itemPaddingH,
                vertical = AppDimens.spaceXs
            )
        )
    }
}

/** 记录流首帧未到达时的占位骨架：避免出现「还没有记录」的误判空态 */
@Composable
private fun RecordsLoadingPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.groupSpacing)) {
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(AppShapes.radiusLg)
                    .background(MiuixTheme.colorScheme.surfaceContainer)
            )
        }
    }
}

/** 记录页空状态：复用全局标准件，仅按页面语义换插画图标。 */
@Composable
private fun RecordsEmptyState(onAdd: () -> Unit) {
    AppEmptyState(icon = MiuixIcons.Notes, onAction = onAdd)
}

@Composable
private fun RecordCard(
    record: OvertimeRecord,
    typeColorWorkday: Int,
    typeColorWeekend: Int,
    typeColorHoliday: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    // 日期格式：同年显示 MM-dd，跨年显示 yyyy-MM-dd。
    // 结果按 record.date 缓存，滚动重组时不再重复 new Calendar / SimpleDateFormat。
    val dateStr = remember(record.date) {
        val (year, _, _) = AppDateFormats.dateParts(record.date)
        val nowYear = AppDateFormats.dateParts(System.currentTimeMillis()).first
        if (year == nowYear) AppDateFormats.shortDay(record.date)
        else AppDateFormats.fullDay(record.date)
    }
    val displayLabel = if (record.isLeave) "请假" else record.type.label
    val typeColor = if (record.isLeave) {
        MiuixTheme.colorScheme.error
    } else {
        when (record.type) {
            OvertimeType.WORKDAY -> Color(typeColorWorkday)
            OvertimeType.WEEKEND -> Color(typeColorWeekend)
            OvertimeType.HOLIDAY -> Color(typeColorHoliday)
        }
    }

    // MIUIX Card 的 onClick 重载提供标准涟漪 + 按下下沉反馈
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppDimens.cardCorner,
        insideMargin = PaddingValues(12.dp),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(AppDimens.typeBarWidth)
                    .height(40.dp)
                    .background(typeColor, AppShapes.radiusXs)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateStr,
                        style = AppBody1Medium(),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayLabel,
                        style = MiuixTheme.textStyles.footnote1,
                        color = typeColor
                    )
                    if (record.isLeave) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MiuixTheme.colorScheme.error.copy(alpha = 0.1f),
                            shape = AppShapes.radiusSm
                        ) {
                            Text(
                                text = "扣薪",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (record.note.isNotBlank()) {
                    Text(
                        text = record.note,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                // 请假金额为负扣减，用错误色突出
                Text(
                    text = SalaryCalculator.formatAmount(record.amount),
                    style = AppBody1Semibold(),
                    color = if (record.isLeave) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                )
                Text(
                    // 请假在库中存负时长用于统计抵扣，列表里按正数展示并标注「请假」
                    text = if (record.isLeave) {
                        "请假 " + SalaryCalculator.formatHours(kotlin.math.abs(record.durationHours))
                    } else {
                        SalaryCalculator.formatHours(record.durationHours)
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(MiuixIcons.Delete, contentDescription = "删除", tint = MiuixTheme.colorScheme.error)
            }
        }
    }
}
