package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一的二级设置分组容器：标题 + 卡片包裹的选项列表。
 * 遵循 Miuix 设计系统的标准分组样式，使各设置页选项风格保持一致。
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            SmallTitle(text = title)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
