package com.mars.overtime.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 日期格式化统一入口。
 *
 * 机器可读的 key 固定 Locale.US（不受系统语言影响，用于节假日查询、按月分组、日历匹配），
 * 展示文本跟随系统 Locale。格式化器复用并加锁保护，避免各处反复 new SimpleDateFormat，
 * 也避免 IO 线程与主线程并发访问同一个格式化器。
 */
object AppDateFormats {
    private val isoDayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.US)
    private val displayDayFormatter = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())

    fun isoDay(millis: Long): String =
        synchronized(isoDayFormatter) { isoDayFormatter.format(Date(millis)) }

    fun monthKey(millis: Long): String =
        synchronized(monthFormatter) { monthFormatter.format(Date(millis)) }

    fun displayDay(millis: Long): String =
        synchronized(displayDayFormatter) { displayDayFormatter.format(Date(millis)) }

    /** 一次取出年月日，避免各处重复 Calendar.getInstance().apply { ... } */
    fun dateParts(millis: Long): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return Triple(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
