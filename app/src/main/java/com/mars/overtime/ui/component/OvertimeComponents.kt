package com.mars.overtime.ui.component

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.mars.overtime.ui.theme.AppBody1Medium
import com.mars.overtime.ui.theme.AppTitle3Semibold
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes

/**
 * App 级复用组件层（基于 MiuiX 原语封装，尺寸/圆角/图标全部走令牌层）。
 *
 * 这是「全局 UI 统一」的落地载体：非设置页（首页 / 统计 / 增改 / 快速提报 / 关于 / 外观）
 * 不再直接拼裸 Card / Row / 图标，而是通过这里的标准件表达，保证视觉与交互一致。
 * 注意：本文件不重写任何 MiuiX 组件，只做组合与令牌收口。
 */

/**
 * 「入口加号 FAB」在屏幕上的锚点（距屏幕底部的距离）。
 *
 * 用途：点击首页右下角加号进入「添加记录」后，确认按钮必须落在同一个屏幕坐标，
 * 手指不需要在两页之间移动。MainScreen 每次布局把「实际使用的 FAB 底部偏移」
 * 发布进来，AddEditRecordPage 读取它摆放确认按钮——两页共用同一个值，而不是
 * 各自按经验写死一个 dp（普通底栏/悬浮底栏/导航栏 inset 下都会算错）。
 *
 * 一致性判定标准：同一 BottomEnd 锚点 + 同一 end 边距（[AppDimens.pageHorizontal]）
 * + 同一 bottom 边距（本对象的值）+ 同一 [AppDimens.fabSize] 圆形尺寸。
 *
 * 状态放在 NavHost 上层，MainScreen 因导航被移出组合后值仍保留，
 * 直到下一次首页布局刷新。
 */
@Stable
class FabAnchorState(
    initialBottom: Dp = AppDimens.navBarHeight + AppDimens.pageVertical
) {
    var bottom: Dp by mutableStateOf(initialBottom)
}

/** 兜底值仅在「未经过首页直接进入增改页」时生效（冷启动默认目的地仍是 main）。 */
val LocalFabAnchor = compositionLocalOf { FabAnchorState() }

/**
 * 图标统一规范（见下方 [SettingsActionItem] / [FeatureCell]）：
 * 设置页与关于页的入口、宫格图标一律走「单色主题色 + 无彩色底盒」，
 * 与底栏导航（选中=primary、未选中=onSurfaceVariant）、FAB 同一套图标语言，
 * 不再为每个功能堆一排水彩方块，避免列表底色杂乱。
 */

/**
 * 与入口加号 FAB 同构的确认按钮：形状 / 直径 / 图标尺寸全部复用 [AppFab] 的同一组令牌，
 * 只把图标换成 Ok（容器仍为主色）。位置由调用方按 [LocalFabAnchor] 对齐。
 */
@Composable
fun AppConfirmFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        shape = AppShapes.pill,
        containerColor = MiuixTheme.colorScheme.primary,
        modifier = modifier.size(AppDimens.fabSize)
    ) {
        Icon(
            MiuixIcons.Ok,
            contentDescription = "保存",
            tint = MiuixTheme.colorScheme.onPrimary,
            modifier = Modifier.size(AppIconSize.md)
        )
    }
}

/**
 * 首页悬浮操作按钮：统一为单层实心强调色，形状/尺寸/图标全部走令牌层。
 * 快速提报模式下提供「添加 / 快速提报」两个按钮，否则为单个圆形按钮。
 *
 * 旧实现在 FAB 外套了一层 CircleShape 毛玻璃，与 MiuiX FAB 默认容器叠加成
 * 「半透明大圆 + 深色多边形」双层脏效果；现统一为单层实心，图标前景色按容器对比度取值。
 */
@Composable
fun AppFab(
    quickSubmit: Boolean,
    onAddRecord: () -> Unit,
    onQuickSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (quickSubmit) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.fabExtendedTextGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = onAddRecord,
                shape = AppShapes.pill,
                containerColor = MiuixTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(AppDimens.fabSize)
            ) {
                Icon(
                    MiuixIcons.Add,
                    contentDescription = "添加记录",
                    tint = MiuixTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(AppIconSize.md)
                )
            }
            FloatingActionButton(
                onClick = onQuickSubmit,
                shape = AppShapes.fab,
                containerColor = MiuixTheme.colorScheme.primary,
                modifier = Modifier.height(AppDimens.fabSize)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = AppDimens.fabExtendedHorizontal)
                ) {
                    Icon(
                        MiuixIcons.Album,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(AppIconSize.sm)
                    )
                    Spacer(modifier = Modifier.width(AppDimens.fabExtendedTextGap))
                    Text(
                        text = "快速提报",
                        color = MiuixTheme.colorScheme.onPrimary,
                        style = AppBody1Medium()
                    )
                }
            }
        }
    } else {
        FloatingActionButton(
            onClick = onAddRecord,
            shape = AppShapes.pill,
            containerColor = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(AppDimens.fabSize)
        ) {
            Icon(
                MiuixIcons.Add,
                contentDescription = "添加记录",
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(AppIconSize.md)
            )
        }
    }
}

/** 区块小标题（对齐设置页的 [SmallTitle]）。 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    SmallTitle(text = title, modifier = modifier)
}

/**
 * 空状态占位：图标 + 标题（+ 可选副标题），居中展示。
 * 用于首页无记录、统计无数据等场景，取代各页手写的零散空态布局。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXs)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(AppIconSize.lg)
        )
        Text(
            title,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

/**
 * 圆形/胶囊容器：用于头像、色块、图标徽标等需要圆形成品的场景。
 * 默认裁剪为圆形（[AppShapes.pill]），调用方传入背景色与内容。
 */
@Composable
fun Pill(
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(color),
        content = content
    )
}

/**
 * 全局「无记录」空状态：首页与记录页共用同一套插画、文案与主操作按钮。
 * 尺寸 / 圆角 / 图标 / 字重全部走令牌层，避免两页各自维护一份逐渐漂移的实现。
 */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    onAction: () -> Unit,
    title: String = "还没有加班记录",
    subtitle: String = "记录第一笔加班，从这里开始",
    actionText: String = "添加记录",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceLg * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)
    ) {
        Box(
            modifier = Modifier.size(AppDimens.emptyCanvas),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.emptyCircle)
                    .clip(AppShapes.pill)
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(AppDimens.emptyDotLarge)
                    .offset(x = AppDimens.spaceXs, y = -AppDimens.spaceXs)
                    .clip(AppShapes.pill)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(AppDimens.emptyDotSmall)
                    .offset(x = -AppDimens.spaceSm, y = AppDimens.spaceXs)
                    .clip(AppShapes.pill)
                    .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f))
            )
            Icon(
                icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(AppDimens.emptyIcon)
            )
        }
        Text(
            text = title,
            style = AppTitle3Semibold(),
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = AppDimens.controlCorner,
            insideMargin = PaddingValues(
                horizontal = AppDimens.fabExtendedHorizontal,
                vertical = AppDimens.spaceXs
            )
        ) {
            Icon(
                MiuixIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(AppIconSize.xs)
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
            Text(actionText, style = AppBody1Medium())
        }
    }
}
