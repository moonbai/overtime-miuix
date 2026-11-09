package com.mars.overtime.util

/**
 * 全局基线配色的单一来源。
 *
 * 主题默认强调色、窗口底色、三类加班的默认类型色原本以 ARGB 字面量散落在
 * Theme / MainActivity / SettingsRepository 与各页 collectAsState 的 initial 里，
 * 改一处漏一处就会出现「首帧一种色、加载后另一种色」。数据层与 UI 层都依赖 util，
 * 因此常量放在这里而不是放进任一侧的包。
 */
object AppDefaults {
    val PRIMARY_ARGB: Int = 0xFF3482FF.toInt()
    val BACKGROUND_ARGB: Int = 0xFFF1F3F7.toInt()
    val WEEKEND_ARGB: Int = 0xFF34C759.toInt()
    val HOLIDAY_ARGB: Int = 0xFFFF7043.toInt()
}
