package com.overtime.miuix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.theme.AppDimens
import com.overtime.miuix.ui.theme.AppIconSize
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置模块统一视觉规范。
 *
 * 所有设置页（含设置首页）都遵循同一套规则：
 * - 页面骨架：[SettingsScreen]，统一返回栏、Snackbar、列表内边距与分组间距；
 * - 内容容器：[SettingsGroup]（小标题 + 卡片），卡片内使用 [SettingsActionItem] /
 *   [SettingsSwitchItem] / [SettingsTextField] / [SettingsNote] 等标准件；
 * - 需要校验的一组数值（薪资倍率、MCP 端口）才使用显式「保存」按钮，
 *   其余开关、下拉、单字段一律即改即存，避免同一套设置里出现三种保存语义。
 */
/**
 * 设置模块间距令牌。
 *
 * 已统一收敛到全局 [AppDimens]（ui/theme 包）：下方全部委托过去，避免两处各自维护同一组值。
 * 设置页代码仍可继续引用 [SettingsDimens.xxx]，无需逐处改动。
 */
object SettingsDimens {
    /** LazyColumn 中各分组卡片的间距 */
    val GroupSpacing get() = AppDimens.groupSpacing

    /**
     * 页面左右留白：与 HomePage / StatisticsPage 的 LazyColumn 统一为 16dp。
     * 设置页此前没有水平 contentPadding，卡片是贴着屏幕边缘的，
     * 与首页、统计页并排切换时视觉上明显不齐。
     */
    val PageHorizontal get() = AppDimens.pageHorizontal

    /** 页面上下留白，与首页 / 统计页一致 */
    val PageVertical get() = AppDimens.pageVertical

    /** 卡片圆角，与 [SettingsGroup] 保持一致 */
    val CardCorner get() = AppDimens.spaceMd

    /** 卡片内输入框、说明文字的水平内边距 */
    val ItemHorizontal get() = AppDimens.itemHorizontal

    /** 卡片内输入框的纵向内边距 */
    val ItemVertical get() = AppDimens.itemVertical
}

/**
 * 设置页统一列表：固定的上下留白与分组间距。
 * 设置首页（无独立顶栏）与二级页（经 [SettingsScreen]）共用，保证视觉节奏一致。
 */
@Composable
fun SettingsList(
    modifier: Modifier = Modifier,
    bottomReserve: Dp = AppDimens.zero,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = AppDimens.pageHorizontal,
            top = AppDimens.pageVertical,
            end = AppDimens.pageHorizontal,
            bottom = AppDimens.pageVertical + bottomReserve
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.groupSpacing),
        content = content
    )
}

/**
 * 二级设置页统一骨架：返回栏 + Snackbar + 标准间距的 LazyColumn。
 *
 * @param title 顶栏标题，需与设置首页的入口名称一致
 * @param onBack 返回行为；有未保存修改的页面可在其中插入二次确认
 * @param bottomReserve 列表底部额外留白（设置首页在悬浮底栏模式下需要）
 * @param dialogs 浮层对话框，放在列表之后叠加显示
 */
@Composable
fun SettingsScreen(
    title: String,
    onBack: () -> Unit,
    bottomReserve: Dp = AppDimens.zero,
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
    dialogs: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        SettingsList(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            bottomReserve = bottomReserve,
            content = content
        )
        dialogs()
    }
}

/** 卡片内的跳转 / 操作行，图标可选 */
@Composable
fun SettingsActionItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    // 用局部 val 承接，避免在 composable lambda 里对可空参数做 smart cast
    val vector = icon
    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            if (vector != null) {
                Icon(vector, contentDescription = null)
            }
        },
        onClick = onClick
    )
}

/** 卡片内的开关行：所有设置页的开关外观与行为一致，整行可点击切换 */
@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    BasicComponent(
        title = title,
        summary = summary,
        // Switch 自身有 toggleable，会消费落在开关上的点击；
        // 这里再给整行 clickable，点击标题/摘要区域同样能切换。
        onClick = { onCheckedChange(!checked) },
        endActions = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

/** 卡片内的输入框，统一左右 16dp 内边距，避免各页手写 padding 不一致 */
@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 密码框：默认以圆点掩码显示，点击尾部的眼睛图标可临时查看明文
    val passwordVisible = remember { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimens.itemHorizontal,
                vertical = AppDimens.itemVertical
            ),
        visualTransformation =
            if (isPassword && !passwordVisible.value) PasswordVisualTransformation()
            else VisualTransformation.None,
        keyboardOptions = if (isPassword)
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password
            )
        else androidx.compose.foundation.text.KeyboardOptions.Default,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible.value = !passwordVisible.value }) {
                    Icon(
                        imageVector = if (passwordVisible.value) MiuixIcons.Hide else MiuixIcons.Show,
                        contentDescription = if (passwordVisible.value) "隐藏密码" else "显示密码",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(AppIconSize.sm)
                    )
                }
            }
        } else null
    )
}

/** 卡片内的辅助说明；校验失败时以错误色显示 */
@Composable
fun SettingsNote(
    text: String,
    isError: Boolean = false
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = if (isError) {
            MiuixTheme.colorScheme.error
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        modifier = Modifier.padding(
            horizontal = AppDimens.itemHorizontal,
            vertical = AppDimens.itemVertical
        )
    )
}

/**
 * 页面级状态提示，统一替代各页手写的 status 文本块。
 * @param insideCard 放在 [SettingsGroup] 卡片内时传 true（补卡片内边距）；
 *                   作为独立 item 放在列表里时传 false，由列表的页面留白负责，
 *                   避免出现「比卡片多缩进 16dp」的错位。
 */
@Composable
fun SettingsStatusText(text: String, insideCard: Boolean = false) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(
            horizontal = if (insideCard) AppDimens.itemHorizontal else AppDimens.zero
        )
    )
}

/** 卡片内的多行预览文本（推送消息预览、配置片段等） */
@Composable
fun SettingsPreviewText(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(
            horizontal = AppDimens.itemHorizontal,
            vertical = AppDimens.spaceXs
        )
    )
}

/** 卡片外的单个主操作按钮（保存 / 测试推送等） */
@Composable
fun SettingsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 列表的 contentPadding 已经统一留白，这里不再叠加一层 16dp，
    // 否则按钮会比同一页的卡片窄 32dp，左右边缘对不齐。
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}

/** 卡片外的并排双按钮，避免各页自行拼 Row + weight */
@Composable
fun SettingsButtonRow(
    firstText: String,
    onFirstClick: () -> Unit,
    secondText: String,
    onSecondClick: () -> Unit,
    firstEnabled: Boolean = true,
    secondEnabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimens.itemHorizontal,
                vertical = AppDimens.itemVertical
            ),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.buttonRowSpacing)
    ) {
        Button(
            onClick = onFirstClick,
            enabled = firstEnabled,
            modifier = Modifier.weight(1f)
        ) { Text(firstText) }
        Button(
            onClick = onSecondClick,
            enabled = secondEnabled,
            modifier = Modifier.weight(1f)
        ) { Text(secondText) }
    }
}
