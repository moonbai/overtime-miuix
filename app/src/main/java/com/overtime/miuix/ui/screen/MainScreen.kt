package com.overtime.miuix.ui.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.component.AppFab
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import com.overtime.miuix.ui.theme.AppDimens
import com.overtime.miuix.ui.theme.AppIconSize
import com.overtime.miuix.ui.theme.AppShapes
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import com.overtime.miuix.ui.icon.AppIcons
import com.overtime.miuix.ui.icon.Home
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
    val blurSupported = isRuntimeShaderSupported()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 快速提报对话框显隐
    var showQuickSubmit by remember { mutableStateOf(false) }
    val snackbarHostStateForQuick = LocalSnackbarHostState.current
    val quickScope = rememberCoroutineScope()

    // 首页列表状态由 MainScreen 持有：重复点击「首页」tab 时平滑回到顶部，
    // 记录多、下滑很深时无需手动一路滚回。
    val homeListState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    fun scrollHomeToTop() {
        scrollScope.launch { homeListState.animateScrollToItem(0) }
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
            SmallTopAppBar(
                title = when (selectedTab) {
                    0 -> "加班记录"
                    1 -> "统计"
                    2 -> "设置"
                    else -> "加班记录"
                }
            )
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
                            onClick = { selectedTab = 1 },
                            icon = MiuixIcons.Months,
                            label = "统计"
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
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

        // 内容底部留白：
        // - 普通底栏：Scaffold 已为其预留空间（paddingValues 含底栏高度），内容自然不被遮挡；
        // - 悬浮底栏（彻底悬浮）：内容层底部不预留空间，可滚动至底栏「背后」，
        //   毛玻璃透出其后内容；末项防遮挡由各页面列表自身底部留白（bottomReserve）保证。
        val contentBottom = if (useFloatingNav) {
            0.dp
        } else {
            paddingValues.calculateBottomPadding()
        }
        // 仅悬浮模式给各页面列表补充底部留白（普通模式由 Scaffold 预留空间，无需额外留白）
        val bottomReserve = if (useFloatingNav) floatingBottomReserve else 0.dp
        val contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
            bottom = contentBottom
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // 页面内容层（同时作为 FAB / 底栏毛玻璃的背景源）
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
                        bottomReserve = bottomReserve,
                        listState = homeListState
                    )
                    1 -> StatisticsPage(
                        navController = navController,
                        repository = repository,
                        settingsRepository = settingsRepository,
                        bottomReserve = bottomReserve
                    )
                    2 -> SettingsPage(
                        navController = navController,
                        bottomReserve = bottomReserve
                    )
                }
            }

            // 悬浮底栏（叠加层）：自定义胶囊 + MIUIX 风格液态选中指示器
            if (useFloatingNav) {
                LiquidFloatingNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { index ->
                        // 与普通底栏保持一致：重复点击「首页」回到顶部
                        if (index == 0 && selectedTab == 0) scrollHomeToTop()
                        else selectedTab = index
                    },
                    backdrop = navBackdrop,
                    blurSupported = blurSupported,
                    bottomOffset = floatingBarOffset,
                    navBarInset = navBarInset,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // 悬浮模式首页 FAB：位于悬浮底栏上方
                if (selectedTab == 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = AppDimens.pageHorizontal, bottom = floatingFabBottom)
                    ) {
                        AppFab(
                            quickSubmit = quickSubmit,
                            onAddRecord = { navController.navigate("add_record") },
                            onQuickSubmit = { showQuickSubmit = true }
                        )
                    }
                }
            }

            // 普通模式首页 FAB：位于普通底栏上方
            if (!useFloatingNav && selectedTab == 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = AppDimens.pageHorizontal, bottom = normalFabBottom)
                ) {
                    AppFab(
                        quickSubmit = quickSubmit,
                        onAddRecord = { navController.navigate("add_record") },
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
 * 悬浮胶囊底栏：MIUIX 0.9.4 的 FloatingNavigationBarItem 仅对选中 icon 着色，
 * 这里补上 HyperOS 风格的「液态」选中指示器 —— 两层圆角胶囊以不同刚度的
 * spring 动画在三个等宽 item 间滑动，主体先到、尾层滞后回弹，形成流体拖尾。
 *
 * 胶囊本体仍使用 MIUIX 的 textureBlur 毛玻璃与 MiuixTheme 配色，图标取自
 * MIUIX / AppIcons，视觉与框架设计语言保持一致。
 * 尺寸与圆角统一取自令牌层（AppDimens / AppShapes）。
 */
@Composable
private fun LiquidFloatingNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    blurSupported: Boolean,
    bottomOffset: Dp,
    navBarInset: Dp,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        AppIcons.Home to "首页",
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / 3f
            val target = itemWidth * selectedTab.toFloat()

            // 双层不同刚度的 spring：主体快速到位，尾层慢半拍形成液体拉伸感
            val leadX by animateDpAsState(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "liquidLead"
            )
            val trailX by animateDpAsState(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "liquidTrail"
            )

            // 尾层：慢、淡
            Box(
                modifier = Modifier
                    .offset(x = trailX)
                    .width(itemWidth)
                    .height(AppDimens.navBarHeight)
                    .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceXs)
                    .clip(AppShapes.indicator)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
            )
            // 主体：快、实
            Box(
                modifier = Modifier
                    .offset(x = leadX)
                    .width(itemWidth)
                    .height(AppDimens.navBarHeight)
                    .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceXs)
                    .clip(AppShapes.indicator)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.18f))
            )
        }

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
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = if (selectedTab == index) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(AppIconSize.lg)
                        )
                    }
                }
            }
        }
    }
}
