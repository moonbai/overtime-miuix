package com.overtime.miuix.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.Html
import android.util.Log
import android.util.Xml
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.overtime.miuix.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 检查结果：成功 / 失败显式区分，「是否已是最新」随成功结果一并返回。
 *
 * 参考 MiMarketPurify 的 UpdateChecker：单源 GitHub、结果自带判定，
 * 调用方无需读取检查器的全局可变状态。
 */
sealed interface UpdateCheckResult {
    data class Success(
        val info: UpdateChecker.UpdateInfo,
        /** 已由检查器按 versionCode / versionName 统一判定 */
        val hasUpdate: Boolean
    ) : UpdateCheckResult

    data class Failure(val reason: String) : UpdateCheckResult
}

/**
 * GitHub Releases 更新检查器（单数据源，两条官方通道）。
 *
 * 数据源始终是 GitHub 官方，不再请求任何第三方镜像/代理：
 * 1. 主通道 `api.github.com/repos/.../releases/latest`：编译期注入
 *    `BuildConfig.GITHUB_TOKEN` 时优先使用令牌（5000 次/小时），
 *    未配置时匿名访问（60 次/小时/IP，共享出口 IP 下易被限流）。
 *    通过 ETag 条件请求（内容未变化时返回 304，直接复用缓存结果）与
 *    结果缓存压低配额消耗，并显式处理 403/429（读取 X-RateLimit-Reset），
 *    限流窗口内跳过该通道。
 * 2. 备用通道 `github.com/.../releases.atom`：GitHub 官方 Release 订阅源，
 *    不属于 REST API、不受匿名 60 次/小时限制，也无需令牌。
 *    仅在 API 限流或不可达时启用，提供版本号 / 发布说明 / 发布页链接，
 *    下载按钮由 UI 自动回退到发布页。
 *
 * 注：浏览器能打开 API 而 App 报限流，是因为浏览器带登录态走用户级配额，
 * App 匿名请求走 IP 级配额；配置 GH_PAT 后可彻底消除该差异。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO_OWNER = "moonbai"
    private const val REPO_NAME = "overtime-miuix"
    private const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"

    private val GITHUB_TOKEN: String = BuildConfig.GITHUB_TOKEN

    private val RELEASES_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val ATOM_URL = "$REPO_URL/releases.atom"

    private const val ACCEPT_GITHUB = "application/vnd.github+json"
    private const val USER_AGENT = "overtime-miuix-UpdateChecker"
    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    private val SHA256_PATTERN = Pattern.compile(
        "sha256[:：]\\s*([0-9a-fA-F]{64})",
        Pattern.CASE_INSENSITIVE
    )

    private val VERSIONCODE_PATTERN = Pattern.compile(
        "versionCode[:：]\\s*([0-9]+)",
        Pattern.CASE_INSENSITIVE
    )

    // 复用同一个客户端，避免每次检查都重建 OkHttp 连接池与线程池
    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(8, TimeUnit.SECONDS)
                    readTimeout(8, TimeUnit.SECONDS)
                    writeTimeout(8, TimeUnit.SECONDS)
                }
            }
        }
    }

    // 进程内缓存：成功结果 / ETag / 限流截止时间。
    // @Volatile 保证协程切换后可见；单次检查最多一次 API 请求，避免叠加消耗配额。
    @Volatile private var cachedInfo: UpdateInfo? = null
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cachedETag: String? = null
    @Volatile private var rateLimitedUntil: Long = 0L // epoch 秒

    /** 是否已配置更新令牌（仅用于更高限额，公开仓库可无）。 */
    fun isTokenConfigured(): Boolean = GITHUB_TOKEN.isNotBlank()

    data class UpdateInfo(
        val latestVersion: String,
        val versionCode: Int?,
        val downloadUrl: String,
        val releaseNotes: String,
        val releaseUrl: String,
        val expectedSha256: String?,
        val source: String = "",
        /** 选中 APK 的文件名，用于 UI 展示 */
        val apkName: String = "",
        /** 选中 APK 的字节数，0 表示未知 */
        val sizeBytes: Long = 0L
    )

    fun getInstalledApkSha256(context: Context): String? {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val sourceDir = pkgInfo.applicationInfo?.sourceDir ?: return null
            val bytes = File(sourceDir).readBytes()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun isLocalConsistent(info: UpdateInfo?, localSha256: String?): Boolean? {
        if (info?.expectedSha256 == null || localSha256 == null) return null
        return info.expectedSha256.equals(localSha256, ignoreCase = true)
    }

    /**
     * 比较版本号字符串（支持多段、v 前缀、预发布与构建元数据）。
     * 例如 "1.1.5" / "v1.2.0-rc1" / "1.2.0+build7" 都只按数字段比较。
     * @return 正数表示 latest 更新，0 相等，负数 latest 更旧。
     */
    fun compareVersion(current: String, latest: String): Int {
        val c = numericSegments(current)
        val l = numericSegments(latest)
        for (i in 0 until maxOf(c.size, l.size)) {
            val diff = l.getOrElse(i) { 0 } - c.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    private fun numericSegments(value: String): List<Int> = value
        .trim()
        .removePrefix("v").removePrefix("V")
        .substringBefore('+')
        .substringBefore('-')
        .split('.')
        .mapNotNull(String::toIntOrNull)

    /**
     * 是否有更新：versionCode 是 Android 的权威版本依据，发布说明里能解析到时以它为准；
     * 缺失时才退回 versionName 比较。
     */
    fun hasUpdate(currentVersion: String, currentVersionCode: Int, info: UpdateInfo): Boolean {
        val remoteCode = info.versionCode
        return if (remoteCode != null) {
            remoteCode > currentVersionCode
        } else {
            compareVersion(currentVersion, info.latestVersion) > 0
        }
    }

    /**
     * 检查更新：官方 API → 官方订阅源，命中即返回。
     * 任一步失败都只向用户呈现一条主因，全部明细写入日志。
     */
    suspend fun check(currentVersion: String, currentVersionCode: Int): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            // 1) 新鲜缓存直接复用，不发起请求、不消耗配额（含 304 复用）
            cachedInfo?.takeIf { now - cachedAt < CACHE_TTL_MS }?.let { info ->
                return@withContext UpdateCheckResult.Success(
                    info = info,
                    hasUpdate = hasUpdate(currentVersion, currentVersionCode, info)
                )
            }

            val errors = mutableListOf<String>()
            try {
                // 2) 限流窗口内跳过 REST API，直接走不受配额限制的官方订阅源
                val apiLimited = now / 1000 < rateLimitedUntil
                val info = (if (apiLimited) null else tryGithubApi(errors))
                    ?: tryOfficialFeed(errors)

                if (info == null) {
                    val reason = summarizeFailure(errors)
                    Log.w(TAG, "更新检查失败: $reason | 明细=$errors")
                    UpdateCheckResult.Failure(reason)
                } else {
                    cachedInfo = info
                    cachedAt = System.currentTimeMillis()
                    UpdateCheckResult.Success(
                        info = info,
                        hasUpdate = hasUpdate(currentVersion, currentVersionCode, info)
                    )
                }
            } catch (e: Exception) {
                val reason = "检查过程异常：${e.javaClass.simpleName}"
                Log.e(TAG, "check() 异常", e)
                UpdateCheckResult.Failure(reason)
            }
        }

    /** 主通道：令牌优先（未配置则跳过），令牌失败再退回匿名，最多两次请求。 */
    private suspend fun tryGithubApi(errors: MutableList<String>): UpdateInfo? {
        if (GITHUB_TOKEN.isNotBlank()) {
            tryFetch(RELEASES_URL, GITHUB_TOKEN, "GitHub", errors)?.let { return it }
        }
        return tryFetch(RELEASES_URL, null, "GitHub", errors)
    }

    /** 通用 API 请求：[source] 仅用于错误提示标注来源。 */
    private suspend fun tryFetch(
        url: String,
        token: String?,
        source: String,
        errors: MutableList<String>
    ): UpdateInfo? {
        return try {
            val response = client.get(url) {
                headers.append(HttpHeaders.Accept, ACCEPT_GITHUB)
                headers.append(HttpHeaders.UserAgent, USER_AGENT)
                if (!token.isNullOrBlank()) {
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
                // 条件请求：发布内容未变化时返回 304，直接复用缓存结果，避免无效刷新
                cachedETag?.let { headers.append("If-None-Match", it) }
            }
            val status = response.status.value

            when (status) {
                200 -> {
                    response.headers["ETag"]?.let { cachedETag = it }
                    val body = response.bodyAsText()
                    parseRelease(body, source) ?: run {
                        errors.add("$source：发布信息解析失败")
                        null
                    }
                }
                304 -> {
                    // 发布未变化，复用缓存结果并续期
                    cachedInfo?.also { cachedAt = System.currentTimeMillis() } ?: run {
                        errors.add("$source：本地缓存已失效，请重试")
                        null
                    }
                }
                401 -> {
                    errors.add("$source：令牌无效或无权限访问该仓库")
                    Log.w(TAG, "$source API 返回 401（令牌无效）")
                    null
                }
                403, 429 -> {
                    val body = response.bodyAsText()
                    val reset = response.headers["X-RateLimit-Reset"]?.toLongOrNull()
                    val isRateLimit = reset != null || body.contains("rate limit", ignoreCase = true)
                    if (isRateLimit) {
                        reset?.let { rateLimitedUntil = it }
                        errors.add(rateLimitMessage(source, reset))
                        Log.w(TAG, "$source API 返回 $status（限流），reset=$reset")
                    } else if (!token.isNullOrBlank()) {
                        errors.add("$source：令牌无效或无权限访问该仓库")
                    } else {
                        errors.add("$source：访问受限")
                    }
                    null
                }
                404 -> {
                    errors.add("$source：未找到发布版本")
                    Log.w(TAG, "$source API 返回 404")
                    null
                }
                else -> {
                    val body = response.bodyAsText()
                    errors.add("$source API 返回异常状态码 $status")
                    Log.w(TAG, "$source API 返回 $status: ${body.take(200)}")
                    null
                }
            }
        } catch (e: Exception) {
            errors.add("$source：${e.javaClass.simpleName}")
            Log.w(TAG, "$source 请求异常: $url -> ${e.message}")
            null
        }
    }

    private fun rateLimitMessage(source: String, reset: Long?): String {
        val minutes = reset
            ?.let { ((it - System.currentTimeMillis() / 1000) / 60).coerceAtLeast(1) }
        return if (minutes != null) {
            "$source：API 请求频率超限，约 ${minutes} 分钟后恢复"
        } else {
            "$source：API 请求频率超限，请稍后重试"
        }
    }

    /**
     * 备用通道：GitHub 官方 Release Atom 订阅源。
     * 不走 REST API，因此不受匿名 60 次/小时限制，也无需令牌。
     * 订阅源不含 assets 元数据，downloadUrl 留空，由 UI 回退到发布页。
     */
    private suspend fun tryOfficialFeed(errors: MutableList<String>): UpdateInfo? {
        return try {
            val response = client.get(ATOM_URL) {
                headers.append(HttpHeaders.Accept, "application/atom+xml")
                headers.append(HttpHeaders.UserAgent, USER_AGENT)
            }
            if (response.status.value != 200) {
                errors.add("GitHub 订阅源：HTTP ${response.status.value}")
                Log.w(TAG, "订阅源返回 ${response.status.value}")
                return null
            }
            val entry = parseAtom(response.bodyAsText())
            if (entry == null) {
                errors.add("GitHub 订阅源：发布信息解析失败")
                return null
            }
            val notes = entry.notes
            UpdateInfo(
                latestVersion = entry.tag.removePrefix("v").removePrefix("V"),
                versionCode = VERSIONCODE_PATTERN.matcher(notes).let { m ->
                    if (m.find()) m.group(1)?.toIntOrNull() else null
                },
                downloadUrl = "",
                releaseNotes = notes,
                releaseUrl = entry.link.ifBlank { "$REPO_URL/releases/tag/${entry.tag}" },
                expectedSha256 = SHA256_PATTERN.matcher(notes).let { m ->
                    if (m.find()) m.group(1)?.lowercase() else null
                },
                source = "GitHub 订阅源",
                apkName = "",
                sizeBytes = 0L
            )
        } catch (e: Exception) {
            errors.add("GitHub 订阅源：${e.javaClass.simpleName}")
            Log.w(TAG, "订阅源请求异常", e)
            null
        }
    }

    private data class FeedEntry(val tag: String, val link: String, val notes: String)

    /** 用 Android 原生 XmlPullParser 解析首条 entry，无需引入第三方 XML 库。 */
    private fun parseAtom(xml: String): FeedEntry? {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var inEntry = false
            var done = false
            var tag = ""
            var link = ""
            var notes = ""
            var event = parser.eventType

            while (event != XmlPullParser.END_DOCUMENT && !done) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "entry" -> inEntry = true
                        "id" -> if (inEntry && tag.isEmpty()) {
                            tag = parser.nextText().substringAfterLast('/')
                        }
                        "link" -> if (inEntry && link.isEmpty() &&
                            parser.getAttributeValue(null, "rel") == "alternate"
                        ) {
                            link = parser.getAttributeValue(null, "href").orEmpty()
                        }
                        "content" -> if (inEntry && notes.isEmpty()) {
                            notes = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) {
                        done = true
                    }
                }
                if (!done) event = parser.next()
            }

            if (tag.isBlank()) null
            else FeedEntry(tag, link, Html.fromHtml(notes, Html.FROM_HTML_MODE_COMPACT).toString().trim())
        } catch (e: Exception) {
            Log.w(TAG, "解析订阅源失败: ${e.message}")
            null
        }
    }

    /** 只向用户呈现一条主因：限流优先，其余取第一条；明细全部留在日志里。 */
    private fun summarizeFailure(errors: List<String>): String {
        if (errors.isEmpty()) return "更新检查失败，请检查网络连接或稍后重试"
        val primary = errors.firstOrNull { it.contains("请求频率超限") } ?: errors.first()
        return if (errors.size > 1) "$primary（已尝试 GitHub 官方备用通道）" else primary
    }

    private fun parseRelease(body: String, source: String): UpdateInfo? {
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            val tagName = json.optString("tag_name").removePrefix("v").removePrefix("V")
            if (tagName.isBlank()) return null
            val htmlUrl = json.optString("html_url")
            // 发布说明保留完整内容，由 UI 决定展示长度
            val bodyText = json.optString("body")
            val apkAsset = pickApkAsset(json.getAsJsonArray("assets"))
            val expectedSha256 = SHA256_PATTERN.matcher(bodyText).let { m ->
                if (m.find()) m.group(1)?.lowercase() else null
            }
            val versionCode = VERSIONCODE_PATTERN.matcher(bodyText).let { m ->
                if (m.find()) m.group(1)?.toIntOrNull() else null
            }
            UpdateInfo(
                latestVersion = tagName,
                versionCode = versionCode,
                downloadUrl = apkAsset?.optString("browser_download_url").orEmpty(),
                releaseNotes = bodyText,
                releaseUrl = htmlUrl,
                expectedSha256 = expectedSha256,
                source = source,
                apkName = apkAsset?.optString("name").orEmpty(),
                sizeBytes = apkAsset?.optLong("size") ?: 0L
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析发布信息失败: ${e.message}")
            null
        }
    }

    /**
     * 从发布资源里挑一个可安装的 APK：优先设备 ABI 精确匹配，其次按架构优先级
     * （arm64 > armeabi-v7a > x86_64 > x86），再退到 universal/all。
     * 同时排除 debug / unsigned 包，避免误把 CI 的调试产物当作正式更新。
     */
    private fun pickApkAsset(assets: JsonArray?): JsonObject? {
        if (assets == null) return null
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase()
        val apks = assets.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject
        }.filter { asset ->
            val name = asset.optString("name")
            name.endsWith(".apk", ignoreCase = true) &&
                    !name.contains("debug", ignoreCase = true) &&
                    !name.contains("unsigned", ignoreCase = true)
        }
        if (apks.isEmpty()) return null

        fun rank(asset: JsonObject): Int {
            val name = asset.optString("name").lowercase()
            if (preferredAbi.isNotEmpty() && name.contains(preferredAbi)) return 100
            return when {
                name.contains("arm64") -> 40
                name.contains("armeabi") || name.contains("v7a") -> 30
                name.contains("x86_64") || name.contains("x86-64") -> 20
                name.contains("x86") -> 10
                name.contains("universal") || name.contains("all") -> 5
                else -> 1
            }
        }
        return apks.maxByOrNull { rank(it) }
    }

    private fun JsonObject.optString(name: String): String = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }.getOrNull().orEmpty()

    private fun JsonObject.optLong(name: String): Long = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asLong
    }.getOrNull() ?: 0L
}
