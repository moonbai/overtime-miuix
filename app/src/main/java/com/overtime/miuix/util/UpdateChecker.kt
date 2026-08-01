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
 * 调用仓库 Releases API 获取最新版本号与下载链接。
 */
object UpdateChecker {

    private const val REPO_OWNER = "moonbai"
    private const val REPO_NAME = "overtime-miuix"
    private val RELEASES_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val releaseUrl: String
    )

    private var cachedInfo: UpdateInfo? = null

    suspend fun check(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val client = HttpClient()
                val response = client.get(RELEASES_URL) {
                    headers.append(HttpHeaders.Accept, "application/vnd.github+json")
                }
                val body = response.bodyAsText()
                client.close()
                val json = JsonParser.parseString(body).asJsonObject
                val tagName = json.get("tag_name")?.asString?.removePrefix("v") ?: return@withContext null
                val htmlUrl = json.get("html_url")?.asString ?: ""
                val bodyText = json.get("body")?.asString ?: ""
                val assets = json.getAsJsonArray("assets") ?: return@withContext null
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
}
