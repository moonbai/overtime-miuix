package com.overtime.miuix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun OvertimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    keyColor: Color = Color(0xFF3482FF),
    monet: Boolean = false,
    content: @Composable () -> Unit
) {
    val controller = remember(darkTheme, keyColor, monet) {
        ThemeController(
            colorSchemeMode = when {
                monet -> ColorSchemeMode.MonetSystem
                darkTheme -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.Light
            },
            // Monet 模式使用系统壁纸动态配色，keyColor 传 null 由框架回退到平台动态色
            keyColor = if (monet) null else keyColor
        )
    }

    MiuixTheme(
        controller = controller,
        content = content
    )
}
