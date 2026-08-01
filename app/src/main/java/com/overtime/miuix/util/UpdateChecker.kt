package com.overtime.miuix.util

import android.content.Context
import android.content.pm.PackageManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.ktor.client.HttpClient
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
 * 优先直连 GitHub API，失败时自动 fallback 到镜像源。
 * 额外提供「安装包校验一致性」比对：将本地已装 APK 的 SHA-256
 * 与官方 Release 中声明的校验值比对，不一致时同样提示用户更新/重装。
 */
object UpdateChecker {

    private const val REPO_OWNER = "moonbai"
    private const val REPO_NAME = "overtime-miuix"
    private val RELEASES_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    // 镜像源列表（按优先级依次 fallback）
    private val MIRROR_URLS = listOf(
        "https://ghproxy.com/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest",
        "https://gh.api.99988866.xyz/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    )

    // 从 Release 正文解析官方 APK 的 SHA-256（形如 "SHA256: xxxx"）
    private val SHA256_PATTERN = Pattern.compile(
        "sha256[:：]\\s*([0-9a-fA-F]{64})",
        Pattern.CASE_INSENSITIVE
    )

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val releaseUrl: String,
        /** 官方发布的 APK 校验值（从 Release 正文解析，可能为空） */
        val expectedSha256: String?
    )

    private var cachedInfo: UpdateInfo? = null

    /**
     * 计算本机已安装 APK 文件的 SHA-256（小写十六进制）。
     * 用于与官方校验值比对，检测安装包是否被篡改/替换。
     */
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

    /** 本地安装包校验值是否与官方一致（expected 为空时返回 null 表示无法判断） */
    fun isLocalConsistent(info: UpdateInfo?, localSha256: String?): Boolean? {
        if (info?.expectedSha256 == null || localSha256 == null) return null
        return info.expectedSha256.equals(localSha256, ignoreCase = true)
    }

    suspend fun check(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            // 优先直连 GitHub API
            var result = tryFetch(RELEASES_URL)
            // 失败则逐个尝试镜像源
            if (result == null) {
                for (mirrorUrl in MIRROR_URLS) {
                    result = tryFetch(mirrorUrl)
                    if (result != null) break
                }
            }
            result
        }
    }

    private suspend fun tryFetch(url: String): UpdateInfo? {
        return try {
            val client = HttpClient()
            val response = client.get(url) {
                headers.append(HttpHeaders.Accept, "application/vnd.github+json")
            }
            val body = response.bodyAsText()
            client.close()
            val json = JsonParser.parseString(body).asJsonObject
            val tagName = json.get("tag_name")?.asString?.removePrefix("v") ?: return null
            val htmlUrl = json.get("html_url")?.asString ?: ""
            val bodyText = json.get("body")?.asString ?: ""
            val assets = json.getAsJsonArray("assets") ?: return null
            val asset = assets.firstOrNull { el ->
                el.asJsonObject.get("name")?.asString?.endsWith(".apk") == true
            }
            val downloadUrl = asset?.asJsonObject?.get("browser_download_url")?.asString ?: ""
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
        } catch (_: Exception) {
            null
        }
    }
}
