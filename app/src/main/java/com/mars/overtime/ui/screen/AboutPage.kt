package com.mars.overtime.ui.screen
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppIconSize
import com.mars.overtime.ui.theme.AppShapes

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.BuildConfig
import com.mars.overtime.ui.component.AppFeaturePalette
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.CHECKING_SNACKBAR_MESSAGE
import com.mars.overtime.ui.snackbar.dismissCheckingSnackbar
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.util.UpdateCheckResult
import com.mars.overtime.util.UpdateChecker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
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
    // Hero 展示应用自身的启动图标，不替换为自绘图形
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
                cornerRadius = AppDimens.cardCorner,
                insideMargin = PaddingValues(28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 展示应用自身的启动图标（渐变时钟 + 橙色日历贴纸），不做任何替换。
                    // 承载底色由浅蓝 primaryContainer 改为纯白：图标是透明底 + 高饱和渐变，
                    // 蓝底会与图标撞色、压灰图案；白底在浅色主题下与卡片自然融合，
                    // 深色主题下也能让彩色图标保持原貌。
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(AppShapes.radiusXxl)
                            .background(Color.White),
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

        item { Spacer(modifier = Modifier.height(AppDimens.groupSpacing)) }

        // 应用简介：归入标准分组容器，标题与卡片由 SettingsGroup 统一定义
        item {
            SettingsGroup(title = "关于应用") {
                Text(
                    text = "简洁实用的加班记录与薪资计算工具，支持本地记录、多渠道推送、云端备份、日历同步、智能识别与薪资自动计算。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(
                        horizontal = AppDimens.itemHorizontal,
                        vertical = 12.dp
                    )
                )
            }
        }

        item { Spacer(modifier = Modifier.height(AppDimens.groupSpacing)) }

        // 主要功能：保留网格概览，但去掉「卡片套卡片」的嵌套，
        // 直接以图标 + 文字铺在分组卡片内，与全局卡片规范一致。
        item {
            SettingsGroup(title = "主要功能") {
                FeatureGrid()
            }
        }

        item { Spacer(modifier = Modifier.height(AppDimens.groupSpacing)) }

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
                        // 网络不通时两个通道各 8s 超时，此前点击后最长约 16s 只有一行
                        // 不起眼的摘要文字变化，容易被当成「点了没反应」。这里在 300ms
                        // 内未返回时先弹一条提示给出即时反馈，检查结束再精确收起它，
                        // 避免与结果提示叠成两条。
                        val done = CompletableDeferred<Unit>()
                        scope.launch {
                            delay(300)
                            if (!done.isCompleted) {
                                snackbarHostState.showCustomToast(CHECKING_SNACKBAR_MESSAGE)
                            }
                        }
                        scope.launch {
                            try {
                                // 检查结果自带「是否有更新」与「失败原因」，
                                // UI 不再读取检查器的全局状态
                                val result = UpdateChecker.check(
                                    versionName, BuildConfig.VERSION_CODE
                                )
                                done.complete(Unit)
                                snackbarHostState.dismissCheckingSnackbar()
                                when (result) {
                                    is UpdateCheckResult.Failure -> {
                                        // 失败原因较长，给足 4 秒，避免 2 秒一闪而过
                                        snackbarHostState.showSnackbar(
                                            result.reason,
                                            duration = SnackbarDuration.Custom(4000)
                                        )
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
                                done.complete(Unit)
                                snackbarHostState.dismissCheckingSnackbar()
                                snackbarHostState.showCustomToast(
                                    "检查异常：${e.javaClass.simpleName}"
                                )
                            } finally {
                                done.complete(Unit)
                                checking = false
                            }
                        }
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(AppDimens.groupSpacing)) }

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

        item { Spacer(modifier = Modifier.height(AppDimens.groupSpacing)) }
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

/**
 * 功能色板：六个功能各给一个低饱和色，容器取 12% 淡底、图标取实色。
 * 取代原来「六个一模一样的实心蓝圆」——既单调，实心高饱和块还会压过文字。
 * 色板是固定的品牌功能色，不随主题强调色变化，避免用户换主题后六格糊成一片。
 */
private data class AboutFeature(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color
)

@Composable
private fun FeatureGrid() {
    // 图标按语义重新选配：本地记录=Notes、推送=Send、智能识别=Scan、
    // 云端备份=UploadCloud、薪资计算=BankCards、日历同步=Months。
    // 旧版用 Info / Alarm / AppRecording 表达记录 / 推送 / 识别，语义完全错配。
    val features = listOf(
        AboutFeature("本地记录", MiuixIcons.Notes, AppFeaturePalette.record),
        AboutFeature("多渠道推送", MiuixIcons.Send, AppFeaturePalette.push),
        AboutFeature("智能识别", MiuixIcons.Scan, AppFeaturePalette.smart),
        AboutFeature("云端备份", MiuixIcons.UploadCloud, AppFeaturePalette.cloud),
        AboutFeature("薪资计算", MiuixIcons.BankCards, AppFeaturePalette.salary),
        AboutFeature("日历同步", MiuixIcons.Months, AppFeaturePalette.calendar)
    )
    val rows = features.chunked(2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceXs)
    ) {
        rows.forEachIndexed { index, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)
            ) {
                rowItems.forEach { feature ->
                    FeatureCell(feature = feature, modifier = Modifier.weight(1f))
                }
                // 末行奇数个时用占位补齐，保持两列对齐
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            if (index != rows.lastIndex) {
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            }
        }
    }
}

@Composable
private fun FeatureCell(
    feature: AboutFeature,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = AppDimens.spaceXxs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(AppShapes.radiusMd)
                .background(feature.tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = feature.tint,
                modifier = Modifier.size(AppIconSize.md)
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs2))
        Text(
            text = feature.title,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

// 辅助函数

/**
 * 读取应用自身的启动图标（渐变时钟 + 橙色日历）。Hero 必须展示真实应用图标，
 * 不能用自绘图形替代；优先按密度取 mipmap 位图，失败时再退回 PackageManager
 * 的 application icon，并统一转成 Bitmap。先量尺寸再按 2 的幂降采样：
 * xxxhdpi 全尺寸 ARGB_8888 单张可占 2MB+ 堆内存。
 */
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

private fun getVersionName(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: PackageManager.NameNotFoundException) { "1.0.0" }
}
