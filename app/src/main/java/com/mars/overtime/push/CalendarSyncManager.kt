package com.overtime.miuix.push

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import java.util.Calendar
import java.util.TimeZone

/**
 * 系统日历同步管理器。
 *
 * 关于 SecurityException: Permission Denial: requires android.permission.WRITE_CALENDAR
 * ------------------------------------------------------------------------------------
 * CalendarProvider 的权限校验发生在 Binder 跨进程调用侧，`checkSelfPermission` 只能反映
 * 调用瞬间的授权快照，存在两类无法靠前置判断消除的失败：
 *   1. 用户在系统设置中撤销权限后，进程未被杀死，旧的授权判断结果仍留在内存里；
 *   2. 部分 ROM（MIUI/HyperOS、ColorOS 等）在权限之上另有隐私管控，即使系统权限已授予，
 *      写入 Calendars/Events 仍可能被拒绝。
 * 因此本类的原则是：前置检查 + 每个 ContentResolver 调用点全量兜底，
 * 任何情况下都以 [SyncResult] 返回失败原因，绝不让 SecurityException 逃逸到调用方。
 */
object CalendarSyncManager {
    private const val TAG = "CalendarSync"
    private const val CALENDAR_DISPLAY_NAME = "加班记"
    private const val ACCOUNT_NAME = "overtime@com.overtime.miuix"
    private const val ACCOUNT_TYPE = "LOCAL"

    /** 同步结果，供 UI 精确提示失败原因，而不是笼统的「同步失败」。 */
    sealed class SyncResult {
        data object Success : SyncResult()

        /** 缺少日历权限（未授予或已被撤销）。 */
        data object PermissionDenied : SyncResult()

        /** 权限已授予但系统/ROM 仍拒绝写入（隐私管控等）。 */
        data object ProviderRejected : SyncResult()

        /** 其他失败（Provider 不可用、数据异常等）。 */
        data class Failure(val message: String) : SyncResult()

        val isSuccess: Boolean get() = this is Success
    }

    /**
     * 判断是否拥有日历读写权限。
     * 始终要求 READ_CALENDAR 与 WRITE_CALENDAR 同时授予：
     * 创建日历账户、写入事件在部分 ROM/系统上仍需 WRITE_CALENDAR，
     * 仅 READ 会在 insert 时抛 SecurityException。
     */
    fun hasCalendarPermission(context: Context): Boolean {
        val readGranted = context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        val writeGranted = context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /** 同步所需的全部日历权限（用于运行时申请）。 */
    fun calendarPermissions(): Array<String> = arrayOf(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR
    )

    /**
     * 查询或创建本应用专属日历。
     *
     * 所有 ContentResolver 调用均已兜底：权限被拒时返回 null 而非抛出异常，
     * 由调用方转换为对用户可见的失败原因。
     */
    fun getOrCreateCalendarId(context: Context): Long? {
        if (!hasCalendarPermission(context)) return null

        val uri = CalendarContract.Calendars.CONTENT_URI

        // 1) 先查已存在的日历账户
        try {
            val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_NAME)
            val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ?"
            val selectionArgs = arrayOf(ACCOUNT_NAME)
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "查询日历账户被拒绝：${e.message}")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "查询日历账户失败：${e.message}")
            return null
        }

        // 2) 不存在则创建。必须以 sync adapter 身份写入 Calendars 表，
        //    且 URI 参数中的 ACCOUNT_NAME/ACCOUNT_TYPE 需与 values 完全一致，
        //    否则 CalendarProvider 会直接拒绝。
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                put(CalendarContract.Calendars.NAME, CALENDAR_DISPLAY_NAME)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4285F4.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            }
            val builderUri = uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build()
            context.contentResolver.insert(builderUri, values)?.lastPathSegment?.toLongOrNull()
        } catch (e: SecurityException) {
            Log.w(TAG, "创建日历账户被拒绝：${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "创建日历账户失败：${e.message}")
            null
        }
    }

    private fun typeStr(type: OvertimeType): String = when (type) {
        OvertimeType.WORKDAY -> "工作日延时"
        OvertimeType.WEEKEND -> "周末加班"
        OvertimeType.HOLIDAY -> "节假日加班"
    }

    /** 以记录当天 18:00 作为日历事件起点，保证新增与删除的匹配口径一致。 */
    private fun eventStartTime(record: OvertimeRecord): Long =
        Calendar.getInstance().apply {
            timeInMillis = record.date
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun eventTitle(record: OvertimeRecord): String =
        "${typeStr(record.type)}-${"%.2f".format(record.durationHours)}小时"

    /** 写入单条记录到系统日历。 */
    fun addEvent(context: Context, record: OvertimeRecord): SyncResult {
        if (!hasCalendarPermission(context)) return SyncResult.PermissionDenied
        val calendarId = getOrCreateCalendarId(context)
            ?: return if (hasCalendarPermission(context)) {
                SyncResult.ProviderRejected
            } else {
                SyncResult.PermissionDenied
            }

        val startTime = eventStartTime(record)
        // 请假记录时长为负值，直接相加会得到早于起点的结束时间，
        // CalendarProvider 会因 DTEND < DTSTART 拒绝写入，故统一取绝对值。
        val durationMillis = (kotlin.math.abs(record.durationHours) * 3600_000L).toLong()
        val endTime = startTime + durationMillis

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
            put(CalendarContract.Events.TITLE, eventTitle(record))
            put(CalendarContract.Events.DESCRIPTION, "金额: ¥${"%.2f".format(record.amount)}\n事由: ${record.note}")
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }

        return try {
            val inserted = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (inserted != null) SyncResult.Success else SyncResult.ProviderRejected
        } catch (e: SecurityException) {
            // 权限在运行期被撤销，或 ROM 隐私管控拦截
            Log.w(TAG, "写入日历事件被拒绝：${e.message}")
            if (hasCalendarPermission(context)) SyncResult.ProviderRejected else SyncResult.PermissionDenied
        } catch (e: Exception) {
            Log.w(TAG, "写入日历事件失败：${e.message}")
            SyncResult.Failure(e.message ?: "未知错误")
        }
    }

    /** 删除与该记录匹配的日历事件。 */
    fun removeEvents(context: Context, record: OvertimeRecord): SyncResult {
        if (!hasCalendarPermission(context)) return SyncResult.PermissionDenied
        val calendarId = getOrCreateCalendarId(context) ?: return SyncResult.ProviderRejected

        val startTime = eventStartTime(record)
        val title = eventTitle(record)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
            "${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ?"
        val selectionArgs = arrayOf(calendarId.toString(), title, startTime.toString())

        return try {
            context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs)
            SyncResult.Success
        } catch (e: SecurityException) {
            Log.w(TAG, "删除日历事件被拒绝：${e.message}")
            if (hasCalendarPermission(context)) SyncResult.ProviderRejected else SyncResult.PermissionDenied
        } catch (e: Exception) {
            Log.w(TAG, "删除日历事件失败：${e.message}")
            SyncResult.Failure(e.message ?: "未知错误")
        }
    }

    /** 同步全部记录到系统日历（先清空本应用日历中的旧事件再写入）。 */
    fun syncAll(context: Context, records: List<OvertimeRecord>): SyncResult {
        if (!hasCalendarPermission(context)) return SyncResult.PermissionDenied
        val calendarId = getOrCreateCalendarId(context) ?: return SyncResult.ProviderRejected

        try {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.CALENDAR_ID} = ?",
                arrayOf(calendarId.toString())
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "清空旧日历事件被拒绝：${e.message}")
            return if (hasCalendarPermission(context)) SyncResult.ProviderRejected else SyncResult.PermissionDenied
        } catch (e: Exception) {
            Log.w(TAG, "清空旧日历事件失败：${e.message}")
            return SyncResult.Failure(e.message ?: "未知错误")
        }

        // 单条失败不应中断整体同步，逐条写入并收敛最终结果
        var firstFailure: SyncResult? = null
        for (record in records) {
            val result = addEvent(context, record)
            if (!result.isSuccess) {
                // 权限类失败属于全局性问题，立即中止避免无谓重试
                if (result is SyncResult.PermissionDenied) return result
                if (firstFailure == null) firstFailure = result
            }
        }
        return firstFailure ?: SyncResult.Success
    }
}
