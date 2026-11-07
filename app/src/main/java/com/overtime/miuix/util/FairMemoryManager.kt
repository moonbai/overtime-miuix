package com.overtime.miuix.util

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Debug
import android.util.Log
import com.overtime.miuix.OvertimeApplication
import com.overtime.miuix.mcp.McpHostService
import com.overtime.miuix.push.PushManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 公平运行内存机制适配——内存预警释放侧。
 *
 * 依据小米/OPPO/vivo《公平运行内存适配》开发者文档（2026-04）规范：
 *  - 内存预警时及时释放：清业务 LRU 缓存、释放大对象与不可见页面资源、
 *    回收 Bitmap/网络连接等空闲资源，避免触达查杀条件；
 *  - PSS/堆水位尽量控制在设备档位上限内（规范以 PSS 为统一统计口径）。
 *
 * 说明：厂商私有 TRIM/KILL 广播（itgsa.intent.action.TRIM / KILL）的
 * Binder 回调事务码（TRANSACTION_EXCEPTION_REPLY）在三家公开文档中均未给出
 * 数值，协议未公开前不注册半成品广播接收器；本类通过标准通道
 * [ComponentCallbacks2]（全厂商有效）完成同一目标，待协议码公开后可在
 * [onFairMemoryNotice] 预留点接入广播与 3 秒回执。
 */
object FairMemoryManager {
    private const val TAG = "FairMemory"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 系统内存压力回调（Application.onTrimMemory 转发）。要求快速返回，不做 IO 重活。 */
    fun onTrimMemory(app: Application, level: Int) {
        // 规范建议 PSS 低频、后台线程采集；此处仅预警时单次记录，便于配合开发者选项复测
        Log.d(TAG, "onTrimMemory level=$level pss=${runCatching { Debug.getPss() }.getOrNull()}MB")

        val full = when (level) {
            // 退后台 / UI 不可见：轻清理（保住用户回前台的恢复速度）
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> false
            // 前台已吃紧
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> true
            // 前台轻度压力：轻清理
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> false
            // 后台各档（40/60/80）：全量释放
            else -> true
        }
        releaseCaches(full)

        // 内存告急档停掉 MCP 本地服务（用户可随时在设置页重开），避免常驻服务顶到查杀线
        if (full) stopMcpForMemory(app)
    }

    /**
     * 释放网络侧空闲资源：OkHttp 连接池（三个业务 client 各自驱逐）。
     * 安全性：evictAll 只关空闲连接，后续请求自动重建，无功能影响。
     */
    private fun releaseCaches(full: Boolean) {
        HolidayManager.evictConnections()
        PushManager.evictConnections()
        WebDavManager.evictConnections()
        // holidayCache 自身为有界 LRU（上限见 HolidayManager），full 档直接清空
        HolidayManager.trimCache(keepCurrentYearOnly = !full)
    }

    private fun stopMcpForMemory(app: Application) {
        runCatching {
            McpHostService.stop(app)
            val settings = (app as? OvertimeApplication)?.settingsRepository ?: return
            appScope.launch { runCatching { settings.setMcpEnabled(false) } }
        }.onFailure { Log.w(TAG, "stop MCP on memory pressure failed: ${it.message}") }
    }

    /**
     * 预留接入点：厂商公平内存广播（itgsa.intent.action.TRIM / KILL）。
     * 待 TRANSACTION_EXCEPTION_REPLY 协议码公开后，在此实现 3 秒内
     * 释放/现场备份并通过 IBinder 回执系统（notifyType=1000 物理内存 / 2000 堆内存）。
     */
    fun onFairMemoryNotice(level: Int, pssLimitKb: Int = 0, heapLimitKb: Int = 0) {
        Log.d(TAG, "fair-memory notice(level=$level, pssLimit=$pssLimitKb, heapLimit=$heapLimitKb) pending protocol")
    }
}
