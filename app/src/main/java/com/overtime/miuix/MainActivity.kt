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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.overtime.miuix.util.SalaryCalculator
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
                // 计薪口径持久化在 DataStore，启动时及设置变化后同步给纯 Kotlin 计算工具，
                // 保证记录保存、推送、MCP 与设置页试算使用同一组参数。
                val workingDaysPerMonth by settingsRepository.workingDaysPerMonth
                    .collectAsState(initial = SalaryCalculator.DEFAULT_WORKING_DAYS_PER_MONTH)
                val hoursPerDay by settingsRepository.hoursPerDay
                    .collectAsState(initial = SalaryCalculator.DEFAULT_HOURS_PER_DAY)
                LaunchedEffect(workingDaysPerMonth, hoursPerDay) {
                    SalaryCalculator.configure(workingDaysPerMonth, hoursPerDay)
                }
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

/** 未识别的持久化值回退到默认动画，避免旧备份写入未知字符串后转场失效。 */
private fun resolveBackTransition(mode: String): BackTransition =
    BackTransition.entries.firstOrNull { it.name == mode } ?: BackTransition.SCALE_FADE

/** 返回（含预测性返回手势预览）转场规格。 */
private fun backEnter(mode: String): EnterTransition = when (resolveBackTransition(mode)) {
    BackTransition.SLIDE_HORIZONTAL -> slideInHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeIn(tween(300))
    BackTransition.SLIDE_VERTICAL -> slideInVertically(animationSpec = tween(300)) { -it / 4 } + fadeIn(tween(300))
    BackTransition.FADE -> fadeIn(tween(300))
    BackTransition.NONE -> EnterTransition.None
    else -> scaleIn(animationSpec = tween(300), initialScale = 0.92f) + fadeIn(tween(300))
}

private fun backExit(mode: String): ExitTransition = when (resolveBackTransition(mode)) {
    BackTransition.SLIDE_HORIZONTAL -> slideOutHorizontally(animationSpec = tween(300)) { it / 4 } + fadeOut(tween(300))
    BackTransition.SLIDE_VERTICAL -> slideOutVertically(animationSpec = tween(300)) { it / 4 } + fadeOut(tween(300))
    BackTransition.FADE -> fadeOut(tween(300))
    BackTransition.NONE -> ExitTransition.None
    else -> scaleOut(animationSpec = tween(300), targetScale = 0.88f) + fadeOut(tween(300))
}

/**
 * 为单个导航目的地提供独立的 [SnackbarHostState]。
 *
 * 旧实现全 App 共用一个 state，而每个页面的 Scaffold 各自挂载 SnackbarHost，
 * 且 MainScreen 为避让 FAB 额外抬高了 Host。页面返回时，子页面尚未消失的提示
 * 会被上一界面的 Host 重新渲染，位置从贴底跳到抬高（视觉上「往上移动」）。
 * 改为页面级 state 后，提示与当前页面生命周期绑定：随返回转场一起淡出，
 * 不会漂移到上一界面，也不会出现多个 Host 同时渲染同一条提示。
 */
@Composable
private fun PageSnackbarScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSnackbarHostState provides remember { SnackbarHostState() },
        content = content
    )
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
    // 返回动画可在「外观设置 → 页面转场」中切换；预测性返回手势预览复用同一组规格
    val backTransition by settingsRepository.predictiveBackTransition
        .collectAsState(initial = BackTransition.SCALE_FADE.name)
    // 预测性返回总开关：关闭后返回瞬时无动画
    val predictiveBackEnabled by settingsRepository.predictiveBackEnabled
        .collectAsState(initial = true)
    val popEnter: EnterTransition =
        if (predictiveBackEnabled) backEnter(backTransition) else EnterTransition.None
    val popExit: ExitTransition =
        if (predictiveBackEnabled) backExit(backTransition) else ExitTransition.None

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides dispatcherOwner,
        // 兜底默认值：各目的地都会用 PageSnackbarScope 覆盖为页面级 state
        LocalSnackbarHostState provides remember { SnackbarHostState() }
    ) {
        NavHost(
            navController = navController,
            startDestination = "main",
            popEnterTransition = { popEnter },
            popExitTransition = { popExit }
        ) {
        composable("main") {
            PageSnackbarScope {
                MainScreen(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("add_record") {
            PageSnackbarScope {
                AddEditRecordPage(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("edit_record/{recordId}") { backStackEntry ->
            PageSnackbarScope {
                val recordId = backStackEntry.arguments?.getString("recordId")?.toLongOrNull()
                AddEditRecordPage(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    recordId = recordId
                )
            }
        }
        composable("statistics") {
            PageSnackbarScope {
                StatisticsPage(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("settings") {
            PageSnackbarScope { SettingsPage(navController = navController) }
        }
        composable("push_settings") {
            PageSnackbarScope {
                PushSettingsPage(
                    navController = navController,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("backup_settings") {
            PageSnackbarScope {
                BackupSettingsPage(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("salary_settings") {
            PageSnackbarScope {
                SalarySettingsPage(
                    navController = navController,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("appearance_settings") {
            PageSnackbarScope {
                AppearanceSettingsPage(
                    navController = navController,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("calendar_settings") {
            PageSnackbarScope {
                CalendarSettingsPage(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("holiday_settings") {
            PageSnackbarScope {
                HolidaySettingsPage(
                    navController = navController,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("mcp_settings") {
            PageSnackbarScope {
                McpSettingsPage(
                    navController = navController,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("about") {
            PageSnackbarScope { AboutPage(navController = navController) }
        }
        }
    }
}
