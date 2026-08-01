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
 * GitHub Release 更新检查器。
 *
 * 仓库为【公开仓库】，因此更新检查【无需令牌】即可匿名访问 `/releases/latest`
 * （公开仓库匿名限额 60 次/小时/IP，对“手动检查更新”足够）。
 * 若编译期注入了令牌（BuildConfig.GITHUB_TOKEN），则优先使用令牌以获得更高限额
 * （5000 次/小时）并兼容私有仓库场景。令牌仅作可选增强，缺失时不影响公开仓库检查。
 *
 * 请求顺序：令牌（若有） → 匿名直连 → 镜像兜底（匿名代理公开仓库）。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO_OWNER = "moonbai"
    private const val REPO_NAME = "overtime-miuix"

    // 可选令牌：编译期经 BuildConfig.GITHUB_TOKEN 注入（源码无明文）。
    // 公开仓库下可留空，更新检查仍能正常工作。
    private val GITHUB_TOKEN: String = BuildConfig.GITHUB_TOKEN

    private val RELEASES_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

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
     * 检查更新。
     * @return 成功返回 [UpdateInfo]，失败（网络/鉴权/限流）返回 null，
     *         失败时可通过 [lastError] 获取更精确的原因。
     */
    suspend fun check(currentVersion: String): UpdateInfo? {
        lastError = null
        return withContext(Dispatchers.IO) {
            // 1) 优先使用令牌（更高限额，且兼容私有仓库）
            if (GITHUB_TOKEN.isNotBlank()) {
                val withToken = tryFetch(RELEASES_URL, useToken = true)
                if (withToken != null) return@withContext withToken
            }
            // 2) 公开仓库可匿名访问（无需令牌）
            val anon = tryFetch(RELEASES_URL, useToken = false)
            if (anon != null) return@withContext anon
            // 3) 兜底镜像（匿名代理公开仓库）
            for (mirror in MIRROR_URLS) {
                val m = tryFetch(mirror, useToken = false)
                if (m != null) return@withContext m
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

    private suspend fun tryFetch(url: String, useToken: Boolean): UpdateInfo? {
        return try {
            createClient().use { client ->
                val response = client.get(url) {
                    headers.append(HttpHeaders.Accept, "application/vnd.github+json")
                    if (useToken && GITHUB_TOKEN.isNotBlank()) {
                        headers.append(HttpHeaders.Authorization, "Bearer $GITHUB_TOKEN")
                    }
                }
                val status = response.status.value
                val body = response.bodyAsText()

                when (status) {
                    200 -> parseRelease(body)
                    401, 403 -> {
                        lastError = if (useToken) {
                            "令牌无效或无权限访问该仓库"
                        } else if (body.contains("rate limit", ignoreCase = true)) {
                            "请求过于频繁，请稍后重试"
                        } else {
                            "仓库为私有，需配置更新令牌"
                        }
                        Log.w(TAG, "API 返回 $status: $lastError")
                        null
                    }
                    404 -> {
                        lastError = "未找到发布版本（仓库或 Release 不存在）"
                        Log.w(TAG, "API 返回 404")
                        null
                    }
                    else -> {
                        lastError = "GitHub API 返回异常状态码 $status"
                        Log.w(TAG, "API 返回 $status: ${body.take(200)}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            lastError = "请求异常：${e.javaClass.simpleName}"
            Log.w(TAG, "请求异常: $url -> ${e.message}")
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
