package com.overtime.miuix

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.overtime.miuix.data.database.AppDatabase
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.mcp.McpHostService
import com.overtime.miuix.ui.screen.*
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.theme.OvertimeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 运行时权限一次申请完：
        // - Android 13+ POST_NOTIFICATIONS：MCP 前台服务的常驻通知需要才能显示；
        // - Android 17（API 37）+ ACCESS_LOCAL_NETWORK：targetSdk >= 37 后访问局域网
        //   （WebDAV 备份、MCP 局域网接入）必需，未授权时系统 BPF 层静默丢包（表现为超时）。
        // 被永久拒绝后需到 系统设置→应用→权限 中手动开启。
        val pendingPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK)
                != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPermissions.add(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        if (pendingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                pendingPermissions.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }

        val database = AppDatabase.getDatabase(this)
        val repository = OvertimeRepository(database)
        val settingsRepository = SettingsRepository(this)

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = "system")
            val accentColor by settingsRepository.accentColor.collectAsState(initial = 0xFF3482FF.toInt())
            val monetEnabled by settingsRepository.monetEnabled.collectAsState(initial = false)

            val darkTheme = when (themeMode) {
                "dark", "monet_dark" -> true
                "light", "monet_light" -> false
                else -> isSystemInDarkTheme()
            }

            OvertimeTheme(
                darkTheme = darkTheme,
                keyColor = androidx.compose.ui.graphics.Color(accentColor),
                monet = monetEnabled
            ) {
                val navController = rememberNavController()
                MainNavHost(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository
                )
            }
        }
    }

    /**
     * 每次回到前台同步 MCP 服务状态：mcp_enabled 是持久化设置，但 Service 不会随
     * 应用进程重启自动复活，也可能被系统回收。旧实现只在设置页点开关时启动，
     * 冷启动后开关显示「已开启」而端口无人监听，是「MCP 服务无效」最典型的场景。
     * 放在 onStart 而非 onCreate，确保此时 Activity 已处于前台，可合法启动前台服务。
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val repo = SettingsRepository(this@MainActivity)
            val enabled = repo.mcpEnabled.first()
            if (enabled && !McpHostService.isRunning()) {
                McpHostService.start(this@MainActivity, repo.mcpPort.first())
            } else if (!enabled && McpHostService.isRunning()) {
                McpHostService.stop(this@MainActivity)
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }
}

@Composable
fun MainNavHost(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    // Miuix 的 OverlayDialog 内部使用 NavigationBackHandler，
    // 需要 LocalNavigationEventDispatcherOwner（navigationevent-compose 1.0.2）。
    // NavHost 自身不提供该值，此处创建根 dispatcher（parent = null）。
    val dispatcherOwner = rememberNavigationEventDispatcherOwner(
        enabled = true,
        parent = null
    )
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides dispatcherOwner,
        LocalSnackbarHostState provides remember { SnackbarHostState() }
    ) {
        NavHost(
            navController = navController,
            startDestination = "main",
            // 返回（pop）动画改为缩放：下层页自 0.92 放大淡入，当前页缩至 0.88 淡出；
            // 手势返回的实时预览（predictive back）复用同一组转场规格，预览即缩放效果
            popEnterTransition = {
                scaleIn(animationSpec = tween(300), initialScale = 0.92f) + fadeIn(tween(300))
            },
            popExitTransition = {
                scaleOut(animationSpec = tween(300), targetScale = 0.88f) + fadeOut(tween(300))
            }
        ) {
        composable("main") {
            MainScreen(
                navController = navController,
                repository = repository,
                settingsRepository = settingsRepository
            )
        }
        composable("add_record") {
            AddEditRecordPage(
                navController = navController,
                repository = repository,
                settingsRepository = settingsRepository
            )
        }
        composable("edit_record/{recordId}") { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString("recordId")?.toLongOrNull()
            AddEditRecordPage(
                navController = navController,
                repository = repository,
                settingsRepository = settingsRepository,
                recordId = recordId
            )
        }
        composable("statistics") {
            StatisticsPage(
                navController = navController,
                repository = repository,
                settingsRepository = settingsRepository
            )
        }
        composable("settings") {
            SettingsPage(navController = navController)
        }
        composable("push_settings") {
            PushSettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("backup_settings") {
            BackupSettingsPage(
                navController = navController,
                repository = repository,
                settingsRepository = settingsRepository
            )
        }
        composable("salary_settings") {
            SalarySettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("appearance_settings") {
            AppearanceSettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("calendar_settings") {
            CalendarSettingsPage(
                navController = navController,
                repository = repository,
                settingsRepository = settingsRepository
            )
        }
        composable("holiday_settings") {
            HolidaySettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("mcp_settings") {
            McpSettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("about") {
            AboutPage(navController = navController)
        }
        }
    }
}
