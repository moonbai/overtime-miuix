package com.mars.overtime.ui.screen
import com.mars.overtime.ui.theme.AppBody1Medium
import com.mars.overtime.ui.theme.AppDimens
import com.mars.overtime.ui.theme.AppHeadline2Bold
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
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.CHECKING_SNACKBAR_MESSAGE
import com.mars.overtime.ui.snackbar.dismissCheckingSnackbar
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.util.UpdateCheckResult
import com.mars.overtime.util.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** 单一弹窗状态：两类结果弹窗天然互斥，且能跨配置变更恢复。 */
private enum class UpdateDialogKind { NONE, UPDATE, CONSISTENCY }

private data class UpdateDialogState(
    val kind: UpdateDialogKind,
    val info: UpdateChecker.UpdateInfo? = null
)

private val NO_DIALOG = UpdateDialogState(UpdateDialogKind.NONE)

/**
 * UpdateInfo 不是 Parcelable / Serializable，手写 Saver 逐字段存进 Bundle：
 * 旋转、切换深色、分屏等 Activity 重建场景下，检查成功的弹窗不会凭空消失。
 * 自定义 Saver 必须传给 stateSaver 重载（init 返回 MutableState<T>）；
 * 若误传 saver 重载（init 返回 T），by 委托会因没有 getValue 而编译失败。
 */
private val UpdateDialogStateSaver = Saver<UpdateDialogState, Bundle>(
    save = { state ->
        Bundle().apply {
            putString("kind", state.kind.name)
            state.info?.let { i ->
                putString("latestVersion", i.latestVersion)
                putInt("versionCode", i.versionCode ?: -1)
                putString("downloadUrl", i.downloadUrl)
                putString("releaseNotes", i.releaseNotes)
                putString("releaseUrl", i.releaseUrl)
                putString("expectedSha256", i.expectedSha256)
                putString("source", i.source)
                putString("apkName", i.apkName)
                putLong("sizeBytes", i.sizeBytes)
            }
        }
    },
    restore = { b ->
        val kind = runCatching {
            UpdateDialogKind.valueOf(b.getString("kind").orEmpty())
        }.getOrDefault(UpdateDialogKind.NONE)
        if (kind == UpdateDialogKind.NONE) NO_DIALOG
        else UpdateDialogState(
            kind = kind,
            info = UpdateChecker.UpdateInfo(
                latestVersion = b.getString("latestVersion").orEmpty(),
                versionCode = b.getInt("versionCode", -1).takeIf { it >= 0 },
                downloadUrl = b.getString("downloadUrl").orEmpty(),
                releaseNotes = b.getString("releaseNotes").orEmpty(),
                releaseUrl = b.getString("releaseUrl").orEmpty(),
                expectedSha256 = b.getString("expectedSha256"),
                source = b.getString("source").orEmpty(),
                apkName = b.getString("apkName").orEmpty(),
                sizeBytes = b.getLong("sizeBytes", 0L)
            )
        )
    }
)

private val MD_HEADING = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
private val MD_BULLET = Regex("^[*+-]\\s+", RegexOption.MULTILINE)
private val MD_LINK = Regex("\\[([^\\]]+)]\\([^)]+\\)")
private val HTML_TAG = Regex("<[^>]+>")

/**
 * 发布说明展示前统一清洗：
 * - 去掉供检查器解析的机器元数据（versionCode / sha256），避免把 64 位 hex 展示给用户
 * - 去掉 Markdown / HTML 标记，让 REST（原始 Markdown）与 Atom（Html.fromHtml）两条
 *   通道在弹窗里呈现一致的纯文本。检查器仍使用原始 body 解析，互不影响。
 */
private fun cleanReleaseNotes(raw: String): String {
    val text = raw
        .lineSequence()
        .map { it.trim() }
        .filterNot { line ->
            line.contains("versionCode:", ignoreCase = true) ||
                line.contains("sha256:", ignoreCase = true)
        }
        .joinToString("\n")
        .replace(MD_HEADING, "")
        .replace(MD_BULLET, "• ")
        .replace("**", "")
        .replace("`", "")
        .replace(HTML_TAG, "")
        .trim()
    return MD_LINK.replace(text) { it.groupValues[1] }
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
    // 两类结果弹窗共用一个状态：互斥；rememberSaveable 保证 Activity 重建后弹窗不丢。
    // 必须用 stateSaver：该重载的 init 返回 MutableState<T>，才能配合 by 委托。
    var dialogState by rememberSaveable(stateSaver = UpdateDialogStateSaver) {
        mutableStateOf(NO_DIALOG)
    }

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
        onBack = { navController.popBackStack() },
        // 弹窗必须经 dialogs 挂进 Scaffold 的 content，OverlayDialog 才能找到 popup 宿主
        dialogs = {
            UpdateResultDialogs(
                state = dialogState,
                versionName = versionName,
                openLink = openLink,
                onDismiss = { dialogState = NO_DIALOG }
            )
        }
    ) {
        // Hero 卡片：图标 + 名称 + 版本 + 一行 Slogan
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = AppDimens.cardCorner,
                insideMargin = PaddingValues(AppDimens.spaceLg + AppDimens.spaceXxs)
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
                            .size(104.dp)
                            .clip(AppShapes.radiusXxl)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        appIcon?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "应用图标",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(84.dp)
                            )
                        } ?: Icon(
                            MiuixIcons.AppRecording,
                            contentDescription = "应用图标",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(68.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "加班记",
                            style = AppHeadline2Bold(),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                        Text(
                            text = "v$versionName",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    Text(
                        text = "用心记录，每一份付出都值得被看见",
                        style = AppBody1Medium(),
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }


        // 应用简介：归入标准分组容器，标题与卡片由 SettingsGroup 统一定义
        item {
            SettingsGroup(title = "关于应用") {
                Text(
                    text = "简洁实用的加班记录与薪资计算工具，支持本地记录、多渠道推送、云端备份、日历同步、智能识别与薪资自动计算。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(
                        horizontal = AppDimens.itemHorizontal,
                        vertical = AppDimens.spaceSm
                    )
                )
            }
        }


        // 主要功能：保留网格概览，但去掉「卡片套卡片」的嵌套，
        // 直接以图标 + 文字铺在分组卡片内，与全局卡片规范一致。
        item {
            SettingsGroup(title = "主要功能") {
                FeatureGrid()
            }
        }


        // 开发者 & 操作：与设置首页一致的 SettingsActionItem 跳转行
        item {
            SettingsGroup(title = "关于") {
                SettingsActionItem(
                    title = "作者",
                    summary = "Mars",
                    icon = MiuixIcons.Contacts,
                    onClick = { openLink(WEIBO_PROFILE_URL) }
                )
                SettingsActionItem(
                    title = "开源仓库",
                    summary = "GitHub",
                    icon = MiuixIcons.Favorites,
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
                                // 结果分支的弹窗状态赋值（非挂起）必须先于任何 suspend 调用：
                                // dismissCheckingSnackbar 是挂起点，协程若在恢复时被取消，
                                // 后续赋值不再执行，表现为「检查成功却静默无弹窗」。
                                when (result) {
                                    is UpdateCheckResult.Failure -> {
                                        snackbarHostState.dismissCheckingSnackbar()
                                        // 失败原因较长，给足 4 秒，避免 2 秒一闪而过
                                        snackbarHostState.showSnackbar(
                                            result.reason,
                                            duration = SnackbarDuration.Custom(4000)
                                        )
                                    }
                                    is UpdateCheckResult.Success -> {
                                        if (result.hasUpdate) {
                                            dialogState = UpdateDialogState(
                                                UpdateDialogKind.UPDATE, result.info
                                            )
                                            android.util.Log.d(
                                                "AboutPage",
                                                "更新弹窗已触发 latest=${result.info.latestVersion}"
                                            )
                                            snackbarHostState.dismissCheckingSnackbar()
                                        } else {
                                            // 读取整包 APK 算 SHA-256 是磁盘 IO（安装包可达 50MB+），
                                            // 必须切到 IO；压在 Compose 主线程会让「已是最新版本」
                                            // 这条最常走的成功路径掉帧甚至 ANR。
                                            val localSha = withContext(Dispatchers.IO) {
                                                UpdateChecker.getInstalledApkSha256(context)
                                            }
                                            if (UpdateChecker.isLocalConsistent(
                                                    result.info, localSha
                                                ) == false
                                            ) {
                                                dialogState = UpdateDialogState(
                                                    UpdateDialogKind.CONSISTENCY, result.info
                                                )
                                                snackbarHostState.dismissCheckingSnackbar()
                                            } else {
                                                snackbarHostState.dismissCheckingSnackbar()
                                                snackbarHostState.showCustomToast("已是最新版本")
                                            }
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                // 协程取消是正常的结构化并发，不能当「检查异常」吞掉：
                                // catch 内的 suspend 会在已取消协程上立即再次抛出。
                                done.complete(Unit)
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w(
                                    "AboutPage",
                                    "检查更新协程异常: ${e.javaClass.simpleName}",
                                    e
                                )
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

    }

}

/**
 * 检查结果弹窗。必须挂到 SettingsScreen 的 dialogs（Scaffold content 内）：
 * MIUIX OverlayDialog 默认 renderInRootScaffold=true，依赖 Scaffold 提供的 popup 宿主，
 * 放在 Scaffold 外部会「状态已置位、窗口不渲染」，只留下一个看不见的返回消费者。
 * 返回处理完全交给弹窗自身的 NavigationBackHandler + onDismissRequest，
 * 不再额外注册 androidx.activity BackHandler，避免一次返回被消费两次。
 */
@Composable
private fun UpdateResultDialogs(
    state: UpdateDialogState,
    versionName: String,
    openLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dialog = state

    OverlayDialog(
        show = dialog.kind == UpdateDialogKind.UPDATE,
        title = "发现新版本",
        summary = "GitHub 已发布新版本，建议更新",
        onDismissRequest = { onDismiss() }
    ) {
        val info = dialog.info
        if (info != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 版本、包名、数据来源拆成独立行，不再挤在 summary 的小号文字里
                Text(
                    text = "v$versionName  →  v${info.latestVersion}",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                if (info.sizeBytes > 0) {
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                    Text(
                        text = "安装包：${info.apkName.ifBlank { "APK" }}（${formatApkSize(info.sizeBytes)}）",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                val notes = cleanReleaseNotes(info.releaseNotes)
                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    Text(
                        text = "更新内容",
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                    // 与按钮同宽（不再额外水平内缩），限高后可滚动
                    Text(
                        text = notes,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                if (info.source.isNotBlank()) {
                    Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                    Text(
                        text = "数据来源：${info.source}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                if (info.downloadUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                    TextButton(
                        text = "查看发布页",
                        onClick = {
                            openLink(info.releaseUrl.ifBlank { RELEASE_PAGE_URL })
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                // 与全项目弹窗一致：两个等宽按钮横向排列；主操作显式用主色
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        text = "稍后再说",
                        onClick = { onDismiss() },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                    Button(
                        onClick = {
                            // 优先直达 APK 直链；解析不到资源时退回发布页
                            val target = info.downloadUrl.ifBlank {
                                info.releaseUrl.ifBlank { RELEASE_PAGE_URL }
                            }
                            openLink(target)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (info.downloadUrl.isNotBlank()) "下载 APK" else "前往发布页")
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = dialog.kind == UpdateDialogKind.CONSISTENCY,
        title = "安装包校验不一致",
        summary = "当前安装包与官方发布版本校验值不一致，可能存在被修改或替换的情况，建议重新下载安装。",
        onDismissRequest = { onDismiss() }
    ) {
        val info = dialog.info
        if (info != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "忽略",
                    onClick = { onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                Button(
                    onClick = {
                        // 与更新弹窗一致：优先直达 APK，解析不到资源时才回退发布页
                        val target = info.downloadUrl.ifBlank {
                            info.releaseUrl.ifBlank { RELEASE_PAGE_URL }
                        }
                        openLink(target)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) { Text("前往重新下载") }
            }
        }
    }
}

/**
 * 功能宫格条目：图标按语义选配，统一单色主题色渲染（见 [FeatureCell]），
 * 与设置行、底栏导航同一套语言，不再为每个功能套彩色底盒。
 */
private data class AboutFeature(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun FeatureGrid() {
    // 图标按语义选配：本地记录=Notes、推送=Send、智能识别=Scan、
    // 云端备份=UploadCloud、薪资计算=HorizontalSplit（与设置页「基础设置」薪资入口一致）、
    // 日历同步=Months。旧版用 Info / Alarm / AppRecording 表达记录 / 推送 / 识别，语义完全错配。
    val features = listOf(
        AboutFeature("本地记录", MiuixIcons.Notes),
        AboutFeature("多渠道推送", MiuixIcons.Send),
        AboutFeature("智能识别", MiuixIcons.Scan),
        AboutFeature("云端备份", MiuixIcons.UploadCloud),
        AboutFeature("薪资计算", MiuixIcons.HorizontalSplit),
        AboutFeature("日历同步", MiuixIcons.Months)
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
        // 统一单色主题色图标 + 中性底容器：去掉原来每个功能各自的 12% 彩色底盒，
        // 与设置行、底栏导航同一套单色语言，底色不再花花绿绿。
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(AppShapes.radiusMd)
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
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
