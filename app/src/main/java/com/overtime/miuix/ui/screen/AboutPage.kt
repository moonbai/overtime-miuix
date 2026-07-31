package com.overtime.miuix.ui.screen

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val appIcon = remember { loadMipmapIcon(context) }
    val versionName = remember { getVersionName(context) }

    val backdrop = rememberLayerBackdrop()
    val blurSupported = isRuntimeShaderSupported()

    Box(modifier = Modifier.fillMaxSize()) {
        // 底层渐变背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MiuixTheme.colorScheme.primary,
                            MiuixTheme.colorScheme.primaryContainer,
                            MiuixTheme.colorScheme.background
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SmallTopAppBar(
                    title = "关于",
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
                item { Spacer(modifier = Modifier.height(24.dp)) }

                // 应用图标（圆角卡片 + 毛玻璃边框）
                item {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(28.dp),
                                blurRadius = 20f,
                                enabled = blurSupported
                            )
                            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        appIcon?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "应用图标",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(80.dp)
                            )
                        } ?: Icon(
                            MiuixIcons.AppRecording,
                            contentDescription = "应用图标",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // 应用名称 — Foreground Blur 前景模糊
                item {
                    Text(
                        text = "加班记",
                        modifier = Modifier
                            .textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(0.dp),
                                blurRadius = 120f,
                                enabled = blurSupported,
                                contentBlendMode = BlendMode.DstIn
                            ),
                        style = MiuixTheme.textStyles.headline2,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }

                item {
                    Text(
                        text = "版本 $versionName",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    Text(
                        text = "用心记录，每一份付出都值得被看见",
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // 关于应用卡片（TextureBlur + Card）
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(20.dp),
                                blurRadius = 30f,
                                enabled = blurSupported
                            )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            insideMargin = PaddingValues(20.dp),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = "关于应用",
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "一款简洁实用的加班记录与薪资计算工具，帮你轻松记录每一次加班，精准计算应得报酬。",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }

                // 主要功能
                item {
                    Text(
                        text = "主要功能",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    FeatureGrid(backdrop, blurSupported)
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // 作者
                item {
                    FrostedCard(backdrop, blurSupported) {
                        InfoRow("作者", "Mars")
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                // 开源仓库 — 可点击跳转浏览器
                item {
                    val ctx = context
                    FrostedCard(backdrop, blurSupported) {
                        ClickableInfoRow(
                            label = "开源仓库",
                            value = "github.com/moonbai/overtime-miuix",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/moonbai/overtime-miuix"))
                                ctx.startActivity(intent)
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                item {
                    Text(
                        text = "© 2026 Mars",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                item {
                    Text(
                        text = "All rights reserved",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f)
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

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
                shape = RoundedCornerShape(16.dp),
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

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun ClickableInfoRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = "打开链接",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun FeatureGrid(
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    blurSupported: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard("本地记录", MiuixIcons.Info, backdrop, blurSupported, Modifier.weight(1f))
            FeatureCard("多渠道推送", MiuixIcons.Alarm, backdrop, blurSupported, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard("智能识别", MiuixIcons.AppRecording, backdrop, blurSupported, Modifier.weight(1f))
            FeatureCard("云端备份", MiuixIcons.CloudFill, backdrop, blurSupported, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard("薪资计算", MiuixIcons.BankCards, backdrop, blurSupported, Modifier.weight(1f))
            FeatureCard("日历同步", MiuixIcons.Months, backdrop, blurSupported, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    blurSupported: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.textureBlur(
            backdrop = backdrop,
            shape = RoundedCornerShape(12.dp),
            blurRadius = 20f,
            enabled = blurSupported
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 辅助函数
private fun loadMipmapIcon(context: Context): Bitmap? {
    val resources = context.resources
    val packageName = context.packageName
    val mipmapNames = listOf("ic_launcher", "ic_launcher_round")
    val densitySuffixes = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi", "")

    for (name in mipmapNames) {
        for (suffix in densitySuffixes) {
            val resName = if (suffix.isEmpty()) name else "${name}_${suffix}"
            val resId = resources.getIdentifier(resName, "mipmap", packageName)
            if (resId != 0) {
                try {
                    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    val bitmap = BitmapFactory.decodeResource(resources, resId, options)
                    if (bitmap != null) return bitmap
                } catch (_: Exception) {}
            }
        }
    }

    return try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(packageName, 0)
        val drawable = info.applicationInfo?.loadIcon(pm) ?: return null
        drawableToBitmap(drawable)
    } catch (_: Exception) { null }
}

private fun getVersionName(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: PackageManager.NameNotFoundException) { "1.0.0" }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    return when (drawable) {
        is BitmapDrawable -> drawable.bitmap
        else -> {
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 192
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }
}
