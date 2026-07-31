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
    content: @Composable () -> Unit
) {
    val controller = remember(darkTheme, keyColor) {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            keyColor = keyColor
        )
    }

    MiuixTheme(
        controller = controller,
        content = content
    )
}
