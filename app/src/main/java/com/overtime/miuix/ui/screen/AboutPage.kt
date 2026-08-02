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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.BuildConfig
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import com.overtime.miuix.util.UpdateChecker
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutPage(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val appIcon = remember { loadMipmapIcon(context) }
    val versionName = remember { getVersionName(context) }

    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showConsistencyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Hero：应用图标 + 名称 + 版本 + 标语（标准 surface 卡片）
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    insideMargin = PaddingValues(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 图标置于 primaryContainer 圆角容器，克制强调色
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(MiuixTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            appIcon?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "应用图标",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(96.dp)
                                )
                            } ?: Icon(
                                MiuixIcons.AppRecording,
                                contentDescription = "应用图标",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "加班记",
                            style = MiuixTheme.textStyles.headline2,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "版本 $versionName",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "用心记录，每一份付出都值得被看见",
                            style = MiuixTheme.textStyles.title3,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // 关于应用（实底卡片，与全局组件一致）
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    insideMargin = PaddingValues(20.dp)
                ) {
                    Text(
                        text = "关于应用",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "一款简洁实用的加班记录与薪资计算工具，帮你轻松记录每一次加班，精准计算应得报酬。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // 主要功能
            item {
                SmallTitle(text = "主要功能")
                Spacer(modifier = Modifier.height(10.dp))
            }
            item { FeatureGrid() }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // 开发者信息（与设置页一致的 BasicComponent 列表）
            item {
                Column {
                    SmallTitle(text = "开发者")
                    BasicComponent(
                        title = "作者",
                        summary = "Mars",
                        startAction = { Icon(MiuixIcons.Info, contentDescription = null) }
                    )
                    BasicComponent(
                        title = "开源仓库",
                        summary = "Github",
                        startAction = { Icon(MiuixIcons.Link, contentDescription = null) },
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.ChevronForward,
                                contentDescription = "打开链接",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/moonbai/overtime-miuix")
                            )
                            context.startActivity(intent)
                        }
                    )
                    BasicComponent(
                        title = "检查更新",
                        summary = "同时查询 GitHub 与 CNB 最新版本",
                        startAction = { Icon(MiuixIcons.Update, contentDescription = null) },
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.ChevronForward,
                                contentDescription = "检查更新",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            if (checking) return@BasicComponent
                            checking = true
                            scope.launch {
                                val info = UpdateChecker.check(versionName)
                                checking = false
                                if (info == null) {
                                    snackbarHostState.showCustomToast(
                                        UpdateChecker.lastError ?: "检查失败，请检查网络连接"
                                    )
                                } else if (UpdateChecker.hasUpdate(versionName, BuildConfig.VERSION_CODE, info)) {
                                    // 有新版：提示更新
                                    updateInfo = info
                                    showUpdateDialog = true
                                } else {
                                    // 版本一致：额外校验安装包与官方是否一致
                                    val localSha = UpdateChecker.getInstalledApkSha256(context)
                                    val consistent = UpdateChecker.isLocalConsistent(info, localSha)
                                    if (consistent == false) {
                                        // 与仓库官方安装包不一致（可能被篡改/替换），提示重装
                                        updateInfo = info
                                        showConsistencyDialog = true
                                    } else {
                                        val msg = if (UpdateChecker.sourceMismatch) {
                                            "已是最新版本（注：GitHub 与 CNB 版本不一致，可能未同步）"
                                        } else {
                                            "已是最新版本"
                                        }
                                        snackbarHostState.showCustomToast(msg)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // 版权信息
            item {
                Text(
                    text = "© 2026 Mars",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "All rights reserved",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // 更新对话框
    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        OverlayDialog(
            show = true,
            title = "发现新版本",
            summary = "当前版本：$versionName\n最新版本：${info.latestVersion}",
            onDismissRequest = { showUpdateDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (info.releaseNotes.isNotBlank()) {
                    Text(
                        text = info.releaseNotes,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(info.releaseUrl.ifBlank { "https://github.com/moonbai/overtime-miuix/releases/latest" })
                        )
                        context.startActivity(intent)
                        showUpdateDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("前往下载") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    text = "稍后再说",
                    onClick = { showUpdateDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 校验一致性对话框：版本一致但与官方安装包校验值不符，提示重新安装
    if (showConsistencyDialog && updateInfo != null) {
        val info = updateInfo!!
        OverlayDialog(
            show = true,
            title = "安装包校验不一致",
            summary = "当前安装包与官方发布版本校验值不一致，可能存在被修改或替换的情况，建议重新下载安装。",
            onDismissRequest = { showConsistencyDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(info.releaseUrl.ifBlank { "https://github.com/moonbai/overtime-miuix/releases/latest" })
                        )
                        context.startActivity(intent)
                        showConsistencyDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("前往重新下载") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    text = "忽略",
                    onClick = { showConsistencyDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FeatureGrid() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard("本地记录", MiuixIcons.Info, Modifier.weight(1f))
            FeatureCard("多渠道推送", MiuixIcons.Alarm, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard("智能识别", MiuixIcons.AppRecording, Modifier.weight(1f))
            FeatureCard("云端备份", MiuixIcons.CloudFill, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard("薪资计算", MiuixIcons.BankCards, Modifier.weight(1f))
            FeatureCard("日历同步", MiuixIcons.Months, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
        info.applicationInfo?.loadIcon(pm)?.let { drawableToBitmap(it) }
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
