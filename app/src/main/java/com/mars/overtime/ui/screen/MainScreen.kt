package com.mars.overtime.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.ui.component.AppFab
import com.mars.overtime.ui.component.LocalFabAnchor
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import com.mars.overtime.util.rememberBlurEnabled
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import com.mars.overtime.ui.icon.AppIcons
import com.mars.overtime.ui.icon.Home
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val bottomBarStyle by settingsRepository.bottomBarStyle.collectAsState(initial = "ICON_TEXT")
    val useFloatingNav by settingsRepository.useFloatingNav.collectAsState(initial = false)
    val floatingNavBottomOffset by settingsRepository.floatingNavBottomOffset
        .collectAsState(initial = 5)
    val quickSubmit by settingsRepository.quickSubmit.collectAsState(initial = false)
    val blurSupported = rememberBlurEnabled()

    // 首页是否存在记录：决定 FAB 是否显示。未添加任何记录时隐藏右下角 FAB，
    // 由首页空状态的主操作按钮承担「添加」入口，避免未记录时首页出现三个按钮；
    // 存在记录时显示 FAB、隐藏首页空状态（与用户预期「有记录时悬浮出现」一致）。
    // 只订阅 COUNT(*)：首页仅用它决定 FAB 显隐，订阅全表会在每次记录变更时拉回整张表
    val recordCount by repository.observeRecordCount().collectAsState(initial = 0)
    val hasRecords = recordCount > 0
    val context = androidx.compose.ui.platform.LocalContext.current

    // 快速提报对话框显隐
    var showQuickSubmit by remember { mutableStateOf(false) }
    val snackbarHostStateForQuick = LocalSnackbarHostState.current
    val quickScope = rememberCoroutineScope()

    // 首页列表状态由 MainScreen 持有：重复点击「首页」tab 时平滑回到顶部，
    // 记录多、下滑很深时无需手动一路滚回。记录页同理。
    val homeListState = rememberLazyListState()
    val recordsListState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    fun scrollHomeToTop() {
        scrollScope.launch { homeListState.animateScrollToItem(0) }
    }
    fun scrollRecordsToTop() {
        scrollScope.launch { recordsListState.animateScrollToItem(0) }
    }

    // 普通底栏显示模式（miuix 0.9.3：NavigationBarDisplayMode.TextOnly 已移除，仅保留图标/图标+文字）
    val navMode = when (bottomBarStyle) {
        "ICON_ONLY" -> NavigationBarDisplayMode.IconOnly
        else -> NavigationBarDisplayMode.IconAndText
    }

    // 悬浮/普通底栏的高斯模糊背景层：捕获页面内容作为毛玻璃源
    val navBackdrop = rememberLayerBackdrop()
    val snackbarHostState = LocalSnackbarHostState.current

    // 系统导航栏（手势条）底部安全间距，避免底栏被遮挡
    val navBarInset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    // 悬浮底栏：与屏幕底部的距离由外观设置控制（0..64dp），胶囊自身高度固定。
    // 以下留白 / FAB / Snackbar 位置全部基于这两个值联动，调节后整体不会错位。
    val floatingBarOffset = floatingNavBottomOffset.dp
    val floatingBarHeight = AppDimens.navBarHeight
    // 悬浮底栏「彻底悬浮」时，各页面列表需补充的底部留白（离底间距 + 估算高度 + 余量），
    // 保证末项（如「关于」）可滚动至悬浮底栏之上，不被遮挡。
    val floatingBottomReserve = floatingBarOffset + AppDimens.navBarHeight + AppDimens.navBarBottomReserve

    // FAB 的水平/垂直留白与首页、统计、设置页的卡片边距统一（16dp），
    // 避免右下角按钮与列表卡片右边缘错开。
    val floatingFabBottom = navBarInset + floatingBarOffset + floatingBarHeight +
        AppDimens.pageVertical
    // 普通模式的底部距离改为在 content 内用 Scaffold 实测的 bottomBar 高度计算：
    // 旧实现硬编码「64dp 底栏 + 24dp 间距」，在图标+文字底栏 / 大字体 / 不同 ROM 上
    // 会与底栏重叠或悬得过高。

    // 旧实现把提示整体抬到 FAB 顶部之上（约 88dp，悬浮底栏模式更高），
    // 结果「已删除 N 条记录」浮在屏幕中部、离操作区太远。改为：
    // 纵向只保留避让底栏的小幅上移，横向把 Host 可绘制区域从右侧收窄并给卡片限宽，
    // 让提示偏左显示，从水平方向绕开右下角的悬浮加号，不再拦截 FAB 触摸。
    val snackbarLift = AppDimens.spaceXs +
        (if (useFloatingNav) navBarInset + floatingBarOffset + floatingBarHeight else 0.dp)

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .padding(bottom = snackbarLift)
                    .padding(end = AppDimens.snackbarEndReserve),
                content = { data ->
                    Snackbar(
                        data,
                        modifier = Modifier.widthIn(max = AppDimens.snackbarMaxWidth)
                    )
                }
            )
        },
        topBar = {
            // 顶栏高斯模糊：内容层延伸到顶栏背后作为毛玻璃采样源，blurSupported 为 false
            // 或设备不支持运行时 shader 时回退到不透明 surface，保证标题始终可读。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .textureBlur(
                        backdrop = navBackdrop,
                        shape = AppShapes.radiusZero,
                        blurRadius = 40f,
                        enabled = blurSupported
                    )
            ) {
                SmallTopAppBar(
                    title = when (selectedTab) {
                        0 -> "首页"
                        1 -> "记录"
                        2 -> "统计"
                        else -> "设置"
                    },
                    color = if (blurSupported) Color.Transparent
                    else MiuixTheme.colorScheme.surface
                )
            }
        },
        // 普通底栏：高斯模糊背景
        bottomBar = {
            if (!useFloatingNav) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .textureBlur(
                            backdrop = navBackdrop,
                            shape = AppShapes.radiusZero,
                            blurRadius = 40f,
                            enabled = blurSupported
                        )
                ) {
                    NavigationBar(
                        modifier = Modifier.background(Color.Transparent),
                        mode = navMode,
                        color = if (blurSupported) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = {
                                // 已在首页时再次点击（即双击的第二次）回到列表顶部
                                if (selectedTab == 0) scrollHomeToTop() else selectedTab = 0
                            },
                            icon = AppIcons.Home,
                            label = "首页"
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = {
                                if (selectedTab == 1) scrollRecordsToTop() else selectedTab = 1
                            },
                            icon = MiuixIcons.Notes,
                            label = "记录"
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = MiuixIcons.Months,
                            label = "统计"
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = MiuixIcons.Settings,
                            label = "设置"
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        // 普通底栏模式：FAB 底部偏移取 Scaffold 实测的底栏高度 + 统一间距
        val normalFabBottom = paddingValues.calculateBottomPadding() +
            AppDimens.pageVertical

        // 入口加号 FAB 的「实际」底部偏移：统一发布给「添加记录」页的确认按钮。
        // 普通底栏取 Scaffold 实测高度、悬浮底栏取胶囊几何 + 导航栏 inset，
        // 两页共用同一个测量值，确认按钮才能落在与加号完全相同的屏幕坐标。
        val activeFabBottom = if (useFloatingNav) floatingFabBottom else normalFabBottom
        val fabAnchor = LocalFabAnchor.current
        SideEffect { fabAnchor.bottom = activeFabBottom }

        // 内容层不再用 Scaffold 的 top/bottom padding 把列表夹在两栏之间：
        // 否则顶栏/底栏背后没有内容可采样，textureBlur 只能模糊到一片空白，
        // 视觉上就是纯色块而不是毛玻璃。改为内容层铺满整屏（延伸到栏背后），
        // 由各页面列表自己用 contentPadding 给首项/末项留出避让空间。
        val topReserve = paddingValues.calculateTopPadding()
        val bottomReserve = if (useFloatingNav) {
            floatingBottomReserve
        } else {
            paddingValues.calculateBottomPadding()
        }
        val contentPadding = PaddingValues(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // 页面内容层（同时作为顶栏 / 底栏 / FAB 毛玻璃的背景源）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(navBackdrop)
                    .padding(contentPadding)
            ) {
                when (selectedTab) {
                    0 -> HomePage(
                        navController = navController,
                        repository = repository,
                        settingsRepository = settingsRepository,
                        onOpenRecords = { selectedTab = 1 },
                        topReserve = topReserve,
                        bottomReserve = bottomReserve,
                        listState = homeListState
                    )
                    1 -> RecordsPage(
                        navController = navController,
                        repository = repository,
                        settingsRepository = settingsRepository,
                        topReserve = topReserve,
                        bottomReserve = bottomReserve,
                        listState = recordsListState
                    )
                    2 -> StatisticsPage(
                        navController = navController,
                        repository = repository,
                        settingsRepository = settingsRepository,
                        topReserve = topReserve,
                        bottomReserve = bottomReserve
                    )
                    3 -> SettingsPage(
                        navController = navController,
                        topReserve = topReserve,
                        bottomReserve = bottomReserve
                    )
                }
            }

            // 悬浮底栏（叠加层）：自定义胶囊 + MIUIX 风格液态选中指示器
            if (useFloatingNav) {
                LiquidFloatingNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { index ->
                        // 与普通底栏保持一致：重复点击「首页 / 记录」回到对应列表顶部
                        if (index == selectedTab) {
                            when (index) {
                                0 -> scrollHomeToTop()
                                1 -> scrollRecordsToTop()
                            }
                        } else selectedTab = index
                    },
                    backdrop = navBackdrop,
                    blurSupported = blurSupported,
                    bottomOffset = floatingBarOffset,
                    navBarInset = navBarInset,
                    // 悬浮底栏此前硬编码只画图标，导致外观设置里的「图标+文字」无效。
                    // 与普通底栏共用同一份 bottomBarStyle 判定，两种底栏表现一致。
                    showLabel = navMode == NavigationBarDisplayMode.IconAndText,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // 悬浮模式首页 FAB：位于悬浮底栏上方；未添加任何记录时隐藏
                if (hasRecords && (selectedTab == 0 || selectedTab == 1)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = AppDimens.pageHorizontal, bottom = activeFabBottom)
                    ) {
                        AppFab(
                            quickSubmit = quickSubmit,
                            onAddRecord = {
                                // 连点加号时避免把多个 add_record 实例压入回退栈
                                navController.navigate("add_record") { launchSingleTop = true }
                            },
                            onQuickSubmit = { showQuickSubmit = true }
                        )
                    }
                }
            }

            // 普通模式首页 FAB：位于普通底栏上方；未添加任何记录时隐藏
            if (!useFloatingNav && hasRecords && (selectedTab == 0 || selectedTab == 1)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = AppDimens.pageHorizontal, bottom = activeFabBottom)
                ) {
                    AppFab(
                        quickSubmit = quickSubmit,
                        onAddRecord = {
                            // 连点加号时避免把多个 add_record 实例压入回退栈
                            navController.navigate("add_record") { launchSingleTop = true }
                        },
                        onQuickSubmit = { showQuickSubmit = true }
                    )
                }
            }

            // 快速提报对话框：必须位于 Scaffold 内容（fillMaxSize Box）内作为 descendant，
            // 否则 OverlayDialog 默认 renderInRootScaffold 在兄弟层级找不到根 Scaffold，弹窗不渲染
            QuickSubmitSheet(
                show = showQuickSubmit,
                repository = repository,
                settingsRepository = settingsRepository,
                context = context,
                onDismiss = { showQuickSubmit = false },
                onSaved = { message ->
                    quickScope.launch {
                        snackbarHostStateForQuick.showCustomToast(message)
                    }
                }
            )
        }
    }
}

/**
 * 悬浮胶囊底栏：选中态与 MIUIX 标准 NavigationBar 保持一致 —— 仅靠图标 / 文字
 * 变色（选中 = primary、未选中 = onSurfaceVariantSummary + 半粗），不使用选中背景
 * 色块（MIUIX 设计语言里导航项本就没有选中底块），从而与「普通底栏」视觉统一。
 *
 * 胶囊本体仍使用 MIUIX 的 textureBlur 毛玻璃与 MiuixTheme 配色，图标取自
 * MIUIX / AppIcons，视觉与框架设计语言保持一致。
 * 尺寸与圆角统一取自令牌层（AppDimens / AppShapes）。
 *
 * @param showLabel 是否显示图标下方文字，与外观设置 bottomBarStyle 联动：
 *                  true 为「图标+文字」，false 为「仅图标」。
 */
@Composable
private fun LiquidFloatingNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    blurSupported: Boolean,
    bottomOffset: Dp,
    navBarInset: Dp,
    showLabel: Boolean,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        AppIcons.Home to "首页",
        MiuixIcons.Notes to "记录",
        MiuixIcons.Months to "统计",
        MiuixIcons.Settings to "设置"
    )

    Box(
        modifier = modifier
            .padding(bottom = navBarInset + bottomOffset)
            .padding(horizontal = AppDimens.pageHorizontal * 2)
            .fillMaxWidth()
            .height(AppDimens.navBarHeight)
            .textureBlur(
                backdrop = backdrop,
                shape = AppShapes.navBar,
                blurRadius = 40f,
                enabled = blurSupported
            )
            .clip(AppShapes.navBar)
            .background(
                if (blurSupported) MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.3f)
                else MiuixTheme.colorScheme.surfaceContainer
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, (icon, label) ->
                Surface(
                    onClick = { onTabSelected(index) },
                    color = Color.Transparent,
                    shape = AppShapes.indicator,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val tint = if (selectedTab == index) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary
                        if (showLabel) {
                            // 图标 + 文字：胶囊固定 64dp 高，图标收至 24dp 并压缩间距，
                            // 保证文字不与液态指示器边缘相撞。
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(AppIconSize.md)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = tint,
                                    fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = tint,
                                modifier = Modifier.size(AppIconSize.lg)
                            )
                        }
                    }
                }
            }
        }
    }
}
