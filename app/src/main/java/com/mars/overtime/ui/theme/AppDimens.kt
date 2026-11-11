package com.mars.overtime.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局间距与尺寸令牌（设计令牌层）。
 *
 * 所有页面（首页 / 统计 / 增改 / 快速提报 / 关于 / 外观 / 各设置子页 / 底栏 / FAB）
 * 的尺寸与间距都必须从这里取，禁止再手写裸 `dp` 字面量，保证全 App 节奏一致。
 *
 * 取值来源：迁移时直接对齐各页既有字面量（如 `spaceMd = 16.dp` 对应原
 * `SettingsDimens.PageHorizontal`、首页/统计页的 `16.dp` 留白；`spaceSm = 12.dp`
 * 对应 `SettingsDimens.GroupSpacing`、各 `12.dp` 间距），因此替换后视觉零变化。
 *
 * 设置页目前仍使用 [SettingsDimens]（在 ui/screen 包内），其值与本对象的语义令牌
 * 一一对应；后续如需进一步收敛，可让 [SettingsDimens] 直接委托到本对象。
 */
object AppDimens {
    // ── 间距标尺（4 的倍数，覆盖全 App 出现的 0/4/8/12/16/24）──
    val zero = 0.dp       // 参数默认值（如 bottomReserve），语义为「无额外留白」
    val spaceXxs = 4.dp   // 卡片内最紧密间距（设置项纵向内边距等）
    val spaceXs = 8.dp    // 图标与文字、紧凑行内元素
    val spaceSm = 12.dp   // 分组卡片间距、标准内边距
    val spaceMd = 16.dp   // 页面左右/上下留白、卡片边距
    val spaceLg = 24.dp   // 大留白（头像、区块之间）

    // ── 页面语义间距（对齐首页/统计/设置页既有 16dp 留白）──
    val pageHorizontal = 16.dp
    val pageVertical = 16.dp
    val groupSpacing = 12.dp
    val itemHorizontal = 16.dp
    val itemVertical = 4.dp

    // ── 图表相关（StatisticsPage 柱状图，保持既有 2/4/8dp 视觉）──
    val chartBarRadius = 2.dp
    val chartBarGapSm = 4.dp
    val chartBarGapLg = 8.dp

    // ── 按钮行间距（SettingsButtonRow 的 spacedBy(8.dp)）──
    val buttonRowSpacing = 8.dp

    // ── 悬浮底栏 / FAB（原 MainScreen 私定义，统一收口到这里）──
    val navBarHeight = 64.dp            // 悬浮胶囊高度
    val navBarBottomReserve = 8.dp     // 悬浮底栏模式下列表末项额外留白
    val fabSize = 56.dp                // 标准 FAB 直径
    val fabExtendedHorizontal = 18.dp  // 扩展型 FAB 文字左右内边距
    val fabExtendedTextGap = 8.dp      // 扩展型 FAB 图标与文字间距
    val fabReserve = 16.dp             // FAB 与列表卡片右/下边缘的对齐留白

    // Snackbar：限宽后偏左显示，从水平方向避开右下角 FAB，无需再整体抬高
    val snackbarMaxWidth = 320.dp
    val snackbarEndReserve = fabSize + fabReserve + fabReserve

    // ── 通用组件内边距 ──
    val cardInnerPadding = 12.dp
    val listItemVertical = 8.dp

    // ── 控件语义圆角（对齐既有 Button cornerRadius 20.dp、Card cornerRadius 16.dp）──
    val cardCorner = 16.dp
    val controlCorner = 20.dp

    // ── 记录卡左侧类型色条宽度 ──
    val typeBarWidth = 4.dp

    // 统计页日历：今日不再用主色填充（默认主色与工作日底色同色相，会混淆），
    // 改用主色描边叠加在日期分类底色之上
    val dayTodayBorderWidth = 1.5.dp

    // ── 紧凑/微型间距（页面特有的 6/14dp 等保持原字面量，避免引入过多令牌）──
    val spaceXxs2 = 6.dp
    val itemPaddingH = 14.dp

    // ── 首页小组件网格 ──
    val hairline = 1.dp              // 编辑态描边、图表细柱间隙
    val widgetDragElevation = 8.dp   // 拖动中的抬升阴影
    val widgetCellHeight = 128.dp    // 1x1 卡片高度，2x1 / 2x2 由它推导（容纳标题/数值/进度条/胶囊四层）
    // 2x2 大图卡在两行基准之上额外增高：日历有「星期行 + 6 行格子 + 图例 + 摘要条」，
    // 240dp 内容区里格子会被压到 ~18dp。独占整行，加高不会造成左右错位。
    val widgetLargeExtraHeight = 28.dp
    val widgetCalendarCellGap = 3.dp // 日历格子行/列间距，避免色块上下连片
    // 密集数据文字用 dp 锚定字号，系统大字体下也不会撑破格子/摘要条
    val widgetCalendarDayText = 12.dp
    val widgetSummaryText = 14.dp
    val widgetDonutSize = 96.dp      // 2x2 卡片内环形图直径
    val overviewLabelWidth = 48.dp  // 概览卡分类行标签列宽
    val overviewValueWidth = 56.dp  // 概览卡分类行数值列宽

    // ── 关于页插画与功能图标 ──
    val aboutIconBox = 104.dp
    val aboutIcon = 84.dp
    val aboutIconFallback = 68.dp
    val featureIconBox = 44.dp
    val releaseNotesMaxHeight = 200.dp
    val dayCellHeight = 40.dp           // 统计页日历单个日期格高度

    // ── 首页空状态插画 ──
    val emptyCanvas = 156.dp
    val emptyCircle = 140.dp
    val emptyDotLarge = 42.dp
    val emptyDotSmall = 30.dp
    val emptyIcon = 64.dp
}
