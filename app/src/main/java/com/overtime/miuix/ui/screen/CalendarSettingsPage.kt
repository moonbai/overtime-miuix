package com.overtime.miuix.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.push.CalendarSyncManager
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            status = "日历权限已授予"
        } else {
            status = "未获得日历权限，无法同步"
            scope.launch { snackbarHostState.showCustomToast("未获得日历权限") }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "日历同步",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (status != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(status ?: "", color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.footnote1)
                    }
                }
            }

            item {
                Column {
                    SmallTitle(text = "同步设置")
                    BasicComponent(
                        title = "启用日历同步",
                        summary = "保存记录时自动同步到系统日历",
                        endActions = {
                            Switch(
                                checked = calendarSyncEnabled,
                                onCheckedChange = {
                                    scope.launch { settingsRepository.setCalendarSyncEnabled(it) }
                                }
                            )
                        }
                    )
                }
            }

            item {
                Column {
                    SmallTitle(text = "日历账户")
                    BasicComponent(
                        title = "授权日历权限",
                        summary = if (CalendarSyncManager.hasCalendarPermission(context)) "已授权" else "点击授予日历读写权限",
                        startAction = { Icon(MiuixIcons.Months, contentDescription = null) },
                        onClick = {
                            if (!CalendarSyncManager.hasCalendarPermission(context)) {
                                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                            } else {
                                status = "日历权限已授予"
                            }
                        }
                    )
                    BasicComponent(
                        title = "立即同步全部记录",
                        summary = "将现有 ${records.size} 条记录写入系统日历",
                        onClick = {
                            scope.launch {
                                if (!CalendarSyncManager.hasCalendarPermission(context)) {
                                    permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                    return@launch
                                }
                                val ok = CalendarSyncManager.syncAll(context, records)
                                status = if (ok) "同步完成" else "同步失败"
                                snackbarHostState.showCustomToast(if (ok) "同步完成" else "同步失败")
                            }
                        }
                    )
                }
            }
        }
    }
}
