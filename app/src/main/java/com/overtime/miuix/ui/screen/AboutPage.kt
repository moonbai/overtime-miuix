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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.BuildConfig
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import com.overtime.miuix.util.UpdateCheckResult
import com.overtime.miuix.util.UpdateChecker
import kotlinx.coroutines.launch
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val RELEASE_PAGE_URL =
    "https://github.com/moonbai/overtime-miuix/releases/latest"
private const val REPO_URL = "https://github.com/moonbai/overtime-miuix"
private const val WEIBO_PROFILE_URL = "https://weibo.com/u/3963594403"

/**
 * 统一的外链打开入口：设备上没有可处理 http 链接的浏览器 / 应用时
 * （如极简 ROM、工作资料隔离），startActivity 会抛 ActivityNotFoundException，
 * 这里降级为提示而不是让应用崩溃。
 */
private fun openExternalLink(context: Context, url: String, onUnavailable: () -> Unit) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        onUnavailable()
    }
}

private fun formatApkSize(bytes: Long): String = if (bytes >= 1024 * 1024) {
    "%.1f MB".format(Locale.US, bytes / 1024.0 / 1024.0)
} else {
    "%.0f KB".format(Locale.US, bytes / 1024.0)
}

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

    // 所有外链共用同一套「打不开就提示」的兜底逻辑
    // 显式声明 () -> Unit：lambda 体返回 Job（scope.launch），
    // 不标注类型会被推断为 () -> Job，传给 openExternalLink 的 () -> Unit 参数时编译失败
    val onLinkUnavailable: () -> Unit = {
        scope.launch { snackbarHostState.showCustomToast("未找到可打开该链接的应用") }
    }
    val openLink: (String) -> Unit = { url ->
        openExternalLink(context, url, onLinkUnavailable)
    }

    // 复用设置页统一骨架（返回栏 + Snackbar + 16dp 页面留白 + 12dp 分组间距），
    // 让「关于」与所有二级设置页共享同一套视觉规范。
    SettingsScreen(
        title = "关于",
        onBack = { navController.popBackStack() }
    ) {
        // Hero 卡片：图标 + 名称 + 版本 + 一行 Slogan
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = SettingsDimens.CardCorner,
                insideMargin = PaddingValues(28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MiuixTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        appIcon?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "应用图标",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(68.dp)
                            )
                        } ?: Icon(
                            MiuixIcons.AppRecording,
                            contentDescription = "应用图标",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "加班记",
                            style = MiuixTheme.textStyles.headline2,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "v$versionName",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "用心记录，每一份付出都值得被看见",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(SettingsDimens.GroupSpacing)) }

        // 应用简介：归入标准分组容器，标题与卡片由 SettingsGroup 统一定义
        item {
            SettingsGroup(title = "关于应用") {
                Text(
                    text = "简洁实用的加班记录与薪资计算工具，支持本地记录、多渠道推送、云端备份、日历同步、智能识别与薪资自动计算。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(
                        horizontal = SettingsDimens.ItemHorizontal,
                        vertical = 12.dp
                    )
                )
            }
        }

        item { Spacer(modifier = Modifier.height(SettingsDimens.GroupSpacing)) }

        // 主要功能：保留网格概览，但去掉「卡片套卡片」的嵌套，
        // 直接以图标 + 文字铺在分组卡片内，与全局卡片规范一致。
        item {
            SettingsGroup(title = "主要功能") {
                FeatureGrid()
            }
        }

        item { Spacer(modifier = Modifier.height(SettingsDimens.GroupSpacing)) }

        // 开发者 & 操作：与设置首页一致的 SettingsActionItem 跳转行
        item {
            SettingsGroup(title = "关于") {
                SettingsActionItem(
                    title = "作者",
                    summary = "Mars",
                    icon = MiuixIcons.Info,
                    onClick = { openLink(WEIBO_PROFILE_URL) }
                )
                SettingsActionItem(
                    title = "开源仓库",
                    summary = "GitHub",
                    icon = MiuixIcons.Link,
                    onClick = { openLink(REPO_URL) }
                )
                SettingsActionItem(
                    title = "检查更新",
                    summary = if (checking) "正在检查…" else "查询 GitHub 发布的最新版本",
                    icon = MiuixIcons.Update,
                    onClick = {
                        if (checking) return@SettingsActionItem
                        checking = true
                        scope.launch {
                            try {
                                // 检查结果自带「是否有更新」与「失败原因」，
                                // UI 不再读取检查器的全局状态
                                when (
                                    val result = UpdateChecker.check(
                                        versionName, BuildConfig.VERSION_CODE
                                    )
                                ) {
                                    is UpdateCheckResult.Failure -> {
                                        snackbarHostState.showCustomToast(result.reason)
                                    }
                                    is UpdateCheckResult.Success -> {
                                        if (result.hasUpdate) {
                                            updateInfo = result.info
                                            showUpdateDialog = true
                                        } else {
                                            val localSha =
                                                UpdateChecker.getInstalledApkSha256(context)
                                            if (UpdateChecker.isLocalConsistent(
                                                    result.info, localSha
                                                ) == false
                                            ) {
                                                updateInfo = result.info
                                                showConsistencyDialog = true
                                            } else {
                                                snackbarHostState.showCustomToast("已是最新版本")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showCustomToast(
                                    "检查异常：${e.javaClass.simpleName}"
                                )
                            } finally {
                                checking = false
                            }
                        }
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(SettingsDimens.GroupSpacing)) }

        // 版权
        item {
            Text(
                text = "© 2026 Mars · All rights reserved",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        item { Spacer(modifier = Modifier.height(SettingsDimens.GroupSpacing)) }
    }

    // 更新对话框
    if (showUpdateDialog && updateInfo != null) {
        BackHandler(enabled = true) { showUpdateDialog = false }
        val info = updateInfo!!
        val apkSummary = if (info.sizeBytes > 0) {
            "\n安装包：${info.apkName.ifBlank { "APK" }}（${formatApkSize(info.sizeBytes)}）"
        } else ""
        OverlayDialog(
            show = true,
            title = "发现新版本",
            summary = "当前版本：$versionName\n最新版本：${info.latestVersion}$apkSummary",
            onDismissRequest = { showUpdateDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (info.releaseNotes.isNotBlank()) {
                    // 发布说明保留完整内容，限高后可滚动，避免长更新日志撑开对话框
                    Text(
                        text = info.releaseNotes,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // 优先直达 APK 直链；解析不到资源时退回发布页
                Button(
                    onClick = {
                        val target = info.downloadUrl.ifBlank {
                            info.releaseUrl.ifBlank { RELEASE_PAGE_URL }
                        }
                        openLink(target)
                        showUpdateDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (info.downloadUrl.isNotBlank()) "下载 APK" else "前往发布页")
                }
                if (info.downloadUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        text = "查看发布页",
                        onClick = {
                            openLink(info.releaseUrl.ifBlank { RELEASE_PAGE_URL })
                            showUpdateDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    text = "稍后再说",
                    onClick = { showUpdateDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 一致性校验对话框
    if (showConsistencyDialog && updateInfo != null) {
        BackHandler(enabled = true) { showConsistencyDialog = false }
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
                        openLink(info.releaseUrl.ifBlank { RELEASE_PAGE_URL })
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
    val features = listOf(
        "本地记录" to MiuixIcons.Info,
        "多渠道推送" to MiuixIcons.Alarm,
        "智能识别" to MiuixIcons.AppRecording,
        "云端备份" to MiuixIcons.CloudFill,
        "薪资计算" to MiuixIcons.BankCards,
        "日历同步" to MiuixIcons.Months
    )
    // 2 列网格，单元格直接铺在分组卡片内（不再各自套卡片），
    // 保留图标 + 文字的概览形式，同时与全局卡片规范一致。
    val rows = features.chunked(2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        rows.forEachIndexed { index, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { (title, icon) ->
                    FeatureCell(title = title, icon = icon, modifier = Modifier.weight(1f))
                }
                // 末行奇数个时用占位补齐，保持两列对齐
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            if (index != rows.lastIndex) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun FeatureCell(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
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
                    // 公平内存：先量尺寸、再按 2 的幂降采样，输出压到 256px 级别；
                    // 全尺寸 ARGB_8888 图标（xxxhdpi 768px）单张可占 2MB+ 堆内存
                    val bound = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeResource(resources, resId, bound)
                    var sampleSize = 1
                    var longest = maxOf(bound.outWidth, bound.outHeight)
                    while (longest / 2 >= 256) { longest /= 2; sampleSize *= 2 }
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inSampleSize = sampleSize
                    }
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
