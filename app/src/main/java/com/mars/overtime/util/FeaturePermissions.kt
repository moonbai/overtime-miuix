package com.mars.overtime.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 运行时权限的「按需分配表」。
 *
 * 启动流程（MainActivity.onCreate）不申请任何权限：权限只在用户主动启用对应功能的
 * 那一刻，由功能页面取 [missing] 的结果发起请求。权限被拒绝不回滚开关，功能仍按用户
 * 意图启用，仅给出影响说明，避免用户遇到「点了开关却没反应」。
 */
object FeaturePermissions {

    /**
     * MCP 前台服务：常驻通知需要 POST_NOTIFICATIONS；
     * Android 17（API 37，本项目 targetSdk 37）起局域网访问还需 ACCESS_LOCAL_NETWORK，
     * 未授权时系统 BPF 层静默丢包（表现为客户端连接超时）。
     */
    fun forMcpService(): Array<String> = buildList {
        add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 37) {
            add(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }.toTypedArray()

    /** WebDAV / 局域网备份：仅在 API 37+ 需要 ACCESS_LOCAL_NETWORK。 */
    fun forLocalNetwork(): Array<String> =
        if (Build.VERSION.SDK_INT >= 37) {
            arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            emptyArray<String>()
        }

    /** 只返回尚未授予的权限；已授予或当前系统不需要该权限时为空，调用方据此跳过请求。 */
    fun missing(context: Context, required: Array<String>): Array<String> =
        required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
}
