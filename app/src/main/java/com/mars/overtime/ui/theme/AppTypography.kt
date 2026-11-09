package com.mars.overtime.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 排版统一规则（设计令牌层）。
 *
 * 1. 所有文字使用 `MiuixTheme.textStyles.*` 命名样式，禁止在各页手写 `fontSize` 基线；
 * 2. 命名样式自带字重，不要在 `Text` 上再叠 `fontWeight`（重复声明、易漂移）。
 *    若某处确需改变字重，统一用下方「带字重的语义别名」（如 [AppTitle3Semibold]），
 *    不要再把 `style = X; fontWeight = Y` 散落在各页。
 *
 * 下方的带字重别名渲染结果与原 `style = X; fontWeight = Y` 完全一致，仅把字重决策集中到一处，
 * 后续若要统一调整字重，只改这里即可。
 */
object AppTypography {
    /** 既定语义角色（仅供代码审查参考，不强制） */
    val roles = listOf("headline1/2", "title3", "body1/2", "footnote1")
}

@Composable
fun AppTitle3Semibold(): TextStyle =
    MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.SemiBold)

@Composable
fun AppTitle3Bold(): TextStyle =
    MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold)

@Composable
fun AppBody1Medium(): TextStyle =
    MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium)

@Composable
fun AppBody1Semibold(): TextStyle =
    MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)

@Composable
fun AppBody2Medium(): TextStyle =
    MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium)

@Composable
fun AppBody2Semibold(): TextStyle =
    MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)

@Composable
fun AppFootnote1Bold(): TextStyle =
    MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold)

@Composable
fun AppHeadline1Bold(): TextStyle =
    MiuixTheme.textStyles.headline1.copy(fontWeight = FontWeight.Bold)

@Composable
fun AppHeadline2Bold(): TextStyle =
    MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.Bold)
