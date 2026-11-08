package com.mars.overtime.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局图标尺寸令牌（设计令牌层）。
 *
 * 全 App 图标统一从这里取尺寸，禁止再手写 `Modifier.size(xx.dp)` 裸值。
 * 取值对齐各页既有图标尺寸：设置行尾部图标 `20.dp`、FAB 主图标 `24.dp`、
 * 悬浮底栏图标 `26.dp`。
 */
object AppIconSize {
    val xs = 18.dp  // 紧凑按钮图标（首页添加按钮）
    val sm = 20.dp  // 设置行尾部图标、紧凑按钮图标
    val md = 24.dp  // FAB 主图标、标准图标按钮
    val lg = 26.dp  // 底栏导航图标
}
