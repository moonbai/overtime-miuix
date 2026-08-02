package com.overtime.miuix.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.overtime.miuix.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * GitHub / CNB 双源 Release 更新检查器。
 *
 * 仓库在 GitHub 为【公开仓库】，更新检查无需令牌即可匿名访问
 * `GET /repos/{owner}/{repo}/releases/latest`（匿名限额 60 次/小时/IP，对“手动检查更新”足够）。
 * 若编译期注入了令牌（BuildConfig.GITHUB_TOKEN），则优先使用令牌以获得更高限额
 * （5000 次/小时）并兼容私有仓库场景。令牌仅作可选增强，缺失时不影响公开仓库检查。
 *
 * 为防止【单边不可达】（如网络/区域限制导致 GitHub 无法连接），
 * 在 GitHub（令牌 → 匿名 → 镜像兜底）全部失败后，追加 CNB（cnb.cool）作为兜底数据源。
 * CNB 同样支持公开仓库匿名访问；若配置了 BuildConfig.CNB_TOKEN 则带令牌请求。
 *
 * 请求顺序：GitHub 令牌 → GitHub 匿名 → GitHub 镜像 → CNB（可选令牌）。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO_OWNER = "moonbai"
    private const val REPO_NAME = "overtime-miuix"

    // 可选令牌：编译期经 BuildConfig.GITHUB_TOKEN 注入（源码无明文）。
    // 公开仓库下可留空，更新检查仍能正常工作。
    private val GITHUB_TOKEN: String = BuildConfig.GITHUB_TOKEN

    // 可选令牌：编译期经 BuildConfig.CNB_TOKEN 注入（源码无明文）。
    // CNB 作为 GitHub 的兜底数据源；公开仓库可匿名访问，配置后支持更高限额/私有仓库。
    private val CNB_TOKEN: String = BuildConfig.CNB_TOKEN

    private val RELEASES_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    // CNB 侧仓库路径与 GitHub 不同：CNB 托管在 ismartis 组织下，切勿复用 GitHub 的 owner/name。
    private const val CNB_REPO_SLUG = "ismartis/Overtime"

    // CNB（cnb.cool）兜底数据源：字段结构与 GitHub releases/latest 兼容（tag_name / body / assets）。
    // 注意：CNB OpenAPI 不支持匿名访问（匿名返回 401），必须注入 BuildConfig.CNB_TOKEN 才会生效。
    private val CNB_RELEASES_URL =
        "https://api.cnb.cool/$CNB_REPO_SLUG/-/releases/latest"

    // GitHub 与 CNB 的 Accept 头不通用：CNB 只接受 application/json，
    // 收到 application/vnd.github+json 会直接返回 406。
    private const val ACCEPT_GITHUB = "application/vnd.github+json"
    private const val ACCEPT_CNB = "application/json"

    // 直连失败时的兜底镜像（仅供【公开仓库】匿名代理，不携带令牌）。
    private val MIRROR_URLS = listOf(
        "https://ghproxy.com/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest",
        "https://gh.api.99988866.xyz/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    )

    private val SHA256_PATTERN = Pattern.compile(
        "sha256[:：]\\s*([0-9a-fA-F]{64})",
        Pattern.CASE_INSENSITIVE
    )

    /** 最近一次检查失败的原因（供 UI 展示更精确的提示）。 */
    var lastError: String? = null
        private set

    /** 是否已配置更新令牌（仅用于更高限额 / 私有仓库，公开仓库可无）。 */
    fun isTokenConfigured(): Boolean = GITHUB_TOKEN.isNotBlank()

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val releaseUrl: String,
        val expectedSha256: String?
    )

    private var cachedInfo: UpdateInfo? = null

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
     * 比较版本号字符串（支持 "1.0.4" / "1.0.10" 多段格式）。
     * @return 正数表示 latest 更新，0 相等，负数 latest 更旧。
     */
    fun compareVersion(current: String, latest: String): Int {
        val c = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val l = latest.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(c.size, l.size)
        for (i in 0 until maxLen) {
            val cv = c.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (cv != lv) return lv - cv
        }
        return 0
    }

    /** 是否有更新（latest 版本号严格大于 current） */
    fun hasUpdate(currentVersion: String, info: UpdateInfo): Boolean =
        compareVersion(currentVersion, info.latestVersion) > 0

    /**
     * 检查更新（GitHub → CNB 双源兜底）。
     * @return 成功返回 [UpdateInfo]，失败（网络/鉴权/限流）返回 null，
     *         失败时可通过 [lastError] 获取更精确的原因。
     */
    suspend fun check(currentVersion: String): UpdateInfo? {
        lastError = null
        return withContext(Dispatchers.IO) {
            // 1) GitHub：优先令牌（更高限额 / 私有仓库），否则匿名；再尝试公开镜像兜底
            if (GITHUB_TOKEN.isNotBlank()) {
                tryFetch(RELEASES_URL, GITHUB_TOKEN, "GitHub", ACCEPT_GITHUB)
                    ?.let { return@withContext it }
            }
            tryFetch(RELEASES_URL, null, "GitHub", ACCEPT_GITHUB)?.let { return@withContext it }
            for (mirror in MIRROR_URLS) {
                tryFetch(mirror, null, "GitHub 镜像", ACCEPT_GITHUB)?.let { return@withContext it }
            }
            // 2) CNB 兜底：防止 GitHub 单边不可达（网络 / 区域限制等）
            // CNB 不支持匿名调用，未注入令牌时直接跳过，避免无谓的 401 覆盖掉更有意义的错误提示
            if (CNB_TOKEN.isNotBlank()) {
                tryFetch(CNB_RELEASES_URL, CNB_TOKEN, "CNB", ACCEPT_CNB)?.let {
                    return@withContext it
                }
            }
            if (lastError == null) {
                lastError = "更新检查失败，请检查网络连接或稍后重试"
            }
            Log.e(TAG, "更新检查失败: $lastError")
            null
        }
    }

    private fun createClient(): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
                writeTimeout(15, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * 通用请求：请求 [url]，[token] 非空时附带 Bearer 鉴权，[source] 仅用于错误提示标注来源，
     * [accept] 为该数据源要求的 Accept 头（GitHub 与 CNB 取值不同，混用会被 CNB 判为 406）。
     * CNB 与 GitHub 的 releases/latest 响应字段结构兼容，复用同一解析逻辑。
     */
    private suspend fun tryFetch(
        url: String,
        token: String?,
        source: String,
        accept: String
    ): UpdateInfo? {
        return try {
            createClient().use { client ->
                val response = client.get(url) {
                    headers.append(HttpHeaders.Accept, accept)
                    if (!token.isNullOrBlank()) {
                        headers.append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
                val status = response.status.value
                val body = response.bodyAsText()

                when (status) {
                    200 -> parseRelease(body)
                    401, 403 -> {
                        lastError = if (!token.isNullOrBlank()) {
                            "$source：令牌无效或无权限访问该仓库"
                        } else if (body.contains("rate limit", ignoreCase = true)) {
                            "$source：请求过于频繁，请稍后重试"
                        } else {
                            "$source：访问受限（可能需要配置令牌）"
                        }
                        Log.w(TAG, "$source API 返回 $status: $lastError")
                        null
                    }
                    404 -> {
                        lastError = "$source：未找到发布版本（仓库或 Release 不存在）"
                        Log.w(TAG, "$source API 返回 404")
                        null
                    }
                    else -> {
                        lastError = "$source API 返回异常状态码 $status"
                        Log.w(TAG, "$source API 返回 $status: ${body.take(200)}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            lastError = "$source 请求异常：${e.javaClass.simpleName}"
            Log.w(TAG, "$source 请求异常: $url -> ${e.message}")
            null
        }
    }

    private fun parseRelease(body: String): UpdateInfo? {
        return try {
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val tagName = json.get("tag_name")?.asString?.removePrefix("v") ?: return null
            val htmlUrl = json.get("html_url")?.asString ?: ""
            val bodyText = json.get("body")?.asString ?: ""
            val assets = json.getAsJsonArray("assets")
            val downloadUrl = if (assets != null) {
                val apkAsset = assets.firstOrNull { el ->
                    el.asJsonObject.get("name")?.asString?.endsWith(".apk") == true
                }
                apkAsset?.asJsonObject?.get("browser_download_url")?.asString ?: ""
            } else ""
            val expectedSha256 = SHA256_PATTERN.matcher(bodyText).let { m ->
                if (m.find()) m.group(1)?.lowercase() else null
            }
            val info = UpdateInfo(
                latestVersion = tagName,
                downloadUrl = downloadUrl,
                releaseNotes = bodyText.take(300),
                releaseUrl = htmlUrl,
                expectedSha256 = expectedSha256
            )
            cachedInfo = info
            info
        } catch (e: Exception) {
            lastError = "解析发布信息失败"
            Log.w(TAG, "解析异常: ${e.message}")
            null
        }
    }
}
