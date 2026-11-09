package com.mars.overtime.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object WebDavManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 公平内存：驱逐 OkHttp 空闲连接（下次请求自动重建，无功能影响）。
     *
     * **v1.1.4 崩溃修复**：evictAll() 会同步关闭空闲 SSL socket，close() 需发送
     * TLS close_notify（网络写）。Application.onTrimMemory 在主线程派发，此处若同步
     * 执行会触发 StrictMode NetworkOnMainThreadException 导致闪退。
     * 借用 OkHttp dispatcher 的后台线程池执行，任何调用点都安全。
     */
    fun evictConnections() {
        client.dispatcher.executorService.execute {
            runCatching { client.connectionPool.evictAll() }
        }
    }

    data class WebDavConfig(
        val baseUrl: String,
        val username: String,
        val password: String,
        val remotePath: String = "/overtime_backup/"
    )

    private fun createCredentials(config: WebDavConfig): String {
        val credentials = "${config.username}:${config.password}"
        return "Basic " + android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
    }

    private fun buildUrl(config: WebDavConfig, fileName: String): String {
        val base = config.baseUrl.trimEnd('/')
        val path = config.remotePath.trim('/')
        val fullPath = if (path.isNotEmpty() && !path.endsWith('/')) {
            "$path/"
        } else {
            path
        }
        return if (fullPath.isNotEmpty()) {
            "$base/$fullPath$fileName"
        } else {
            "$base/$fileName"
        }
    }

    private fun buildListUrl(config: WebDavConfig): String {
        val base = config.baseUrl.trimEnd('/')
        val path = config.remotePath.trim('/')
        val finalPath = if (path.isNotEmpty() && !path.endsWith('/')) {
            "$path/"
        } else {
            path
        }
        return if (finalPath.isNotEmpty()) {
            "$base/$finalPath"
        } else {
            "$base/"
        }
    }

    private suspend fun ensureDirectoryExists(config: WebDavConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val base = config.baseUrl.trimEnd('/')
            val path = config.remotePath.trim('/')
            val fullPath = if (path.isNotEmpty() && !path.endsWith('/')) {
                "$path/"
            } else {
                path
            }

            val segments = fullPath.split('/').filter { it.isNotEmpty() }
            var currentUrl = base
            for (segment in segments) {
                currentUrl = "$currentUrl/$segment"
                val mkcolUrl = if (!currentUrl.endsWith('/')) {
                    "$currentUrl/"
                } else {
                    currentUrl
                }
                Log.d("WebDavManager", "创建目录: $mkcolUrl")
                val request = Request.Builder()
                    .url(mkcolUrl)
                    .method("MKCOL", null)
                    .header("Authorization", createCredentials(config))
                    .build()
                val response = client.newCall(request).execute()
                val success = response.isSuccessful || response.code in listOf(201, 204, 207)
                Log.d("WebDavManager", "创建目录 $mkcolUrl: ${response.code}")
                response.close()
            }
            true
        } catch (e: Exception) {
            Log.e("WebDavManager", "创建目录失败", e)
            false
        }
    }

    suspend fun uploadFile(config: WebDavConfig, localFilePath: String, remoteFileName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.d("WebDavManager", "开始上传: $localFilePath -> $remoteFileName")
                val file = File(localFilePath)
                if (!file.exists()) {
                    Log.e("WebDavManager", "本地文件不存在: $localFilePath")
                    return@withContext false
                }

                ensureDirectoryExists(config)
                val url = buildUrl(config, remoteFileName)

                val mediaType = "application/json".toMediaType()
                val body = file.readBytes().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .put(body)
                    .header("Authorization", createCredentials(config))
                    .header("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                val success = response.isSuccessful || code in listOf(201, 204, 207)
                Log.d("WebDavManager", "上传: $code, success: $success")
                if (!success) {
                    val errorBody = response.body?.string()
                    Log.e("WebDavManager", "上传失败: $errorBody")
                }
                response.close()
                success
            } catch (e: Exception) {
                Log.e("WebDavManager", "上传失败", e)
                false
            }
        }

    suspend fun downloadFile(config: WebDavConfig, remoteFileName: String, localFilePath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.d("WebDavManager", "下载从: $remoteFileName -> $localFilePath")
                val url = buildUrl(config, remoteFileName)

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", createCredentials(config))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.bytes()?.let { bytes ->
                        File(localFilePath).writeBytes(bytes)
                        response.close()
                        Log.d("WebDavManager", "下载成功")
                        return@withContext true
                    }
                }
                val errorBody = response.body?.string()
                Log.e("WebDavManager", "下载失败: ${response.code}, $errorBody")
                response.close()
                false
            } catch (e: Exception) {
                Log.e("WebDavManager", "下载失败", e)
                false
            }
        }

    suspend fun listFiles(config: WebDavConfig): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = buildListUrl(config)
            Log.d("WebDavManager", "列出文件从: $url")

            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", null)
                .header("Authorization", createCredentials(config))
                .header("Depth", "1")
                .header("Content-Type", "application/xml")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val code = response.code
            val success = response.isSuccessful
            Log.d("WebDavManager", "列表响应: HTTP $code, 长度 ${body.length}")
            response.close()
            // 207 Multi-Status 是 PROPFIND 的正常返回
            if (!success && code != 207) {
                Log.e("WebDavManager", "列出文件失败: HTTP $code")
                return@withContext emptyList()
            }

            // PROPFIND Depth:1 会把「目录自身」也作为一个 response 返回，且各服务器
            // 的 XML 命名空间前缀不一（d: / D: / 无前缀）。旧实现只抓 <d:href> 文本，
            // 再用 contains("overtime") 过滤，导致远程目录 /overtime_backup/ 被当成文件；
            // substringAfterLast('/') 对以 / 结尾的 href 返回空字符串，于是列表里多出
            // 一个空白名字条目，点击它下载的是目录，必然失败。
            // 根治：按 response 块解析，排除带 collection 的目录，再只保留备份文件名。
            val ns = "(?:[A-Za-z0-9_-]+:)?"
            val responsePattern = Regex("<${ns}response\\b[^>]*>([\\s\\S]*?)</${ns}response>", RegexOption.IGNORE_CASE)
            val hrefPattern = Regex("<${ns}href>([^<]*)</${ns}href>", RegexOption.IGNORE_CASE)
            val collectionPattern = Regex("<${ns}collection\\b", RegexOption.IGNORE_CASE)

            val blocks = responsePattern.findAll(body).map { it.groupValues[1] }.toList()
                .ifEmpty { listOf(body) }

            blocks.asSequence()
                .filterNot { collectionPattern.containsMatchIn(it) }
                .flatMap { block -> hrefPattern.findAll(block).map { m -> m.groupValues[1] } }
                .map { raw ->
                    val decoded = try {
                        java.net.URLDecoder.decode(raw, "UTF-8")
                    } catch (e: Exception) {
                        raw
                    }
                    decoded.trim().trimEnd('/').substringAfterLast('/')
                }
                .filter { name ->
                    name.startsWith("overtime_backup_", ignoreCase = true) &&
                        (name.endsWith(".zip", ignoreCase = true) || name.endsWith(".json", ignoreCase = true))
                }
                .distinct()
                .sortedDescending()
                .toList()
        } catch (e: Exception) {
            Log.e("WebDavManager", "列出文件失败", e)
            emptyList()
        }
    }

    suspend fun testConnection(config: WebDavConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = buildListUrl(config)
            Log.d("WebDavManager", "测试连接: $url")

            ensureDirectoryExists(config)

            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", null)
                .header("Authorization", createCredentials(config))
                .header("Depth", "0")
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful || response.code in listOf(200, 201, 207)
            Log.d("WebDavManager", "连接测试: ${response.code}, success: $success")
            if (!success) {
                val errorBody = response.body?.string()
                Log.e("WebDavManager", "连接失败: $errorBody")
            }
            response.close()
            success
        } catch (e: Exception) {
            Log.e("WebDavManager", "连接测试失败", e)
            false
        }
    }
}
