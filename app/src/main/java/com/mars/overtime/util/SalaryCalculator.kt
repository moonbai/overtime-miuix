package com.mars.overtime.util

import com.mars.overtime.data.model.OvertimeType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object SalaryCalculator {
    const val DEFAULT_WORKING_DAYS_PER_MONTH = 21.75
    const val DEFAULT_HOURS_PER_DAY = 8.0

    /**
     * 当前计薪口径，由「基础设置」设置页持久化并由 MainActivity 在设置变化时同步。
     * 用 @Volatile 保证记录保存 / 推送 / MCP 等后台线程读取时可见；configure 做下限
     * 保护，避免非法值导致除零。
     */
    @Volatile
    var workingDaysPerMonth: Double = DEFAULT_WORKING_DAYS_PER_MONTH
        private set

    @Volatile
    var hoursPerDay: Double = DEFAULT_HOURS_PER_DAY
        private set

    fun configure(workingDaysPerMonth: Double, hoursPerDay: Double) {
        this.workingDaysPerMonth = workingDaysPerMonth.coerceAtLeast(0.01)
        this.hoursPerDay = hoursPerDay.coerceAtLeast(0.01)
    }

    /**
     * 小时薪资口径：月基础薪资 / 月计薪天数 / 每日工时。
     * 抽成公共方法，供金额计算、推送内容展示与设置页试算共用，避免公式漂移。
     */
    fun hourlySalary(baseSalary: Double): Double =
        baseSalary / workingDaysPerMonth / hoursPerDay

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
     * 例如按每日工时请满一天，扣减一天工资 = baseSalary / 当前月计薪天数。
     */
    fun calculateLeaveDeduction(baseSalary: Double, durationHours: Double): Double {
        val absHours = kotlin.math.abs(durationHours)
        if (baseSalary <= 0 || absHours <= 0) return 0.0
        return -calculateOvertimeAmount(baseSalary, OvertimeType.WORKDAY, 1.0, absHours)
    }
    
    /**
     * 全局时长显示统一为小时制：0.5H / 1H / 1.5H（整数不带小数点）。
     * 库中请假存负时长用于统计抵扣，这里保留负号、数值取绝对值；
     * 需要正数展示的调用方自行取 abs。
     */
    fun formatHours(hours: Double): String {
        val sign = if (hours < 0) "-" else ""
        val absHours = kotlin.math.abs(hours)
        // 保留两位小数，避免浮点误差打出 1.4999999999 这类长串
        val rounded = kotlin.math.round(absHours * 100) / 100.0
        val number = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        return "$sign${number}H"
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
