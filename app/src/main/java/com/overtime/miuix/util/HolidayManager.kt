package com.overtime.miuix.util

import java.util.*

object HolidayManager {
    private val holidayCalendar = mapOf(
        "2026-01-01" to "元旦",
        "2026-02-14" to "春节",
        "2026-02-15" to "春节",
        "2026-02-16" to "春节",
        "2026-02-17" to "春节",
        "2026-02-18" to "春节",
        "2026-02-19" to "春节",
        "2026-02-20" to "春节",
        "2026-02-21" to "春节",
        "2026-04-04" to "清明节",
        "2026-04-05" to "清明节",
        "2026-04-06" to "清明节",
        "2026-05-01" to "劳动节",
        "2026-05-02" to "劳动节",
        "2026-05-03" to "劳动节",
        "2026-06-01" to "儿童节",
        "2026-06-20" to "端午节",
        "2026-06-21" to "端午节",
        "2026-06-22" to "端午节",
        "2026-10-01" to "国庆节",
        "2026-10-02" to "国庆节",
        "2026-10-03" to "国庆节",
        "2026-10-04" to "国庆节",
        "2026-10-05" to "国庆节",
        "2026-10-06" to "国庆节",
        "2026-10-07" to "国庆节",
        "2026-10-08" to "国庆节"
    )
    
    fun isHoliday(date: Date): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return holidayCalendar.containsKey(sdf.format(date))
    }
    
    fun isWeekend(date: Date): Boolean {
        val cal = Calendar.getInstance()
        cal.time = date
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }
    
    fun getHolidayName(date: Date): String? {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return holidayCalendar[sdf.format(date)]
    }
}
