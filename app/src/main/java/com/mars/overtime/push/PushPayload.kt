package com.mars.overtime.push

import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.util.HolidayManager
import com.mars.overtime.util.SalaryCalculator
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 推送内容的统一载荷：所有渠道共用同一份字段，避免各渠道消息内容不一致。
 *
 * 字段来源严格限于现有数据模型，不做臆测：
 * - 记录本身：[OvertimeRecord.date] / type / durationHours / baseSalary / rate / amount / note / isLeave
 * - 派生：星期、年月、当天类型、小时薪资、记录模式、时间戳
 * - 数据模型中没有「调休标记」「记录创建时间」：
 *   · 调休由节假日缓存的日期名称推断，缓存无该日期数据时为 null（未知），不臆测为 false；
 *   · 时间戳取推送发动时刻，而非记录创建时刻（后者需要 Room 加列并做迁移）。
 * - 数据模型中没有请假类别，因此不输出「请假类型」字段；请假记录通过「记录模式」区分。
 *
 * 文本渠道使用 [toText]，自定义 WebHook 使用 [toJson]（结构化字段 + text 纯文本副本）。
 */
data class PushPayload(
    val date: String,
    val weekday: String,
    val yearMonth: String,
    val dayType: String,
    val isMakeUpWork: Boolean?,
    val hours: Double,
    val hourlySalary: Double,
    val amount: Double,
    val rate: Double,
    val recordMode: String,
    val note: String,
    val timestamp: String
) {
    companion object {
        /** 数值统一格式：最多两位小数、去掉尾随 0（5.00→5，13.80→13.8） */
        private fun num(value: Double): String =
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }

        fun from(record: OvertimeRecord, now: Date = Date()): PushPayload {
            val locale = Locale.CHINA
            val recordDate = Date(record.date)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", locale).format(recordDate)
            val dayInfo = HolidayManager.getDayInfo(dateStr)

            return PushPayload(
                date = dateStr,
                weekday = SimpleDateFormat("EEEE", locale).format(recordDate),
                yearMonth = SimpleDateFormat("yyyy-MM", locale).format(recordDate),
                dayType = when (record.type) {
                    OvertimeType.WORKDAY -> "工作日"
                    OvertimeType.WEEKEND -> "休息日"
                    OvertimeType.HOLIDAY -> "节假日"
                },
                // 仅在节假日缓存明确标注「调休」时判定；无缓存视为未知，不臆测为 false
                isMakeUpWork = dayInfo?.name?.contains("调休"),
                // 库中请假存负时长（用于统计抵扣），但对外的时长字段一律为正值，
                // 请假与加班由 recordMode 区分，避免 webhook 收到 -4 / -8 这类内部表示。
                hours = if (record.isLeave) {
                    kotlin.math.abs(record.durationHours)
                } else {
                    record.durationHours
                },
                hourlySalary = SalaryCalculator.hourlySalary(record.baseSalary),
                amount = record.amount,
                rate = record.rate,
                recordMode = if (record.isLeave) "请假" else "加班",
                note = record.note,
                timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", locale).format(now)
            )
        }
    }

    /** 面向钉钉 / 飞书 / 企业微信 / WxPusher / Telegram / Discord 的可读文本 */
    fun toText(): String = buildString {
        append("日期：").append(date).append('\n')
        append("星期：").append(weekday).append('\n')
        append("年月：").append(yearMonth).append('\n')
        append("当天类型：").append(dayType).append('\n')
        append("是否调休：").append(
            when (isMakeUpWork) {
                true -> "是"
                false -> "否"
                null -> "未知"
            }
        ).append('\n')
        append(if (recordMode == "请假") "请假时长：" else "加班时长：")
            .append(SalaryCalculator.formatHours(hours)).append('\n')
        append("小时薪资：").append(num(hourlySalary)).append('\n')
        append("加班费：").append(num(amount)).append('\n')
        append("倍数：").append(num(rate)).append('\n')
        append("记录模式：").append(recordMode).append('\n')
        append("备注：").append(note).append('\n')
        append("时间戳：").append(timestamp)
    }

    /** 面向自定义 WebHook 的结构化 JSON，同时附带完整可读文本 */
    fun toJson(): String = JSONObject().apply {
        put("date", date)
        put("weekday", weekday)
        put("year_month", yearMonth)
        put("day_type", dayType)
        put("is_make_up_work", isMakeUpWork ?: JSONObject.NULL)
        put("hours", hours)
        put("hourly_salary", hourlySalary)
        put("amount", amount)
        put("rate", rate)
        put("record_mode", recordMode)
        put("note", note)
        put("timestamp", timestamp)
        put("text", toText())
    }.toString()
}
