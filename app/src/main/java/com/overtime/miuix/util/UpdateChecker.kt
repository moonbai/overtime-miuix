package com.overtime.miuix.util

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GitHub Release 更新检查器。
 * 优先直连 GitHub API，失败时自动 fallback 到镜像源。
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

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val releaseUrl: String
    )

    private var cachedInfo: UpdateInfo? = null

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
            val info = UpdateInfo(
                latestVersion = tagName,
                downloadUrl = downloadUrl,
                releaseNotes = bodyText.take(300),
                releaseUrl = htmlUrl
            )
            cachedInfo = info
            info
        } catch (_: Exception) {
            null
        }
    }
}
