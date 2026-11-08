package com.mars.overtime.util

import android.util.Log
import com.mars.overtime.data.model.OvertimeType
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

    // 公平内存：有界 LRU（accessOrder），上限≈5.6 年全年日历，超限按最久未用淘汰，防止无界增长。
    // 读写分散在 IO 协程与内存预警清理线程，故用 synchronizedMap 保证并发安全。
    private const val HOLIDAY_CACHE_MAX = 2048
    private val holidayCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, HolidayInfo>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HolidayInfo>?): Boolean =
                size > HOLIDAY_CACHE_MAX
        }
    )

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

    /**
     * 公平内存：驱逐 OkHttp 空闲连接（下次请求自动重建，无功能影响）。
     *
     * 必须异步：evictAll() 同步关闭 SSL socket 会发送 TLS close_notify（网络写），
     * 主线程调用触发 StrictMode NetworkOnMainThreadException（Android 17 实测崩溃）。
     * 借用 OkHttp dispatcher 的后台线程池，任何调用点都安全。
     */
    fun evictConnections() {
        client.dispatcher.executorService.execute {
            runCatching { client.connectionPool.evictAll() }
        }
    }

    /**
     * 公平内存：内存预警时清理日历缓存。
     * @param keepCurrentYearOnly 轻档预警只保留当年（次年起自动重建）；重档全清。
     */
    fun trimCache(keepCurrentYearOnly: Boolean) {
        // 迭代类操作（keys.retainAll）需在同步块内执行，避免与 IO 协程写入并发
        synchronized(holidayCache) {
            if (!keepCurrentYearOnly) { holidayCache.clear(); return }
            val year = Calendar.getInstance().get(Calendar.YEAR).toString()
            holidayCache.keys.retainAll { it.startsWith(year) }
        }
    }

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
        // 忽略节假日：只按周末判定，不读取节假日规则、也不发起网络请求
        if (cachedIgnoreHoliday == true) {
            return@withContext if (isWeekend(dateStr)) OvertimeType.WEEKEND else OvertimeType.WORKDAY
        }
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

    /**
     * 依据 mxnzp 日期 API 的 detailsType 判定加班类型：
     *   0 = 工作日      -> WORKDAY（工作日加班）
     *   1 = 假日(周末)   -> WEEKEND（休息日加班）
     *   2 = 普通节假日   -> WEEKEND（休息日加班，双倍档）
     *   3 = 三倍工资节假日 -> HOLIDAY（节假日加班）
     * 参考文档：https://www.mxnzp.com/doc/detail?id=1
     */
    private fun infoToType(info: HolidayInfo): OvertimeType {
        return when (info.type) {
            0 -> OvertimeType.WORKDAY
            1, 2 -> OvertimeType.WEEKEND
            3 -> OvertimeType.HOLIDAY
            else -> if (info.isHoliday) OvertimeType.WEEKEND else OvertimeType.WORKDAY
        }
    }

    fun isHoliday(date: Date): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val key = sdf.format(date)
        if (cachedIgnoreHoliday == true) return isWeekend(key)
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

    /**
     * 读取缓存中的日期信息（含节假日名称与类型），无缓存时返回 null。
     * 供推送载荷判断调休等场景使用；同步读缓存，不触发网络请求。
     */
    fun getDayInfo(dateStr: String): HolidayInfo? = holidayCache[dateStr]

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
