package com.mars.overtime.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.push.CalendarSyncManager
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun CalendarSettingsPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val calendarSyncEnabled by settingsRepository.calendarSyncEnabled.collectAsState(initial = false)
    val records by repository.getAllRecords().collectAsState(initial = emptyList())

    var status by remember { mutableStateOf<String?>(null) }

    // 权限状态需作为 Compose 状态持有：用户可能在系统设置中撤销权限后返回，
    // 直接调用 hasCalendarPermission() 不会触发重组，界面会一直停留在「已授权」。
    var hasPermission by remember { mutableStateOf(CalendarSyncManager.hasCalendarPermission(context)) }

    // 页面每次回到前台时重新校验权限，保证开关与提示反映真实授权状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = CalendarSyncManager.hasCalendarPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // 必须 READ + WRITE 同时授予，缺少 WRITE_CALENDAR 时写入仍会抛 SecurityException
        val granted = CalendarSyncManager.calendarPermissions().all { result[it] == true }
        hasPermission = granted
        if (granted) {
            status = "日历权限已授予"
        } else {
            status = "未获得日历权限，无法同步"
            scope.launch { snackbarHostState.showCustomToast("未获得日历权限") }
        }
    }

    SettingsScreen(
        title = "日历同步",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    ) {
        status?.let { message ->
            item { SettingsStatusText(text = message) }
        }

        item {
            SettingsGroup(title = "同步与权限") {
                SettingsSwitchItem(
                    title = "启用日历同步",
                    summary = if (calendarSyncEnabled && !hasPermission) {
                        "已开启，但缺少日历权限，请先授权"
                    } else {
                        "保存记录时自动写入系统日历"
                    },
                    checked = calendarSyncEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setCalendarSyncEnabled(enabled) }
                        // 开启同步时立即申请权限，避免提交记录时才遭遇 SecurityException
                        if (enabled && !CalendarSyncManager.hasCalendarPermission(context)) {
                            permissionLauncher.launch(CalendarSyncManager.calendarPermissions())
                        }
                    }
                )
                SettingsActionItem(
                    title = "日历权限",
                    summary = if (hasPermission) "已授权读写权限" else "点击授予日历读写权限",
                    icon = MiuixIcons.Months,
                    onClick = {
                        if (!CalendarSyncManager.hasCalendarPermission(context)) {
                            permissionLauncher.launch(CalendarSyncManager.calendarPermissions())
                        } else {
                            hasPermission = true
                            status = "日历权限已授予"
                        }
                    }
                )
                SettingsActionItem(
                    title = "立即同步全部记录",
                    summary = "将现有 ${records.size} 条记录写入系统日历",
                    icon = MiuixIcons.Update,
                    onClick = {
                        scope.launch {
                            if (!CalendarSyncManager.hasCalendarPermission(context)) {
                                hasPermission = false
                                permissionLauncher.launch(CalendarSyncManager.calendarPermissions())
                                return@launch
                            }
                            // 批量同步会先清空日历再逐条写 Provider，必须切到 IO 线程
                            val msg = when (val result = withContext(Dispatchers.IO) {
                                CalendarSyncManager.syncAll(context, records)
                            }) {
                                is CalendarSyncManager.SyncResult.Success -> "同步完成"
                                is CalendarSyncManager.SyncResult.PermissionDenied -> {
                                    hasPermission = false
                                    "缺少日历权限，请重新授权后重试"
                                }
                                is CalendarSyncManager.SyncResult.ProviderRejected ->
                                    "系统拒绝写入日历，请在系统设置中检查本应用的日历权限"
                                is CalendarSyncManager.SyncResult.Failure ->
                                    "同步失败：${result.message}"
                            }
                            status = msg
                            snackbarHostState.showCustomToast(msg)
                        }
                    }
                )
            }
        }
    }
}
