package com.mars.overtime.util

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/**
 * 毛玻璃统一开关。
 *
 * 主界面最多同时存在顶栏 / 底栏 / 悬浮底栏三层 textureBlur，二级页再各有一层，
 * 中低端设备上这会带来持续的 GPU 采样与合成开销。这里把「是否启用」收敛到一个入口：
 * 不支持运行时 shader，或被系统标记为低内存设备时，一律回退到不透明 surface，
 * 标题可读性由各页原有的 color 分支保证。
 */
@Composable
fun rememberBlurEnabled(): Boolean {
    val shaderSupported = isRuntimeShaderSupported()
    val context = LocalContext.current
    return remember(shaderSupported) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        shaderSupported && activityManager?.isLowRamDevice != true
    }
}
