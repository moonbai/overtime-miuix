package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
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
    val blurSupported = isRuntimeShaderSupported()

    val navMode = when (bottomBarStyle) {
        "ICON_ONLY" -> NavigationBarDisplayMode.IconOnly
        "TEXT_ONLY" -> NavigationBarDisplayMode.IconWithSelectedLabel
        else -> NavigationBarDisplayMode.IconAndText
    }

    // 首页 FAB 的高斯模糊背景层：捕获底栏之上的页面内容作为模糊源
    val fabBackdrop = rememberLayerBackdrop()
    // 悬浮底栏的高斯模糊背景层：捕获页面内容作为毛玻璃源
    val navBackdrop = rememberLayerBackdrop()
    val snackbarHostState = LocalSnackbarHostState.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = when (selectedTab) {
                    0 -> "加班记录"
                    1 -> "统计"
                    2 -> "设置"
                    else -> "加班记录"
                }
            )
        },
        bottomBar = {
            if (useFloatingNav) {
                // 悬浮底栏：IconOnly 模式（紧凑）+ 高斯模糊毛玻璃效果
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .textureBlur(
                            backdrop = navBackdrop,
                            shape = RoundedCornerShape(28.dp),
                            blurRadius = 40f,
                            enabled = blurSupported
                        )
                ) {
                    FloatingNavigationBar(
                        mode = FloatingNavigationBarDisplayMode.IconOnly,
                        cornerRadius = 28.dp,
                        horizontalOutSidePadding = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        FloatingNavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = MiuixIcons.All,
                            label = "首页"
                        )
                        FloatingNavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = MiuixIcons.Months,
                            label = "统计"
                        )
                        FloatingNavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = MiuixIcons.Settings,
                            label = "设置"
                        )
                    }
                }
            } else {
                NavigationBar(mode = navMode) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = MiuixIcons.All,
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
        },
        floatingActionButton = {
            if (selectedTab == 0) {
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
                        onClick = { navController.navigate("add_record") },
                        containerColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.6f)
                    ) {
                        Icon(MiuixIcons.Add, contentDescription = "添加记录")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(fabBackdrop)
                .layerBackdrop(navBackdrop)
                .padding(paddingValues)
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
    }
}
