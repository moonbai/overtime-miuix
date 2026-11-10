package com.mars.overtime.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 启动锁的进程内状态。
 *
 * - 冷启动初始为「锁定」；用户输对 PIN 或生物识别通过后 [unlock]。
 * - 应用整体退到后台超过 [BACKGROUND_LOCK_TIMEOUT_MS] 再回前台时重新上锁；
 *   给 15s 宽限是为了不让系统生物识别弹窗、SAF 文件选择器等短暂停留把刚解锁的会话锁死。
 * - 上锁**不清空** [BackupSession]：备份口令与启动 PIN 分离，锁屏期间后台自动备份
 *   仍应能用已验证的会话密钥完成加密，不应静默退化。
 */
object AppLockManager {
    private const val BACKGROUND_LOCK_TIMEOUT_MS = 15_000L

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    /** 由 [com.mars.overtime.ui.screen.AppLockGate] 按持久化开关同步，供生命周期回调读取。 */
    @Volatile
    var lockEnabled: Boolean = false
        private set

    @Volatile
    private var startedActivities = 0

    @Volatile
    private var backgroundAt = 0L

    @Volatile
    private var installed = false

    fun setLockEnabled(enabled: Boolean) {
        lockEnabled = enabled
        if (!enabled) unlock()
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                val since = backgroundAt
                if (since > 0L && lockEnabled &&
                    SystemClock.elapsedRealtime() - since >= BACKGROUND_LOCK_TIMEOUT_MS
                ) {
                    lock()
                }
                backgroundAt = 0L
            }

            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) backgroundAt = SystemClock.elapsedRealtime()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
