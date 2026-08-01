package com.overtime.miuix.util

import com.overtime.miuix.data.model.OvertimeType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object SalaryCalculator {
    private const val WORKING_DAYS_PER_MONTH = 21.75
    private const val HOURS_PER_DAY = 8.0
    
    fun calculateDurationHours(startTime: Long, endTime: Long): Double {
        val diff = endTime - startTime
        return (diff / (1000.0 * 60 * 60)).coerceAtLeast(0.0)
    }
    
    fun calculateOvertimeAmount(
        baseSalary: Double,
        type: OvertimeType,
        rate: Double,
        durationHours: Double
    ): Double {
        if (durationHours <= 0) return 0.0
        
        val dailySalary = baseSalary / WORKING_DAYS_PER_MONTH
        val hourlySalary = dailySalary / HOURS_PER_DAY
        
        return hourlySalary * rate * durationHours
    }

    /**
     * 请假工资扣减：按标准日薪比例计算（rate=1.0），以负值表示扣款。
     * 例如请全天(8h)扣减一天工资 = baseSalary / 21.75。
     */
    fun calculateLeaveDeduction(baseSalary: Double, durationHours: Double): Double {
        val absHours = kotlin.math.abs(durationHours)
        if (absHours <= 0) return 0.0
        return -calculateOvertimeAmount(baseSalary, OvertimeType.WORKDAY, 1.0, absHours)
    }
    
    fun formatHours(hours: Double): String {
        val h = hours.toInt()
        val m = ((hours - h) * 60).toInt()
        return if (m > 0) "${h}小时${m}分钟" else "${h}小时"
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
