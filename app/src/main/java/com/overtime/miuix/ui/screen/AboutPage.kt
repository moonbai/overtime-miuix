package com.overtime.miuix.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutPage(navController: NavHostController) {
    // 模糊背景层：捕获底层渐变，供前景模糊与毛玻璃卡片采样
    val backdrop = rememberLayerBackdrop()
    val blurSupported = isRuntimeShaderSupported()

    Box(modifier = Modifier.fillMaxSize()) {
        // 底层渐变（被 layerBackdrop 捕获，作为模糊源）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MiuixTheme.colorScheme.primary,
                            MiuixTheme.colorScheme.secondary,
                            MiuixTheme.colorScheme.background
                        )
                    )
                )
        )

        Scaffold(
            // 透明容器，露出底层渐变
            containerColor = Color.Transparent,
            topBar = {
                SmallTopAppBar(
                    title = "关于应用",
                    color = Color.Transparent,
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Icon(
                        MiuixIcons.AppRecording,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Foreground Blur：文字字形作为遮罩，透出模糊后的渐变背景
                    Text(
                        text = "加班记",
                        modifier = Modifier
                            .fillMaxWidth()
                            .textureBlur(
                                backdrop = backdrop,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                blurRadius = 150f,
                                enabled = blurSupported,
                                contentBlendMode = BlendMode.DstIn
                            )
                            .padding(vertical = 4.dp),
                        style = MiuixTheme.textStyles.headline2,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "版本 1.0.0",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                item {
                    // 毛玻璃卡片：Texture Blur 高斯模糊 + 半透明底色
                    FrostedCard(backdrop, blurSupported) {
                        Text(
                            text = "应用介绍",
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "一款基于 MIUIX 框架开发的加班记录应用「加班记」，支持工作日、周末、节假日加班分类统计，实时薪资预览，日历同步，智能推送，MCP 服务等功能。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    FrostedCard(backdrop, blurSupported) {
                        Text(
                            text = "技术栈",
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• MIUIX - Compose UI 框架")
                        Text("• Room - 本地数据库")
                        Text("• DataStore - 设置存储")
                        Text("• Navigation Compose - 导航")
                        Text("• MCP SDK - AI 服务集成")
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * 毛玻璃卡片：在捕获的模糊背景之上叠加半透明表面，形成 Texture Blur 效果。
 */
@Composable
private fun FrostedCard(
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    blurSupported: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .textureBlur(
                backdrop = backdrop,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                blurRadius = 30f,
                enabled = blurSupported
            )
    ) {
        Column(
            modifier = Modifier
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(16.dp),
            content = content
        )
    }
}
