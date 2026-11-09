package com.mars.overtime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.mars.overtime.util.AppDefaults
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun OvertimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    keyColor: Color = Color(AppDefaults.PRIMARY_ARGB),
    monet: Boolean = false,
    content: @Composable () -> Unit
) {
    // Miuix 浅色默认把 background 与 surfaceContainer 都设成纯白 (#FFFFFFFF)，
    // 于是首页/记录/统计页的 Card 与白色大背景完全同色、看不出独立卡片。
    // 用户要求卡片保持纯白，因此改为把「页面大背景」压到浅灰蓝：
    // 白色卡片浮在浅灰底上，边界清晰；深色配色沿用框架默认（本身已有层级差）。
    // window 背景与转场兜底底色都取 colorScheme.background，会自动跟随。
    val lightColors = remember {
        lightColorScheme(
            background = Color(AppDefaults.BACKGROUND_ARGB)
        )
    }
    val controller = remember(darkTheme, keyColor, monet) {
        ThemeController(
            colorSchemeMode = when {
                monet && darkTheme -> ColorSchemeMode.MonetDark
                monet && !darkTheme -> ColorSchemeMode.MonetLight
                darkTheme -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.Light
            },
            // 浅色非 Monet 模式使用上面的定制配色；深色/Monet 由框架按模式给出
            lightColors = lightColors,
            // Monet 模式使用系统壁纸动态配色，keyColor 传 null 由框架回退到平台动态色
            keyColor = if (monet) null else keyColor
        )
    }

    MiuixTheme(
        controller = controller,
        content = content
    )
}
