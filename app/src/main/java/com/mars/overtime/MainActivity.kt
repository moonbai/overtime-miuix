package com.mars.overtime

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.view.MotionEvent
import android.window.BackEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import com.mars.overtime.util.AppDefaults
import com.mars.overtime.util.FeaturePermissions
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.core.LinearEasing
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
import com.mars.overtime.data.database.AppDatabase
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.mcp.McpHostService
import com.mars.overtime.security.AppLockManager
import com.mars.overtime.ui.component.FabAnchorState
import com.mars.overtime.ui.component.LocalFabAnchor
import com.mars.overtime.ui.screen.*
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.OnBackInvokedOverlayInput
import com.mars.overtime.ui.theme.OvertimeTheme
import com.mars.overtime.util.SalaryCalculator
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        /** 通知 / 外部入口携带的目标导航路由 extra，值为 NavHost 中的路由字符串。 */
        const val EXTRA_NAVIGATE_TO = "navigate_to"

        // 桌面长按 shortcut：action 为主识别通道，extra / shortcutId 为备用。
        // 部分国产 launcher 会把静态 shortcut 的 intent action 规范化成 MAIN，
        // 只认 action 会出现「应用打开了但动作没执行」。
        const val ACTION_SHORTCUT_QUICK_SUBMIT = "com.mars.overtime.action.QUICK_SUBMIT"
        const val ACTION_SHORTCUT_RECORDS = "com.mars.overtime.action.RECORDS"
        const val ACTION_SHORTCUT_START_MCP = "com.mars.overtime.action.START_MCP"
        const val EXTRA_SHORTCUT_ACTION = "com.mars.overtime.shortcut_action"

        private const val SHORTCUT_QUICK_SUBMIT = "quick_submit"
        private const val SHORTCUT_RECORDS = "records"
        private const val SHORTCUT_START_MCP = "start_mcp"

        private val VALID_SHORTCUTS = setOf(
            SHORTCUT_QUICK_SUBMIT, SHORTCUT_RECORDS, SHORTCUT_START_MCP
        )
    }

    /**
     * 最近一次返回手势的来源边缘（左 / 右），供「退出方向 = 跟随手势」使用。
     *
     * 恢复 NavHost 原生预测性返回后，页面级返回手势不再经过 App 自建的
     * NavigationEventDispatcher（那个 dispatcher 只在弹窗打开时收到事件），
     * 因此这里改由 Activity 层采集，见 [dispatchTouchEvent] 与 [registerBackObserver]。
     * 手势开始后方向即冻结，seek 期间不再改写，避免转场规格被重建。
     */
    private val backSwipeEdge = mutableIntStateOf(NavigationEvent.EDGE_LEFT)

    /**
     * 待执行的导航路由（如通知点击携带的 "mcp_settings"）。
     * 通知点击两条路径都会写入：冷启动在 onCreate 读 intent、应用已在前台/后台
     * 走 onNewIntent。MainNavHost 内的 LaunchedEffect 消费后置空，避免重复跳转。
     */
    private val pendingNavigation = mutableStateOf<String?>(null)

    /** 桌面 shortcut 待执行动作；只在 AppLockGate 门内消费，未解锁不执行。 */
    private val pendingShortcutAction = mutableStateOf<String?>(null)

    /**
     * 已下发给主界面的 shortcut 事件 token 与动作类型。
     * 用一次性 token 而非「字符串参数 + 双方各自 LaunchedEffect」：MainScreen 可能因
     * 导航被移出组合再重建，token 留在 Activity 层不会丢，重建后仍能补上动作；
     * MainScreen 记住已处理的 token，避免同一事件重复弹出。token 每次都不同，
     * 因此 Activity 配置变更重建后字段归零也不会与历史值撞车。
     */
    private val shortcutEventToken = mutableStateOf<String?>(null)
    private val shortcutEventKind = mutableStateOf<String?>(null)

    /** 系统预测性返回手势的实时进度（0..1），仅 API 36+ 的非消费型观察者能读到。 */
    private val backProgress = mutableFloatStateOf(0f)
    private var backObserver: OnBackAnimationCallback? = null

    /** 启动锁是否放行 MCP 服务：上锁期间不启动/主动停止，解锁后按 mcp_enabled 恢复。 */
    @Volatile
    private var mcpAllowedByLock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 窗口背景设为「跟随主题的不透明色」（具体颜色在 setContent 内按 darkTheme 同步）。
        // 不能用全透明：预测性返回手势会缩放/淡出整个窗口，透明窗口会露出桌面或下层窗口的
        // 深色，浅色模式下表现为「返回预览闪一下深色」。不透明底色既挡住该深色，
        // 又因与 Compose 背景同色，主题切换时不会露出旧的白色/黑色，白闪同样消除。
        window.setBackgroundDrawable(
            // 与浅色主题 colorScheme.background 同色；setContent 内会按 darkTheme 动态覆盖
            android.graphics.drawable.ColorDrawable(AppDefaults.BACKGROUND_ARGB)
        )

        // 通知点击冷启动：从 intent 读取目标路由（如 MCP 服务子界面）
        pendingNavigation.value = intent?.getStringExtra(EXTRA_NAVIGATE_TO)
        // 桌面 shortcut：识别后清除 intent 上的所有标记，防止配置变更重复消费
        pendingShortcutAction.value = resolveShortcutAction(intent)

        // 启动流程不申请任何运行时权限：POST_NOTIFICATIONS / ACCESS_LOCAL_NETWORK
        // 只在用户主动启用 MCP 服务、WebDAV 云端备份等开关那一刻按需请求，
        // 见 FeaturePermissions 与各功能设置页。此处保持零弹窗。

        val database = AppDatabase.getDatabase(this)
        val repository = OvertimeRepository(database)
        val settingsRepository = SettingsRepository(this)
        registerBackObserver()
        observeAppLockForMcp(settingsRepository)

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = "system")
            val accentColor by settingsRepository.accentColor.collectAsState(initial = AppDefaults.PRIMARY_ARGB)
            val monetEnabled by settingsRepository.monetEnabled.collectAsState(initial = false)

            val darkTheme = when (themeMode) {
                "dark", "monet_dark" -> true
                "light", "monet_light" -> false
                // "system" / "monet_system" 及未知值都跟随系统深浅
                else -> isSystemInDarkTheme()
            }

            OvertimeTheme(
                darkTheme = darkTheme,
                keyColor = androidx.compose.ui.graphics.Color(accentColor),
                // 以主题模式为准：旧备份可能只有 theme_mode 而缺 monet_enabled，
                // 用前缀兜底保证 monet_* 选项仍能启用系统壁纸动态取色。
                monet = themeMode.startsWith("monet_") || monetEnabled
            ) {
                // 状态栏 / 导航栏图标（时间、电量、手势条）的明暗随主题切换：
                // MiuixTheme 不管理该项，默认始终深色图标，深色模式下在深色背景上不可见。
                // 此处显式跟随 darkTheme 翻转（深色主题用浅色图标、浅色主题用深色图标）。
                val view = LocalView.current
                val window = (view.context as ComponentActivity).window
                // 当前主题的真实背景色：用它刷 window 背景，保证预测性返回缩放窗口时
                // 露出的是同色底而非桌面深色；主题切换时随 darkTheme 一起更新，避免残留旧色。
                val windowBg = MiuixTheme.colorScheme.background.toArgb()
                SideEffect {
                    window.setBackgroundDrawable(
                        android.graphics.drawable.ColorDrawable(windowBg)
                    )
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    val wic = WindowInsetsControllerCompat(window, view)
                    wic.isAppearanceLightStatusBars = !darkTheme
                    wic.isAppearanceLightNavigationBars = !darkTheme
                }
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
                // 启动锁门控：未解锁时不组合主界面，也不消费通知 deep link。
                // navController 放在门控外，解锁后能恢复原有导航栈。
                AppLockGate(settingsRepository = settingsRepository) {
                    // 消费待执行的导航：通知点击后直达目标子界面（如 MCP 服务设置）
                    LaunchedEffect(pendingNavigation.value) {
                        pendingNavigation.value?.let { route ->
                            navController.navigate(route) { launchSingleTop = true }
                            pendingNavigation.value = null
                        }
                    }
                    // 桌面 shortcut 动作统一在门内消费：锁屏时这段不会组合，
                    // 因此「启动 MCP」绝不会在未解锁状态下把服务拉起。
                    // 动作在这里一次性转成事件序号，MainScreen 不再参与「清空 pending」，
                    // 消除两个 LaunchedEffect 抢同一个 key 的竞态。
                    val shortcutAction = pendingShortcutAction.value
                    LaunchedEffect(shortcutAction) {
                        if (shortcutAction == null) return@LaunchedEffect
                        try {
                            when (shortcutAction) {
                                SHORTCUT_START_MCP -> startMcpFromShortcut()
                                SHORTCUT_QUICK_SUBMIT, SHORTCUT_RECORDS -> {
                                    // 热启动停留在子页面时回到主界面。用 popBackStack 而非
                                    // navigate，避免重建 main 导致弹窗状态被重置。
                                    navController.popBackStack("main", inclusive = false)
                                    shortcutEventKind.value = shortcutAction
                                    shortcutEventToken.value =
                                        "$shortcutAction-${System.nanoTime()}"
                                }
                            }
                        } finally {
                            pendingShortcutAction.value = null
                        }
                    }
                    MainNavHost(
                        navController = navController,
                        repository = repository,
                        settingsRepository = settingsRepository,
                        backSwipeEdge = backSwipeEdge,
                        shortcutEventToken = shortcutEventToken.value,
                        shortcutEventKind = shortcutEventKind.value
                    )
                }
            }
        }
    }

    /**
     * Android 16（API 36）起系统提供「非消费型」返回观察者优先级：
     * 注册后可在不拦截、不消费返回事件的前提下收到完整的 started / progressed /
     * cancelled 序列，NavHost 的返回处理完全不受影响。这里用它同时采集 swipeEdge
     * 与 progress，是「跟随手势」最准确的来源；少数 ROM 拒绝该优先级时静默降级到
     * [dispatchTouchEvent] 的边缘推断。
     */
    private fun registerBackObserver() {
        if (Build.VERSION.SDK_INT < 36) return
        try {
            val observer = object : OnBackAnimationCallback {
                override fun onBackStarted(backEvent: BackEvent) {
                    Log.d("MainActivity", "onBackStarted swipeEdge=${backEvent.swipeEdge}")
                    backProgress.floatValue = backEvent.progress
                    applyBackEdge(backEvent.swipeEdge)
                }

                override fun onBackProgressed(backEvent: BackEvent) {
                    val previous = backProgress.floatValue
                    // 首次进度回调再校准一次边缘（部分 ROM 只在 progressed 给出正确值），
                    // 之后整段 seek 期间方向保持冻结，避免 popEnter / popExit 规格重建。
                    if (previous <= 0f) applyBackEdge(backEvent.swipeEdge)
                    // 只接受单调递增的进度，过滤 ROM 回传的抖动值
                    if (backEvent.progress >= previous) {
                        backProgress.floatValue = backEvent.progress
                    }
                }

                override fun onBackCancelled() {
                    backProgress.floatValue = 0f
                }

                override fun onBackInvoked() {
                    backProgress.floatValue = 0f
                }
            }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM_NAVIGATION_OBSERVER,
                observer
            )
            backObserver = observer
            Log.d("MainActivity", "返回观察者注册成功 (API ${Build.VERSION.SDK_INT})")
        } catch (t: Throwable) {
            Log.w("MainActivity", "返回手势观察者注册失败，降级为触摸边缘推断", t)
        }
    }

    /** 把平台 BackEvent 的边缘常量显式映射到 navigationevent 常量，不假定两套常量值一致。 */
    private fun applyBackEdge(edge: Int) {
        backSwipeEdge.intValue = when (edge) {
            BackEvent.EDGE_RIGHT -> NavigationEvent.EDGE_RIGHT
            BackEvent.EDGE_LEFT -> NavigationEvent.EDGE_LEFT
            else -> return
        }
    }

    /**
     * API 33~35 没有非消费型返回观察者：从边缘起手的 ACTION_DOWN 推断来源。
     * 系统识别返回手势前窗口已收到这次 DOWN（随后才收到 CANCEL），因此可据此定向；
     * 只记录左右各 15% 边缘区域，普通点击不会误改方向。API 36+ 上观察者随后覆盖。
     * 每次 DOWN 同时清掉上一次手势的进度，避免 commit 后状态残留影响下一次识别。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            backProgress.floatValue = 0f
            // 窗口实际宽度优先：分屏 / 小窗 / 折叠屏上 displayMetrics.widthPixels
            // 是屏幕宽而非窗口宽，右边缘起手时 x 永远够不到 width-zone，
            // 导致方向恒为初始左值（表现为「怎么滑都往右退」）。decorView 未布局
            // （宽度为 0）时才回退到屏幕宽。
            val windowWidth = window.decorView.width.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels
            val zone = windowWidth * 0.15f
            val edge = when {
                ev.x <= zone -> NavigationEvent.EDGE_LEFT
                ev.x >= windowWidth - zone -> NavigationEvent.EDGE_RIGHT
                else -> null
            }
            if (edge != null) {
                Log.d("MainActivity", "touch DOWN edge=$edge x=${ev.x.toInt()} width=$windowWidth")
                backSwipeEdge.intValue = edge
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * MainActivity 声明为 singleTop：通知点击时若 Activity 已在栈顶，
     * 系统不再新建实例而是回调这里，intent 中携带的目标路由随后由
     * setContent 内的 LaunchedEffect 消费并导航。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_NAVIGATE_TO)?.let { pendingNavigation.value = it }
        resolveShortcutAction(intent)?.let { pendingShortcutAction.value = it }
    }

    /**
     * 识别桌面 shortcut 动作，三重通道兜底：
     * 1) intent.action（静态 shortcuts.xml 声明的主通道）
     * 2) intent extra（部分 ROM 会把 action 规范化，extra 仍保留）
     * 3) Intent.EXTRA_SHORTCUT_ID（系统启动 shortcut 时附带的 shortcutId）
     *
     * 识别后清除这三个标记：Activity 因配置变更重建时 onCreate 会再次读同一个
     * intent，不清除将导致重复提报 / 重复启动服务。
     */
    private fun resolveShortcutAction(intent: Intent?): String? {
        if (intent == null) return null
        val fromAction = when (intent.action) {
            ACTION_SHORTCUT_QUICK_SUBMIT -> SHORTCUT_QUICK_SUBMIT
            ACTION_SHORTCUT_RECORDS -> SHORTCUT_RECORDS
            ACTION_SHORTCUT_START_MCP -> SHORTCUT_START_MCP
            else -> null
        }
        val fromExtra = intent.getStringExtra(EXTRA_SHORTCUT_ACTION)
            ?.takeIf { it in VALID_SHORTCUTS }
        val fromId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
            ?.takeIf { it in VALID_SHORTCUTS }
        val result = fromAction ?: fromExtra ?: fromId
        if (result == null) {
            // 真机排查通道：若 ROM 把三个标记全部改写，logcat 里能看到原始 intent 信息
            Log.d(
                TAG, "shortcut: unmatched action=${intent.action}" +
                    " extra=${intent.getStringExtra(EXTRA_SHORTCUT_ACTION)}" +
                    " id=${intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)}"
            )
            return null
        }
        Log.d(TAG, "shortcut resolved=$result from=" +
            when {
                fromAction != null -> "action"
                fromExtra != null -> "extra"
                else -> "shortcutId"
            })
        intent.action = Intent.ACTION_MAIN
        intent.removeExtra(EXTRA_SHORTCUT_ACTION)
        intent.removeExtra(Intent.EXTRA_SHORTCUT_ID)
        return result
    }

    override fun onDestroy() {
        backObserver?.let {
            try {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            } catch (_: Throwable) {
            }
        }
        backObserver = null
        super.onDestroy()
    }

    /**
     * 启动锁状态变化时同步 MCP 服务：上锁（含冷启动未解锁）立即停服，解锁后按
     * mcp_enabled 恢复。放在 onCreate 持续 collect，覆盖「解锁瞬间」这个时点。
     */
    private fun observeAppLockForMcp(settingsRepository: SettingsRepository) {
        lifecycleScope.launch {
            combine(
                settingsRepository.appLockEnabled,
                AppLockManager.isUnlocked
            ) { enabled: Boolean, unlocked: Boolean -> !(enabled && !unlocked) }
                .distinctUntilChanged()
                .collect { allowed ->
                    mcpAllowedByLock = allowed
                    syncMcpService(allowed)
                }
        }
    }

    /**
     * 每次回到前台同步 MCP 服务状态：mcp_enabled 是持久化设置，但 Service 不会随
     * 应用进程重启自动复活，也可能被系统回收。放在 onStart 而非 onCreate，确保此时
     * Activity 已处于前台，可合法启动前台服务。这里只恢复已开启的服务，绝不补申请权限。
     */
    /**
     * 桌面 shortcut「一键自启动 MCP」。调用点位于 AppLockGate 门内，已确保解锁。
     *
     * 这是一个「从桌面触发、界面无开关可点」的动作，必须给出明确反馈：
     * - 先弹即时 Toast，覆盖 DataStore 读取与 Service 启动之间的等待；
     * - 全流程 try 包裹，任一步异常都取消占位 Toast 并给出结果提示，绝不静默失败；
     * - 结果 Toast 用 LENGTH_LONG，文案明确写出「自启动」与端口。
     *
     * 写回 mcp_enabled 是必需的：否则下次 onStart 的 syncMcpService 会把服务按「已关闭」停掉。
     * 权限采用提示而非阻断：前台服务本身可启动，缺 POST_NOTIFICATIONS / ACCESS_LOCAL_NETWORK
     * 只会让通知或局域网客户端连不上。
     */
    private suspend fun startMcpFromShortcut() {
        val holding = Toast.makeText(this, "正在自启动 MCP 服务…", Toast.LENGTH_SHORT)
        holding.show()
        var port = 0
        var running = false
        var started = false
        var persisted = true
        try {
            val repo = SettingsRepository(this@MainActivity)
            port = repo.mcpPort.first()
            try {
                repo.setMcpEnabled(true)
            } catch (e: Exception) {
                persisted = false
                Log.w(TAG, "shortcut: persist mcp_enabled failed", e)
            }
            running = McpHostService.isRunning()
            if (!running) started = McpHostService.start(this@MainActivity, port)
        } catch (e: Exception) {
            Log.w(TAG, "shortcut: start MCP failed", e)
        }
        val missing = try {
            FeaturePermissions.missing(this, FeaturePermissions.forMcpService())
        } catch (_: Exception) {
            emptyArray()
        }
        holding.cancel()
        val message = when {
            !running && !started -> "MCP 服务自启动失败，请在应用内手动启用"
            missing.isNotEmpty() -> "已自启动 MCP 服务 · 端口 $port（通知/局域网权限未授予）"
            !persisted -> "已自启动 MCP 服务 · 端口 $port（状态保存失败，重启后需重开）"
            else -> "已自启动 MCP 服务 · 端口 $port"
        }
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
    }

    override fun onStart() {
        super.onStart()
        syncMcpService(mcpAllowedByLock)
    }

    private fun syncMcpService(allowedByLock: Boolean) {
        lifecycleScope.launch {
            val repo = SettingsRepository(this@MainActivity)
            if (!allowedByLock) {
                if (McpHostService.isRunning()) McpHostService.stop(this@MainActivity)
                return@launch
            }
            val enabled = repo.mcpEnabled.first()
            if (enabled && !McpHostService.isRunning()) {
                McpHostService.start(this@MainActivity, repo.mcpPort.first())
            } else if (!enabled && McpHostService.isRunning()) {
                McpHostService.stop(this@MainActivity)
            }
        }
    }
}

/** 未识别的持久化值回退到默认动画，避免旧备份写入未知字符串后转场失效。 */
private fun resolveBackTransition(mode: String): BackTransition =
    BackTransition.entries.firstOrNull { it.name == mode } ?: BackTransition.SCALE_FADE

/**
 * 返回转场的动画规格必须保持线性。
 *
 * Navigation Compose 在预测性返回手势期间会按系统 progress 直接 seek 这组
 * popEnter / popExit 动画，spec 的 easing 同样作用于 seek：旧的强 ease-in
 * (0.2,0,0,1) 让前 80% 的手势几乎没有位移，是「横向跟随不生效」的直接原因。
 * 线性规格保证位移与手指一一对应，松手后的 200ms 收尾也干脆；取消回弹由
 * NavHost 按进度反向驱动。
 */
private fun backTween(): androidx.compose.animation.core.FiniteAnimationSpec<Float> =
    tween(200, easing = LinearEasing)
private fun backTweenOffset(): androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> =
    tween(200, easing = LinearEasing)

/** 未识别的退出方向值回退到跟随手势，兼容旧备份与未知字符串。 */
private fun resolveExitDirection(direction: String): String =
    if (direction == "ALWAYS_RIGHT" || direction == "ALWAYS_LEFT") direction else "FOLLOW_GESTURE"

/**
 * 返回（含预测性返回手势预览）转场规格。
 * @param edge 最近一次返回手势的来源边缘：[NavigationEvent.EDGE_RIGHT] 时横向方向翻转，
 *             即手势从右侧返回则页面从右向左收束，从左侧返回则保持默认（左向右）。
 * @param exitDirection 横向返回的退出方向：FOLLOW_GESTURE 跟随手势边缘（默认），
 *                      ALWAYS_RIGHT / ALWAYS_LEFT 恒定向右 / 恒定向左退出，忽略手势来源。
 *                      仅 SLIDE_HORIZONTAL 使用，其余动画类型方向无意义。
 */
private fun backEnter(mode: String, edge: Int, exitDirection: String): EnterTransition = when (resolveBackTransition(mode)) {
    BackTransition.SLIDE_HORIZONTAL -> when (resolveExitDirection(exitDirection)) {
        // 恒定向左退出：上一页从右侧进入
        "ALWAYS_LEFT" -> slideInHorizontally(animationSpec = backTweenOffset()) { it / 4 } + fadeIn(backTween())
        // 恒定向右退出：上一页从左侧进入
        "ALWAYS_RIGHT" -> slideInHorizontally(animationSpec = backTweenOffset()) { -it / 4 } + fadeIn(backTween())
        // 跟随手势：右边缘手势上一页从右侧进入；左边缘从左侧进入
        else ->
            if (edge == NavigationEvent.EDGE_RIGHT)
                slideInHorizontally(animationSpec = backTweenOffset()) { it / 4 } + fadeIn(backTween())
            else
                slideInHorizontally(animationSpec = backTweenOffset()) { -it / 4 } + fadeIn(backTween())
    }
    BackTransition.SLIDE_VERTICAL -> slideInVertically(animationSpec = backTweenOffset()) { -it / 4 } + fadeIn(backTween())
    BackTransition.FADE -> fadeIn(backTween())
    BackTransition.NONE -> EnterTransition.None
    else -> scaleIn(animationSpec = backTween(), initialScale = 0.92f) + fadeIn(backTween())
}

/**
 * 退出动画是「跟手」的主体：当前页按整屏宽位移，seek 到 50% 就移出半屏，
 * 与手指位置一致。旧实现只有 1/4 屏宽，拖动全程几乎看不出横向跟随。
 * 1/4 的视差只保留在 [backEnter]（上一页从侧边轻微移入），既露出上一页又不喧宾夺主。
 */
private fun backExit(mode: String, edge: Int, exitDirection: String): ExitTransition = when (resolveBackTransition(mode)) {
    BackTransition.SLIDE_HORIZONTAL -> when (resolveExitDirection(exitDirection)) {
        // 恒定向左退出：当前页向左滑出
        "ALWAYS_LEFT" -> slideOutHorizontally(animationSpec = backTweenOffset()) { -it } + fadeOut(backTween())
        // 恒定向右退出：当前页向右滑出
        "ALWAYS_RIGHT" -> slideOutHorizontally(animationSpec = backTweenOffset()) { it } + fadeOut(backTween())
        // 跟随手势：右边缘手势当前页向左滑出；左边缘向右滑出
        else ->
            if (edge == NavigationEvent.EDGE_RIGHT)
                slideOutHorizontally(animationSpec = backTweenOffset()) { -it } + fadeOut(backTween())
            else
                slideOutHorizontally(animationSpec = backTweenOffset()) { it } + fadeOut(backTween())
    }
    BackTransition.SLIDE_VERTICAL -> slideOutVertically(animationSpec = backTweenOffset()) { it } + fadeOut(backTween())
    BackTransition.FADE -> fadeOut(backTween())
    BackTransition.NONE -> ExitTransition.None
    else -> scaleOut(animationSpec = backTween(), targetScale = 0.88f) + fadeOut(backTween())
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
    settingsRepository: SettingsRepository,
    backSwipeEdge: MutableIntState,
    shortcutEventToken: String? = null,
    shortcutEventKind: String? = null
) {
    // Miuix 的 OverlayDialog 内部使用 NavigationBackHandler，
    // 需要 LocalNavigationEventDispatcherOwner（navigationevent-compose 1.1.2）。
    // NavHost 自身不提供该值，此处创建根 dispatcher（parent = null）。
    val dispatcherOwner = rememberNavigationEventDispatcherOwner(
        enabled = true,
        parent = null
    )
    val dispatcher = dispatcherOwner.navigationEventDispatcher

    // 「入口加号 FAB」的屏幕锚点由 MainScreen 发布、AddEditRecordPage 消费。
    // 必须在 NavHost 之上持有：导航离开首页后 MainScreen 会被移出组合，
    // 但锚点值需要保留到增改页完成布局。
    val fabAnchor = remember { FabAnchorState() }

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

    // 弹窗返回（OverlayDialog → NavigationBackHandler）走的是 App 自建的 dispatcher，
    // 与页面返回不是同一条链路。旧实现在这里把弹窗手势的 swipeEdge 写回
    // backSwipeEdge，会让「跟随手势」在关掉弹窗后被上一次弹窗的边缘污染，
    // 出现同一侧返回却忽左忽右。页面方向统一由 Activity 层采集，此处不再改写。

    // 返回动画可在「外观设置 → 页面转场」中切换；预测性返回手势预览复用同一组规格
    val backTransition by settingsRepository.predictiveBackTransition
        .collectAsState(initial = BackTransition.SCALE_FADE.name)
    // 预测性返回总开关：关闭后返回瞬时无动画
    val predictiveBackEnabled by settingsRepository.predictiveBackEnabled
        .collectAsState(initial = true)
    // 横向返回的退出方向（跟随手势 / 恒定向右 / 恒定向左）
    val backExitDirection by settingsRepository.backExitDirection
        .collectAsState(initial = "FOLLOW_GESTURE")
    val edge = backSwipeEdge.intValue
    // 直接交由 NavHost 的「原生预测性返回」驱动 popEnter / popExit：
    // 手势进行中 NavHost 会同时组合上一页（popEnter）与当前页（popExit），
    // 并把转场进度 seek 到系统 progress——既跟手又能露出上一页内容。
    // 转场对象必须稳定：seek 期间若因重组重建 EnterTransition / ExitTransition，
    // AnimatedContent 会重新配置动画，跟手位移会被打断。用 remember 把规格只与
    // 「设置项 + 本次手势边缘」绑定；手势开始后边缘已冻结，这些 key 不再变化。
    val popEnter: EnterTransition = remember(
        predictiveBackEnabled, backTransition, backExitDirection, edge
    ) {
        if (predictiveBackEnabled) backEnter(backTransition, edge, backExitDirection)
        else EnterTransition.None
    }
    val popExit: ExitTransition = remember(
        predictiveBackEnabled, backTransition, backExitDirection, edge
    ) {
        if (predictiveBackEnabled) backExit(backTransition, edge, backExitDirection)
        else ExitTransition.None
    }

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides dispatcherOwner,
        LocalFabAnchor provides fabAnchor,
        // 兜底默认值：各目的地都会用 PageSnackbarScope 覆盖为页面级 state
        LocalSnackbarHostState provides remember { SnackbarHostState() }
    ) {
        // 转场兜底底色：预测性返回时 popExit 会淡出/缩放当前页，若两页内容尚未铺满，
        // 这层不透明主题底色可挡住 window 之后的下层深色，避免浅色模式闪深色。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
        ) {
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.fillMaxSize(),
            popEnterTransition = { popEnter },
            popExitTransition = { popExit }
        ) {
        composable("main") {
            PageSnackbarScope {
                MainScreen(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    shortcutEventToken = shortcutEventToken,
                    shortcutEventKind = shortcutEventKind
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
        composable(route = "statistics?month={month}") { backStackEntry ->
            PageSnackbarScope {
                StatisticsDetailScreen(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    initialMonth = backStackEntry.arguments?.getString("month"),
                    onBack = { navController.popBackStack() }
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
        composable("security_settings") {
            PageSnackbarScope {
                SecuritySettingsPage(
                    navController = navController,
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
        composable("home_widgets") {
            PageSnackbarScope {
                HomeWidgetsSettingsPage(
                    navController = navController,
                    settingsRepository = settingsRepository
                )
            }
        }
        composable("add_widget") {
            PageSnackbarScope {
                AddWidgetPage(
                    navController = navController,
                    repository = repository,
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
