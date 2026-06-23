package com.overtime.miuix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.rememberThemeController
import top.yukonga.miuix.kmp.utils.ColorSchemeMode

@Composable
fun OvertimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    keyColor: Color = Color(0xFF3482FF),
    content: @Composable () -> Unit
) {
    val controller = rememberThemeController(
        if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
        keyColor = keyColor
    )
    
    MiuixTheme(
        controller = controller,
        content = content
    )
}
