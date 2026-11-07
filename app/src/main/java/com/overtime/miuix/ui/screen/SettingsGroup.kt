package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置模块唯一的分组容器：小标题 + 卡片包裹的选项列表。
 * 所有设置首页与二级页都通过它组织内容，圆角、内边距、分组间距由 [SettingsDimens] 统一定义。
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
            cornerRadius = SettingsDimens.CardCorner
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
