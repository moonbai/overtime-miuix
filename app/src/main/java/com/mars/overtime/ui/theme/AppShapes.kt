package com.mars.overtime.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 全局圆角令牌（设计令牌层）。
 *
 * 全 App 的圆角统一从这里取，禁止再手写 `RoundedCornerShape(x.dp)` 裸值。
 * 取值对齐各页既有半径：统计页/快速提报/薪资页的卡片与浮层 `12.dp`、设置卡 `16.dp`、
 * 悬浮底栏/扩展 FAB `28.dp`、液态指示器 `20.dp`、关于页头像/外观页圆形 `CircleShape` 等，
 * 替换后视觉零变化。
 */
object AppShapes {
    val radiusXs = RoundedCornerShape(4.dp)    // 图表细条、微小元素
    val radiusSm = RoundedCornerShape(8.dp)    // 紧凑容器
    val radiusMd = RoundedCornerShape(12.dp)   // 浮层、次级卡片、可点区域
    val radiusLg = RoundedCornerShape(16.dp)   // 主卡片（对齐 SettingsDimens.CardCorner）
    val radiusXl = RoundedCornerShape(20.dp)   // 液态选中指示器
    val radiusXxl = RoundedCornerShape(24.dp)  // 大头像/区块
    val radiusZero = RoundedCornerShape(0.dp)  // 普通底栏满宽毛玻璃

    val fab = RoundedCornerShape(28.dp)            // 标准 / 扩展型 FAB
    val navBar = RoundedCornerShape(28.dp)         // 悬浮胶囊底栏
    val indicator = RoundedCornerShape(20.dp)       // 液态选中指示器
    val pill = CircleShape                          // 圆形头像、FAB、图标按钮
}
