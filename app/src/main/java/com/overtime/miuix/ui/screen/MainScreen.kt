package com.overtime.miuix.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
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
    val quickSubmit by settingsRepository.quickSubmit.collectAsState(initial = false)
    val blurSupported = isRuntimeShaderSupported()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 快速提报对话框显隐
    var showQuickSubmit by remember { mutableStateOf(false) }
    val snackbarHostStateForQuick = LocalSnackbarHostState.current
    val quickScope = rememberCoroutineScope()

    // 普通底栏显示模式
    val navMode = when (bottomBarStyle) {
        "ICON_ONLY" -> NavigationBarDisplayMode.IconOnly
        "TEXT_ONLY" -> NavigationBarDisplayMode.TextOnly
        else -> NavigationBarDisplayMode.IconAndText
    }

    // 悬浮底栏显示模式：与底栏样式设置保持一致
    val floatingNavMode = when (bottomBarStyle) {
        "ICON_ONLY" -> FloatingNavigationBarDisplayMode.IconOnly
        "TEXT_ONLY" -> FloatingNavigationBarDisplayMode.TextOnly
        else -> FloatingNavigationBarDisplayMode.IconAndText
    }

    // 首页 FAB 的高斯模糊背景层：捕获底栏之上的页面内容作为模糊源
    val fabBackdrop = rememberLayerBackdrop()
    // 悬浮底栏的高斯模糊背景层：捕获页面内容作为毛玻璃源
    val navBackdrop = rememberLayerBackdrop()
    val snackbarHostState = LocalSnackbarHostState.current

    // 系统导航栏（手势条）底部安全间距，避免悬浮底栏被遮挡
    val navBarInset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        // 普通底栏：放在 bottomBar 槽位（会预留底部空间）
        bottomBar = {
            if (!useFloatingNav) {
                // 普通底栏：毛玻璃背景
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .textureBlur(
                            backdrop = navBackdrop,
                            shape = RoundedCornerShape(0.dp),
                            blurRadius = 40f,
                            enabled = blurSupported
                        )
                ) {
                    NavigationBar(
                        modifier = Modifier.background(Color.Transparent),
                        mode = navMode
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
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
        },
        // 普通模式下的 FAB（悬浮模式下改由内容层叠加渲染，避免与悬浮底栏重叠）
        floatingActionButton = {
            if (!useFloatingNav && selectedTab == 0) {
                HomeFab(
                    quickSubmit = quickSubmit,
                    fabBackdrop = fabBackdrop,
                    blurSupported = blurSupported,
                    onAddRecord = { navController.navigate("add_record") },
                    onQuickSubmit = { showQuickSubmit = true }
                )
            }
        }
    ) { paddingValues ->
        // 悬浮模式下不预留底部空间，让页面内容铺满并可滚动到悬浮底栏之下
        val contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
            bottom = if (useFloatingNav) 0.dp else paddingValues.calculateBottomPadding()
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // 页面内容层（捕获到 fabBackdrop / navBackdrop 供毛玻璃使用）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(fabBackdrop)
                    .layerBackdrop(navBackdrop)
                    .padding(contentPadding)
            ) {
                when (selectedTab) {
                    0 -> HomePage(
                        navController = navController,
                        repository = repository,
                        settingsRepository = settingsRepository
                    )
                    1 -> StatisticsPage(
                        navController = navController,
                        repository = repository,
                        settingsRepository = settingsRepository
                    )
                    2 -> SettingsPage(navController = navController)
                }
            }

            // 悬浮底栏：真正悬浮于内容之上（叠加层，不预留空间）
            if (useFloatingNav) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 10.dp,
                            bottom = 10.dp + navBarInset
                        )
                        .textureBlur(
                            backdrop = navBackdrop,
                            shape = RoundedCornerShape(28.dp),
                            blurRadius = 40f,
                            enabled = blurSupported
                        )
                ) {
                    FloatingNavigationBar(
                        modifier = Modifier.background(Color.Transparent),
                        mode = floatingNavMode,
                        cornerRadius = 28.dp,
                        horizontalOutSidePadding = 0.dp,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FloatingNavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = AppIcons.Home,
                            label = "首页"
                        )
                        // 按钮之间留白，避免拥挤
                        Spacer(modifier = Modifier.width(24.dp))
                        FloatingNavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = MiuixIcons.Months,
                            label = "统计"
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        FloatingNavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = MiuixIcons.Settings,
                            label = "设置"
                        )
                    }
                }

                // 悬浮模式下的首页 FAB：叠加在内容之上，并抬升到底栏上方避免遮挡
                if (selectedTab == 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 10.dp + navBarInset + 72.dp)
                    ) {
                        HomeFab(
                            quickSubmit = quickSubmit,
                            fabBackdrop = fabBackdrop,
                            blurSupported = blurSupported,
                            onAddRecord = { navController.navigate("add_record") },
                            onQuickSubmit = { showQuickSubmit = true }
                        )
                    }
                }
            }
        }
    }

    // 快速提报对话框
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

/**
 * 首页悬浮操作按钮：快速提报模式下提供「添加 / 快速提报」两个按钮，
 * 否则为带有高斯模糊背景的单个添加按钮。
 */
@Composable
private fun HomeFab(
    quickSubmit: Boolean,
    fabBackdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    blurSupported: Boolean,
    onAddRecord: () -> Unit,
    onQuickSubmit: () -> Unit
) {
    if (quickSubmit) {
        // 快速提报模式：底栏区直接提供「提报」按钮，点击弹出快速录入
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 常规添加（进入完整表单）
            FloatingActionButton(
                onClick = onAddRecord,
                containerColor = MiuixTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    MiuixIcons.Add,
                    contentDescription = "添加记录",
                    tint = MiuixTheme.colorScheme.onSecondaryContainer
                )
            }
            // 快速提报（一键记录今天）
            FloatingActionButton(
                onClick = onQuickSubmit,
                containerColor = MiuixTheme.colorScheme.primary
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        MiuixIcons.Ok,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "快速提报",
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
            }
        }
    } else {
        // Texture Blur 高斯模糊：将 FAB 背景渲染为页面内容的毛玻璃
        Box(
            modifier = Modifier.textureBlur(
                backdrop = fabBackdrop,
                shape = CircleShape,
                blurRadius = 28f,
                enabled = blurSupported
            )
        ) {
            FloatingActionButton(
                onClick = onAddRecord,
                containerColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.6f)
            ) {
                Icon(MiuixIcons.Add, contentDescription = "添加记录")
            }
        }
    }
}
