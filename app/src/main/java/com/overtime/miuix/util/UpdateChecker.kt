package com.overtime.miuix.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 检查结果：成功 / 失败显式区分，「是否已是最新」随成功结果一并返回。
 *
 * 参考 MiMarketPurify 的 UpdateChecker：单源 GitHub、单次请求即可得出结论，
 * 结果自带判定，调用方无需读取检查器的全局可变状态。
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
 * GitHub Releases 更新检查器（单数据源）。
 *
 * 仓库为公开仓库，无需令牌即可匿名访问 `GET /repos/{owner}/{repo}/releases/latest`
 * （匿名限额 60 次/小时/IP，对「手动检查更新」足够）。编译期注入
 * `BuildConfig.GITHUB_TOKEN` 时优先使用令牌以获得更高限额，作为可选增强。
 * 直连失败时依次尝试公开镜像，本质上仍是同一个 GitHub Release 数据，不再引入第二个数据源。
 *
 * 参考 MiMarketPurify：8s 超时、直链解析、按设备 ABI 挑选 APK、
 * 版本号比较忽略预发布/构建元数据、发布说明保留完整内容。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO_OWNER = "moonbai"
    private const val REPO_NAME = "overtime-miuix"

    private val GITHUB_TOKEN: String = BuildConfig.GITHUB_TOKEN

    private val RELEASES_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    private const val ACCEPT_GITHUB = "application/vnd.github+json"

    // 直连失败时的公开仓库匿名代理（不携带令牌），仍指向同一个 GitHub Release
    private val MIRROR_URLS = listOf(
        "https://ghproxy.com/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest",
        "https://gh.api.99988866.xyz/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    )

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

    /** 检查更新：GitHub 单源，令牌 → 匿名 → 镜像按顺序命中即返回。 */
    suspend fun check(currentVersion: String, currentVersionCode: Int): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val errors = mutableListOf<String>()
            try {
                val info = tryGithub(errors)
                if (info == null) {
                    val reason = errors.joinToString("；")
                        .ifBlank { "更新检查失败，请检查网络连接或稍后重试" }
                    Log.w(TAG, "更新检查失败: $reason")
                    UpdateCheckResult.Failure(reason)
                } else {
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

    private suspend fun tryGithub(errors: MutableList<String>): UpdateInfo? {
        if (GITHUB_TOKEN.isNotBlank()) {
            tryFetch(RELEASES_URL, GITHUB_TOKEN, "GitHub", errors)?.let { return it }
        }
        tryFetch(RELEASES_URL, null, "GitHub", errors)?.let { return it }
        for (mirror in MIRROR_URLS) {
            tryFetch(mirror, null, "GitHub 镜像", errors)?.let { return it }
        }
        return null
    }

    /** 通用请求：[source] 仅用于错误提示标注来源。 */
    private suspend fun tryFetch(
        url: String,
        token: String?,
        source: String,
        errors: MutableList<String>
    ): UpdateInfo? {
        return try {
            val response = client.get(url) {
                headers.append(HttpHeaders.Accept, ACCEPT_GITHUB)
                headers.append(HttpHeaders.UserAgent, "overtime-miuix-UpdateChecker")
                if (!token.isNullOrBlank()) {
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            val status = response.status.value
            val body = response.bodyAsText()

            when (status) {
                200 -> parseRelease(body, source) ?: run {
                    errors.add("$source：发布信息解析失败")
                    null
                }
                401, 403 -> {
                    val err = if (!token.isNullOrBlank()) {
                        "$source：令牌无效或无权限访问该仓库"
                    } else if (body.contains("rate limit", ignoreCase = true)) {
                        "$source：API 请求频率超限，请稍后重试"
                    } else {
                        "$source：访问受限"
                    }
                    errors.add(err)
                    Log.w(TAG, "$source API 返回 $status: $err")
                    null
                }
                404 -> {
                    errors.add("$source：未找到发布版本")
                    Log.w(TAG, "$source API 返回 404")
                    null
                }
                else -> {
                    val err = "$source API 返回异常状态码 $status"
                    errors.add(err)
                    Log.w(TAG, "$source API 返回 $status: ${body.take(200)}")
                    null
                }
            }
        } catch (e: Exception) {
            val err = "$source：${e.javaClass.simpleName}（${e.message?.take(80)}）"
            errors.add(err)
            Log.w(TAG, "$source 请求异常: $url -> ${e.message}")
            null
        }
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
