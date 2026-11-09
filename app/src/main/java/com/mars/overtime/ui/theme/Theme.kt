package com.mars.overtime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun OvertimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    keyColor: Color = Color(0xFF3482FF),
    monet: Boolean = false,
    content: @Composable () -> Unit
) {
    // Miuix 浅色默认把 background 与 surfaceContainer 都设成纯白 (#FFFFFFFF)，
    // 于是首页/记录/统计页的 Card 与白色大背景完全同色、看不出独立卡片。
    // 这里只在浅色配色里把 surfaceContainer 系列压低一档到浅灰蓝：大背景保持纯白，
    // 卡片即可稳定辨识；深色配色本身已有层级差，沿用框架默认不动。
    val lightColors = remember {
        lightColorScheme(
            surfaceContainer = Color(0xFFF0F3F8),
            surfaceContainerHigh = Color(0xFFE7ECF4),
            surfaceContainerHighest = Color(0xFFDDE5F0)
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
