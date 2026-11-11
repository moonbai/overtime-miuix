package com.mars.overtime.util

import java.util.Calendar

/**
 * 按「工作日下班时刻」把当前时刻折算成加班时长。
 *
 * 规则（与产品确认）：
 * - 未配置下班时刻（[UNSET]）或当前尚未完成第一个半小时 → 返回 null，
 *   由调用方保留原默认值 2.0h，不虚报 0h、也不在下班前自动填数；
 * - 只按**已完成的**半小时计（向下取整）：下班后 30~59 分钟 = 0.5h，
 *   60~89 分钟 = 1.0h，17:00 下班、17:40 即 0.5h；
 * - 结果始终落在 0.5h 档位上，且不超过当日类型允许的时长上限；
 * - 不跨天：当前时刻早于下班时刻时 elapsed 为负，直接返回 null。
 *
 * 纯 JVM 实现，不依赖 Android 框架，便于边界用例直接跑验证。
 */
object OvertimeDurationEstimator {
    /** 未配置下班时刻。 */
    const val UNSET = -1

    const val MAX_WORKDAY_HOURS = 8.0

    /** @return 估算时长（0.5h 档）；不适用时返回 null。 */
    fun estimate(
        endMinutes: Int,
        nowMinutes: Int,
        maxHours: Double = MAX_WORKDAY_HOURS
    ): Double? {
        if (endMinutes !in 0..1439 || nowMinutes !in 0..1439) return null
        if (maxHours < 0.5) return null
        val elapsed = nowMinutes - endMinutes
        if (elapsed < 30) return null
        // 整数除法 = 向下取整到已完成的半小时档
        val hours = (elapsed / 30) * 0.5
        return hours.coerceAtMost(maxHours).coerceAtLeast(0.5)
    }

    /** 当前时刻距零点的分钟数。 */
    fun nowMinutes(calendar: Calendar = Calendar.getInstance()): Int =
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

    /** 解析 "HH:mm"，空串视为未配置；非法格式返回 null。 */
    fun parseEndMinutes(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return UNSET
        val parts = trimmed.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** 分钟数还原为 "HH:mm"；未配置返回空串。 */
    fun formatEndMinutes(minutes: Int): String =
        if (minutes in 0..1439) "%02d:%02d".format(minutes / 60, minutes % 60) else ""
}
