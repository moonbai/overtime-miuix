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
import java.util.regex.Pattern

/**
 * GitHub Release 更新检查器。
 * 使用 GitHub Token 认证以避免 API 限流（60 次/小时匿名 vs 5000 次/小时认证）。
 * 优先直连 GitHub API，失败时自动 fallback 到镜像源。
 * 额外提供「安装包校验一致性」比对。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO_OWNER = "moonbai"
    private const val REPO_NAME = "overtime-miuix"

    // GitHub Personal Access Token：从 BuildConfig 读取（Gradle 编译时从环境变量注入）
    // 用于 API 认证，避免匿名限流。Token 仅读公开仓库，风险可控。
    private val GITHUB_TOKEN: String = BuildConfig.GITHUB_TOKEN

    private val RELEASES_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    // 镜像源列表（按优先级依次 fallback）
    private val MIRROR_URLS = listOf(
        "https://ghproxy.com/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest",
        "https://gh.api.99988866.xyz/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    )

    private val SHA256_PATTERN = Pattern.compile(
        "sha256[:：]\\s*([0-9a-fA-F]{64})",
        Pattern.CASE_INSENSITIVE
    )

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

    suspend fun check(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            var result = tryFetch(RELEASES_URL)
            if (result == null) {
                Log.w(TAG, "直连 GitHub API 失败，尝试镜像源")
                for (mirrorUrl in MIRROR_URLS) {
                    result = tryFetch(mirrorUrl)
                    if (result != null) break
                }
            }
            if (result != null) {
                Log.d(TAG, "检查成功: latest=${result.latestVersion}, current=$currentVersion, " +
                    "hasUpdate=${hasUpdate(currentVersion, result)}")
            } else {
                Log.e(TAG, "所有源均不可用")
            }
            result
        }
    }

    private suspend fun tryFetch(url: String): UpdateInfo? {
        return try {
            val client = HttpClient(OkHttp)
            val response = client.get(url) {
                headers.append(HttpHeaders.Accept, "application/vnd.github+json")
                headers.append(HttpHeaders.Authorization, "Bearer $GITHUB_TOKEN")
                // 部分镜像源（ghproxy 等）接受 Token 头转发到上游
            }
            val status = response.status.value
            val body = response.bodyAsText()
            client.close()

            if (status != 200) {
                Log.w(TAG, "API 返回 $status: ${body.take(200)}")
                return null
            }

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
            Log.w(TAG, "请求异常: $url -> ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
