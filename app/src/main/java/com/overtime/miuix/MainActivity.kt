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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.NavController
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.OnBackInvokedOverlayInput
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.overtime.miuix.ui.theme.OvertimeTheme
import com.overtime.miuix.util.SalaryCalculator
import kotlinx.coroutines.flow.collect
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

/**
 * 返回（含预测性返回手势预览）转场规格。
 * @param edge 最近一次返回手势的来源边缘：[NavigationEvent.EDGE_RIGHT] 时横向方向翻转，
 *             即手势从右侧返回则页面从右向左收束，从左侧返回则保持默认（左向右）。
 */
private fun backEnter(mode: String, edge: Int): EnterTransition = when (resolveBackTransition(mode)) {
    BackTransition.SLIDE_HORIZONTAL ->
        // 右边缘手势：上一页从右侧进入；左边缘手势：从左侧进入
        if (edge == NavigationEvent.EDGE_RIGHT)
            slideInHorizontally(animationSpec = tween(300)) { it / 4 } + fadeIn(tween(300))
        else
            slideInHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeIn(tween(300))
    BackTransition.SLIDE_VERTICAL -> slideInVertically(animationSpec = tween(300)) { -it / 4 } + fadeIn(tween(300))
    BackTransition.FADE -> fadeIn(tween(300))
    BackTransition.NONE -> EnterTransition.None
    else -> scaleIn(animationSpec = tween(300), initialScale = 0.92f) + fadeIn(tween(300))
}

private fun backExit(mode: String, edge: Int): ExitTransition = when (resolveBackTransition(mode)) {
    BackTransition.SLIDE_HORIZONTAL ->
        // 右边缘手势：当前页向左收束；左边缘手势：向右收束
        if (edge == NavigationEvent.EDGE_RIGHT)
            slideOutHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeOut(tween(300))
        else
            slideOutHorizontally(animationSpec = tween(300)) { it / 4 } + fadeOut(tween(300))
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
    // 需要 LocalNavigationEventDispatcherOwner（navigationevent-compose 1.1.2）。
    // NavHost 自身不提供该值，此处创建根 dispatcher（parent = null）。
    val dispatcherOwner = rememberNavigationEventDispatcherOwner(
        enabled = true,
        parent = null
    )
    val dispatcher = dispatcherOwner.navigationEventDispatcher

    // 将系统预测返回接入 dispatcher 树，让 MIUIX 弹窗（OverlayDialog 内部使用
    // NavigationBackHandler）能拦截返回手势并关闭弹窗：
    //   - 此前 root dispatcher 没有任何系统输入，返回手势从未进入该树，
    //     导致弹窗打开时按返回直接退出应用；
    //   - 使用 OverlayInput（优先级 1000000，高于 Activity 默认返回 0 / NavHost 所用），
    //     保证弹窗打开时其消费型 handler 优先于页面返回拿到手势。
    // 仅当树中存在「已启用（消费型）」handler 时，OverlayInput 才会注册系统回调，
    // 弹窗关闭后自动注销，因此不会干扰正常的页面返回导航（页面返回仍由 NavHost 处理）。
    val onBackInvokedDispatcher =
        (LocalContext.current as? ComponentActivity)?.onBackInvokedDispatcher
    DisposableEffect(dispatcher, onBackInvokedDispatcher) {
        val input = onBackInvokedDispatcher?.let { OnBackInvokedOverlayInput(it) }
        if (input != null) dispatcher.addInput(input)
        onDispose { input?.let { dispatcher.removeInput(it) } }
    }

    // 记录最近一次返回手势的来源边缘（左 / 右），供以下场景复用横向返回方向：
    //   - 跟手平移期间实时写入（见 snapshotFlow 收集）；
    //   - 按钮 / 代码触发的 pop（不走手势）沿用上一次边缘。
    val backSwipeEdge = remember { mutableIntStateOf(NavigationEvent.EDGE_LEFT) }

    // 容器宽度（px）与跟手偏移量：手势期间实时平移 NavHost 容器，实现「跟手」横向返回。
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val boxWidth = remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 是否可返回（仅在非根页面启用页面返回 handler，根页面保留系统「退出应用」）。
    val canGoBack = remember { mutableStateOf(navController.previousBackStackEntry != null) }
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            canGoBack.value = navController.previousBackStackEntry != null
            // 进入新页面时复位偏移，避免上一次手势残留位移。
            scope.launch { offset.snapTo(0f) }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // 返回动画可在「外观设置 → 页面转场」中切换；预测性返回手势预览复用同一组规格
    val backTransition by settingsRepository.predictiveBackTransition
        .collectAsState(initial = BackTransition.SCALE_FADE.name)
    // 预测性返回总开关：关闭后返回瞬时无动画
    val predictiveBackEnabled by settingsRepository.predictiveBackEnabled
        .collectAsState(initial = true)
    // 手势返回进行中（松手确认）时，由 offset 跟手平移「接管」整页转场，
    // 抑制 NavHost 自带 pop 动画，避免与跟手平移叠加成双重动画。
    val gesturePop = remember { mutableStateOf(false) }
    val popEnter: EnterTransition =
        if (predictiveBackEnabled && !gesturePop.value) backEnter(backTransition, backSwipeEdge.intValue)
        else EnterTransition.None
    val popExit: ExitTransition =
        if (predictiveBackEnabled && !gesturePop.value) backExit(backTransition, backSwipeEdge.intValue)
        else ExitTransition.None

    // 页面返回 handler（navigationevent 官方 Composable，自动完成 add / remove 与启用管理）。
    // 它消费页面级返回手势并驱动跟手平移；弹窗打开时，弹窗的 overlay handler 优先级更高且
    // 后注册，会先被命中，因此页面返回不会与弹窗关闭冲突。
    val backState = rememberNavigationEventState(
        NavigationEventInfo.None,
        emptyList(),
        emptyList()
    )
    fun widthPx(): Float = if (boxWidth.intValue > 0) boxWidth.intValue.toFloat() else screenWidthPx
    // 左边缘手势 → 内容从左向右滑出（+）；右边缘手势 → 从右向左滑出（-）。
    fun dir(edge: Int): Float = if (edge == NavigationEvent.EDGE_RIGHT) -1f else 1f

    // 跟手平移 + 边缘检测：收集「本 handler 自身」的转场状态（backState.transitionState），
    // 它只在页面返回 handler 真正接管手势时更新；弹窗返回由弹窗 handler 接管，不会误平移整页。
    // 手势进度（progress）直接驱动容器平移实现跟手；来源边缘（swipeEdge）翻转横向方向。
    LaunchedEffect(backState) {
        snapshotFlow { backState.transitionState }.collect { state ->
            if (state is NavigationEventTransitionState.InProgress) {
                // 手势进行中：抑制 NavHost 自带 pop 动画，由 offset 跟手平移独占驱动整页，
                // 避免「弹窗/页面」与「手写平移」叠加成双重动画。
                gesturePop.value = true
                val event = state.latestEvent
                backSwipeEdge.intValue = event.swipeEdge
                val p = event.progress.coerceIn(0f, 1f)
                scope.launch { offset.snapTo(dir(event.swipeEdge) * widthPx() * p) }
            }
        }
    }

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides dispatcherOwner,
        // 兜底默认值：各目的地都会用 PageSnackbarScope 覆盖为页面级 state
        LocalSnackbarHostState provides remember { SnackbarHostState() }
    ) {
        // 页面返回 handler 必须位于 dispatcherOwner 提供的 CompositionLocalProvider 内，
        // 才能将自身注册到根 dispatcher 并接管页面级返回手势。弹窗打开时弹窗的 overlay
        // handler 优先级更高且后注册，会先被命中，故不会与弹窗关闭冲突。
        NavigationBackHandler(
            state = backState,
            isBackEnabled = predictiveBackEnabled && canGoBack.value,
            onBackCancelled = {
                // 手势取消：当前页沿跟手方向回弹归位，并复位手势返回标记，
                // 让下一次（非手势）pop 仍能走 NavHost 自带转场。
                scope.launch {
                    offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    gesturePop.value = false
                }
            },
            onBackCompleted = {
                // 手势确认：当前页沿跟手方向继续滑出至整屏外完成收尾（跟手感延续），
                // 再由 NavHost 切换上一页——此刻 pop 动画已被 gesturePop 抑制，避免叠加；
                // 最后复位偏移与标记。方向由本次手势来源边缘决定（左→右滑 / 右→左滑）。
                scope.launch {
                    gesturePop.value = true
                    offset.animateTo(
                        dir(backSwipeEdge.intValue) * widthPx(),
                        spring(stiffness = Spring.StiffnessMediumLow)
                    )
                    navController.popBackStack()
                    offset.snapTo(0f)
                    gesturePop.value = false
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { boxWidth.intValue = it.width }
                .graphicsLayer { translationX = offset.value }
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
}
