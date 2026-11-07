package com.overtime.miuix.util

import com.overtime.miuix.data.model.OvertimeType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object SalaryCalculator {
    private const val WORKING_DAYS_PER_MONTH = 21.75
    private const val HOURS_PER_DAY = 8.0
    
    /**
     * 标准小时薪资口径：月基础薪资 / 21.75 天 / 8 小时。
     * 抽成公共方法，供金额计算与推送内容展示共用，避免两处公式漂移。
     */
    fun hourlySalary(baseSalary: Double): Double =
        baseSalary / WORKING_DAYS_PER_MONTH / HOURS_PER_DAY

    fun calculateOvertimeAmount(
        baseSalary: Double,
        type: OvertimeType,
        rate: Double,
        durationHours: Double
    ): Double {
        // 防御：基础薪资、倍率、时长任一非正均为非法输入，返回 0 避免脏数据
        if (baseSalary <= 0 || rate <= 0 || durationHours <= 0) return 0.0

        return hourlySalary(baseSalary) * rate * durationHours
    }

    /**
     * 请假工资扣减：按标准日薪比例计算（rate=1.0），以负值表示扣款。
     * 例如请全天(8h)扣减一天工资 = baseSalary / 21.75。
     */
    fun calculateLeaveDeduction(baseSalary: Double, durationHours: Double): Double {
        val absHours = kotlin.math.abs(durationHours)
        if (baseSalary <= 0 || absHours <= 0) return 0.0
        return -calculateOvertimeAmount(baseSalary, OvertimeType.WORKDAY, 1.0, absHours)
    }
    
    fun formatHours(hours: Double): String {
        // 总时长可能为负（请假抵扣后），先取绝对值再拼分钟，
        // 否则 -2.5h 会被拼成「-2小时-30分钟」。
        val sign = if (hours < 0) "-" else ""
        val absHours = kotlin.math.abs(hours)
        val h = absHours.toInt()
        val m = ((absHours - h) * 60).toInt()
        return if (m > 0) "$sign${h}小时${m}分钟" else "$sign${h}小时"
    }
    
    fun formatAmount(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.CHINA)
        return formatter.format(amount)
    }
    
    fun getCurrentYearMonth(): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return sdf.format(Date())
    }
    
    fun getYearMonthList(count: Int): List<String> {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        val list = mutableListOf<String>()
        repeat(count) {
            list.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }
        return list
    }
}
