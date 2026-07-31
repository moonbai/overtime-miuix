package com.overtime.miuix.util

import android.util.Log
import com.overtime.miuix.data.model.OvertimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

enum class HolidayDataSource(val label: String) {
    TIMOR("Timor API"),
    MXNZP("MXNZP API"),
    CUSTOM("自定义 API")
}

object HolidayManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val TIMOR_API_URL_TEMPLATE = "https://timor.tech/api/holiday/year/{year}"
    private const val MXNZP_API_URL_TEMPLATE = "https://www.mxnzp.com/api/holiday/list/year/{year}"

    private val holidayCache = mutableMapOf<String, HolidayInfo>()

    data class HolidayInfo(
        val date: String,
        val isHoliday: Boolean,
        val name: String = "",
        val type: Int = 0  // 0=工作日, 1=休息日, 2=节假日
    )

    private var cachedDataSource: HolidayDataSource? = null
    private var cachedCustomUrl: String? = null
    private var cachedMxnzpAppId: String? = null
    private var cachedMxnzpAppSecret: String? = null
    private var cachedIgnoreHoliday: Boolean? = null

    fun configure(
        dataSource: HolidayDataSource,
        customUrl: String? = null,
        mxnzpAppId: String? = null,
        mxnzpAppSecret: String? = null,
        ignoreHoliday: Boolean = false
    ) {
        cachedDataSource = dataSource
        cachedCustomUrl = customUrl
        cachedMxnzpAppId = mxnzpAppId
        cachedMxnzpAppSecret = mxnzpAppSecret
        cachedIgnoreHoliday = ignoreHoliday
    }

    fun getDataSource(): HolidayDataSource = cachedDataSource ?: HolidayDataSource.TIMOR

    private fun isWeekend(dateStr: String): Boolean {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        format.timeZone = TimeZone.getTimeZone("GMT+8")
        val date = format.parse(dateStr) ?: return false
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"), Locale.CHINA)
        cal.time = date
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    private fun buildApiUrl(year: String): String {
        val dataSource = cachedDataSource ?: HolidayDataSource.TIMOR
        val customUrl = cachedCustomUrl
        return when (dataSource) {
            HolidayDataSource.TIMOR -> TIMOR_API_URL_TEMPLATE.replace("{year}", year)
            HolidayDataSource.MXNZP -> MXNZP_API_URL_TEMPLATE.replace("{year}", year)
            HolidayDataSource.CUSTOM -> customUrl?.replace("\${year}", year)?.replace("{year}", year) ?: ""
        }
    }

    private fun parseTimorResponse(body: String) {
        try {
            val json = org.json.JSONObject(body)
            val code = json.optInt("code", -1)
            if (code == 0) {
                val holidayObj = json.optJSONObject("holiday")
                if (holidayObj != null) {
                    val keys = holidayObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val item = holidayObj.optJSONObject(key)
                        if (item != null) {
                            val date = item.optString("date", "")
                            val name = item.optString("name", "")
                            val isHoliday = item.optBoolean("holiday", false)
                            val wage = item.optInt("wage", 0)
                            holidayCache[date] = HolidayInfo(
                                date = date,
                                isHoliday = isHoliday,
                                name = name,
                                type = wage
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HolidayManager", "解析 Timor 响应失败", e)
        }
    }

    private fun parseMxnzpResponse(body: String) {
        try {
            val json = org.json.JSONObject(body)
            val code = json.optInt("code", -1)
            if (code == 1) {
                val dataArray = json.optJSONArray("data")
                if (dataArray != null) {
                    for (i in 0 until dataArray.length()) {
                        val monthItem = dataArray.optJSONObject(i)
                        val daysArray = monthItem?.optJSONArray("days")
                        if (daysArray != null) {
                            for (j in 0 until daysArray.length()) {
                                val item = daysArray.optJSONObject(j)
                                if (item != null) {
                                    val date = item.optString("date", "")
                                    val name = item.optString("name", "")
                                    val detailsType = item.optInt("detailsType", 0)
                                    val isHoliday = detailsType in 1..3
                                    holidayCache[date] = HolidayInfo(
                                        date = date,
                                        isHoliday = isHoliday,
                                        name = name,
                                        type = detailsType
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HolidayManager", "解析 MXNZP 响应失败", e)
        }
    }

    private fun parseCustomResponse(body: String) {
        try {
            val json = org.json.JSONObject(body)
            when {
                json.has("holiday") -> parseTimorResponse(body)
                json.has("data") -> {
                    val data = json.opt("data")
                    if (data is org.json.JSONArray) {
                        parseMxnzpResponse(body)
                    } else {
                        parseSimpleResponse(body)
                    }
                }
                else -> parseSimpleResponse(body)
            }
        } catch (e: Exception) {
            Log.e("HolidayManager", "解析自定义 API 响应失败", e)
        }
    }

    private fun parseSimpleResponse(body: String) {
        try {
            val json = org.json.JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: json.optJSONArray("holidays") ?: return
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val date = item.optString("date", item.optString("dateStr", ""))
                val name = item.optString("name", "")
                val type = item.optInt("detailsType", item.optInt("type", item.optInt("wage", 0)))
                val isHoliday = item.optBoolean("holiday", item.optBoolean("isHoliday", type != 0))
                if (date.isNotEmpty()) {
                    holidayCache[date] = HolidayInfo(date = date, isHoliday = isHoliday, name = name, type = type)
                }
            }
        } catch (e: Exception) {
            Log.e("HolidayManager", "解析简单响应失败", e)
        }
    }

    suspend fun getOvertimeType(dateStr: String): OvertimeType = withContext(Dispatchers.IO) {
        holidayCache[dateStr]?.let { info ->
            return@withContext infoToType(info)
        }

        val year = dateStr.substring(0, 4)
        try {
            var url = buildApiUrl(year)
            val dataSource = cachedDataSource ?: HolidayDataSource.TIMOR

            if (dataSource == HolidayDataSource.MXNZP) {
                val appId = cachedMxnzpAppId ?: ""
                val appSecret = cachedMxnzpAppSecret ?: ""
                if (appId.isNotEmpty() && appSecret.isNotEmpty()) {
                    url = if (url.contains("?")) "$url&app_id=$appId&app_secret=$appSecret"
                    else "$url?app_id=$appId&app_secret=$appSecret"
                }
            }

            if (url.isNotEmpty()) {
                Log.d("HolidayManager", "请求节假日API: $url")
                val req = Request.Builder().url(url).build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()
                res.close()

                if (body != null) {
                    when (dataSource) {
                        HolidayDataSource.TIMOR -> parseTimorResponse(body)
                        HolidayDataSource.MXNZP -> parseMxnzpResponse(body)
                        HolidayDataSource.CUSTOM -> parseCustomResponse(body)
                    }
                }
            }

            holidayCache[dateStr]?.let { info ->
                return@withContext infoToType(info)
            }

            // 无缓存时按周末判断
            if (isWeekend(dateStr)) {
                holidayCache[dateStr] = HolidayInfo(dateStr, true, type = 1)
                OvertimeType.WEEKEND
            } else {
                holidayCache[dateStr] = HolidayInfo(dateStr, false, type = 0)
                OvertimeType.WORKDAY
            }
        } catch (e: Exception) {
            Log.e("HolidayManager", "获取节假日失败", e)
            if (isWeekend(dateStr)) OvertimeType.WEEKEND else OvertimeType.WORKDAY
        }
    }

    private fun infoToType(info: HolidayInfo): OvertimeType {
        return when (info.type) {
            0 -> OvertimeType.WORKDAY
            1 -> OvertimeType.WEEKEND
            2, 3 -> OvertimeType.HOLIDAY
            else -> if (info.isHoliday) OvertimeType.WEEKEND else OvertimeType.WORKDAY
        }
    }

    fun isHoliday(date: Date): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val key = sdf.format(date)
        holidayCache[key]?.let { return it.isHoliday }
        return isWeekend(key)
    }

    fun isWeekend(date: Date): Boolean {
        val cal = Calendar.getInstance()
        cal.time = date
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    fun getHolidayName(date: Date): String? {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return holidayCache[sdf.format(date)]?.name
    }

    suspend fun fetchHolidays(year: String): Boolean = withContext(Dispatchers.IO) {
        try {
            var url = buildApiUrl(year)
            val dataSource = cachedDataSource ?: HolidayDataSource.TIMOR

            if (dataSource == HolidayDataSource.MXNZP) {
                val appId = cachedMxnzpAppId ?: ""
                val appSecret = cachedMxnzpAppSecret ?: ""
                if (appId.isNotEmpty() && appSecret.isNotEmpty()) {
                    url = if (url.contains("?")) "$url&app_id=$appId&app_secret=$appSecret"
                    else "$url?app_id=$appId&app_secret=$appSecret"
                }
            }

            if (url.isEmpty()) return@withContext false

            Log.d("HolidayManager", "拉取节假日: $url")
            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string()
            res.close()

            if (body != null) {
                when (dataSource) {
                    HolidayDataSource.TIMOR -> parseTimorResponse(body)
                    HolidayDataSource.MXNZP -> parseMxnzpResponse(body)
                    HolidayDataSource.CUSTOM -> parseCustomResponse(body)
                }
            }
            body != null
        } catch (e: Exception) {
            Log.e("HolidayManager", "拉取节假日失败", e)
            false
        }
    }

    fun clearCache() {
        holidayCache.clear()
    }

    fun getCacheSize(): Int = holidayCache.size
}
