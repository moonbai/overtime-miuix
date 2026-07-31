package com.overtime.miuix.push

import android.util.Base64
import android.util.Log
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PushManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private fun typeLabel(type: OvertimeType): String = when (type) {
        OvertimeType.WORKDAY -> "工作日"
        OvertimeType.WEEKEND -> "周末"
        OvertimeType.HOLIDAY -> "节假日"
    }

    fun buildText(record: OvertimeRecord): String {
        val typeStr = typeLabel(record.type)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(record.date))
        val startStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.startTime))
        val endStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.endTime))
        val reason = record.note.takeIf { it.isNotBlank() } ?: "无"
        return """日期: $dateStr
类型: $typeStr
时间: $startStr-$endStr
时长: ${"%.2f".format(record.durationHours)}小时
金额: ¥${"%.2f".format(record.amount)}
事由: $reason""".trimIndent()
    }

    /**
     * 生成 HMAC-SHA256 签名
     */
    private fun hmacSha256(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val signData = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(signData, Base64.NO_WRAP)
    }

    suspend fun sendDingTalk(url: String, secret: String, record: OvertimeRecord): Boolean = withContext(Dispatchers.IO) {
        val text = buildText(record)
        val json = """{"msgtype":"text","text":{"content":"${escapeJson(text)}"}}"""
        try {
            val body = json.toRequestBody(mediaType)
            val finalUrl = if (secret.isNotBlank()) {
                val timestamp = System.currentTimeMillis().toString()
                val stringToSign = "$timestamp\n$secret"
                val sign = URLEncoder.encode(hmacSha256(secret, stringToSign), "UTF-8")
                if (url.contains("?")) {
                    "$url&timestamp=$timestamp&sign=$sign"
                } else {
                    "$url?timestamp=$timestamp&sign=$sign"
                }
            } else {
                url
            }
            val req = Request.Builder()
                .url(finalUrl)
                .post(body)
                .header("Content-Type", "application/json; charset=utf-8")
                .build()
            val res = client.newCall(req).execute()
            val response = res.body?.string()
            Log.d("PushManager", "钉钉推送响应: $response")
            res.close()
            res.isSuccessful && response?.contains("\"errcode\":0") == true
        } catch (e: Exception) {
            Log.e("PushManager", "钉钉推送失败", e)
            false
        }
    }

    suspend fun sendFeishu(url: String, secret: String, record: OvertimeRecord): Boolean = withContext(Dispatchers.IO) {
        val text = buildText(record)

        if (url.contains("open.feishu.cn/open-apis/bot/v2/hook")) {
            val json = if (secret.isNotBlank()) {
                val timestamp = System.currentTimeMillis().toString()
                val stringToSign = "$timestamp\n$secret"
                val sign = hmacSha256(secret, stringToSign)
                """{"msg_type":"text","content":{"text":"${escapeJson(text)}"},"timestamp":"$timestamp","sign":"$sign"}"""
            } else {
                """{"msg_type":"text","content":{"text":"${escapeJson(text)}"}}"""
            }
            try {
                val body = json.toRequestBody(mediaType)
                val req = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build()
                val res = client.newCall(req).execute()
                val response = res.body?.string()
                Log.d("PushManager", "飞书推送响应: $response")
                res.close()

                if (response != null) {
                    val jsonResponse = try {
                        org.json.JSONObject(response)
                    } catch (e: Exception) {
                        null
                    }
                    jsonResponse?.let {
                        val code = it.optInt("code", -1)
                        val statusCode = it.optInt("StatusCode", -1)
                        return@withContext code == 0 || statusCode == 0 || res.isSuccessful
                    }
                }
                res.isSuccessful
            } catch (e: Exception) {
                Log.e("PushManager", "飞书推送失败", e)
                false
            }
        } else {
            Log.d("PushManager", "飞书URL格式不正确: $url")
            false
        }
    }

    suspend fun sendWeCom(url: String, secret: String, record: OvertimeRecord): Boolean = withContext(Dispatchers.IO) {
        val text = buildText(record)
        val json = if (secret.isNotBlank()) {
            """{"msgtype":"text","text":{"content":"${escapeJson(text)}"},"mentioned_list":["@all"]}"""
        } else {
            """{"msgtype":"text","text":{"content":"${escapeJson(text)}"}}"""
        }
        try {
            val body = json.toRequestBody(mediaType)
            val req = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json; charset=utf-8")
                .build()
            val res = client.newCall(req).execute()
            val response = res.body?.string()
            Log.d("PushManager", "企业微信推送响应: $response")
            res.close()
            res.isSuccessful && response?.contains("\"errcode\":0") == true
        } catch (e: Exception) {
            Log.e("PushManager", "企业微信推送失败", e)
            false
        }
    }

    suspend fun sendWxPusher(url: String, record: OvertimeRecord): Boolean = withContext(Dispatchers.IO) {
        val text = buildText(record)
        val json = """{"content":"${escapeJson(text)}","contentType":1,"summary":"加班记录推送"}"""
        try {
            val body = json.toRequestBody(mediaType)
            val req = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json; charset=utf-8")
                .build()
            val res = client.newCall(req).execute()
            val response = res.body?.string()
            Log.d("PushManager", "WxPusher推送响应: $response")
            res.close()
            res.isSuccessful && response?.contains("\"success\":true") == true
        } catch (e: Exception) {
            Log.e("PushManager", "WxPusher推送失败", e)
            false
        }
    }

    suspend fun sendTelegram(url: String, chatId: String, record: OvertimeRecord): Boolean = withContext(Dispatchers.IO) {
        val text = buildText(record)
        val json = """{"chat_id":"${escapeJson(chatId)}","text":"${escapeJson(text)}"}"""
        try {
            val body = json.toRequestBody(mediaType)
            val req = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json; charset=utf-8")
                .build()
            val res = client.newCall(req).execute()
            val response = res.body?.string()
            Log.d("PushManager", "Telegram推送响应: $response")
            res.close()
            res.isSuccessful && response?.contains("\"ok\":true") == true
        } catch (e: Exception) {
            Log.e("PushManager", "Telegram推送失败", e)
            false
        }
    }

    suspend fun sendDiscord(url: String, username: String, record: OvertimeRecord): Boolean = withContext(Dispatchers.IO) {
        val text = buildText(record)
        val json = if (username.isNotBlank()) {
            """{"content":"${escapeJson(text)}","username":"${escapeJson(username)}"}"""
        } else {
            """{"content":"${escapeJson(text)}"}"""
        }
        try {
            val body = json.toRequestBody(mediaType)
            val req = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json; charset=utf-8")
                .build()
            val res = client.newCall(req).execute()
            Log.d("PushManager", "Discord推送响应码: ${res.code}")
            res.close()
            res.isSuccessful
        } catch (e: Exception) {
            Log.e("PushManager", "Discord推送失败", e)
            false
        }
    }

    suspend fun sendCustom(url: String, headers: String, record: OvertimeRecord): Boolean = withContext(Dispatchers.IO) {
        val text = buildText(record)
        try {
            val body = text.toRequestBody("text/plain; charset=utf-8".toMediaType())
            val reqBuilder = Request.Builder()
                .url(url)
                .post(body)
            // 解析自定义请求头，每行格式：HeaderName: HeaderValue
            if (headers.isNotBlank()) {
                headers.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.contains(":")) {
                        val parts = trimmed.split(":", limit = 2)
                        if (parts.size == 2) {
                            reqBuilder.header(parts[0].trim(), parts[1].trim())
                        }
                    }
                }
            }
            val req = reqBuilder.build()
            val res = client.newCall(req).execute()
            Log.d("PushManager", "自定义推送响应: ${res.code}")
            res.close()
            res.isSuccessful
        } catch (e: Exception) {
            Log.e("PushManager", "自定义推送失败", e)
            false
        }
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * 根据配置推送到当前选择的单个渠道
     */
    suspend fun sendToSelectedChannel(channel: String, config: Map<String, String>, record: OvertimeRecord): Boolean =
        withContext(Dispatchers.IO) {
            when (channel) {
                "dingtalk" -> {
                    val url = config["push_dingtalk"] ?: return@withContext false
                    val secret = config["push_dingtalk_secret"] ?: ""
                    sendDingTalk(url, secret, record)
                }
                "feishu" -> {
                    val url = config["push_feishu"] ?: return@withContext false
                    val secret = config["push_feishu_secret"] ?: ""
                    sendFeishu(url, secret, record)
                }
                "wecom" -> {
                    val url = config["push_wecom"] ?: return@withContext false
                    val secret = config["push_wecom_secret"] ?: ""
                    sendWeCom(url, secret, record)
                }
                "wxpusher" -> {
                    val url = config["push_wxpusher"] ?: return@withContext false
                    sendWxPusher(url, record)
                }
                "telegram" -> {
                    val url = config["push_telegram"] ?: return@withContext false
                    val chatId = config["push_telegram_chatid"] ?: return@withContext false
                    sendTelegram(url, chatId, record)
                }
                "discord" -> {
                    val url = config["push_discord"] ?: return@withContext false
                    val username = config["push_discord_username"] ?: ""
                    sendDiscord(url, username, record)
                }
                "custom" -> {
                    val url = config["push_custom"] ?: return@withContext false
                    val headers = config["push_custom_headers"] ?: ""
                    sendCustom(url, headers, record)
                }
                else -> false
            }
        }
}
