package com.overtime.miuix.push

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import java.util.TimeZone

object CalendarSyncManager {
    private const val CALENDAR_DISPLAY_NAME = "加班记"
    private const val ACCOUNT_NAME = "overtime@com.overtime.miuix"
    private const val ACCOUNT_TYPE = "LOCAL"

    /**
     * 判断是否拥有日历读写权限。
     * Android 11（API 30）及以上，拥有 [READ_CALENDAR] 即可写入自身拥有的日历；
     * Android 11 以下还需显式授予 [WRITE_CALENDAR]。
     */
    fun hasCalendarPermission(context: Context): Boolean {
        val readGranted = context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return readGranted
        }
        val writeGranted = context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /** 同步所需的全部日历权限（用于运行时申请）。 */
    fun calendarPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(android.Manifest.permission.READ_CALENDAR)
        } else {
            arrayOf(
                android.Manifest.permission.READ_CALENDAR,
                android.Manifest.permission.WRITE_CALENDAR
            )
        }
    }

    fun getOrCreateCalendarId(context: Context): Long? {
        if (!hasCalendarPermission(context)) return null

        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_NAME)
        val uri = CalendarContract.Calendars.CONTENT_URI
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ?"
        val selectionArgs = arrayOf(ACCOUNT_NAME)

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            }
        }

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

        val result = context.contentResolver.insert(builderUri, values)
        return result?.lastPathSegment?.toLongOrNull()
    }

    private fun typeStr(type: OvertimeType): String = when (type) {
        OvertimeType.WORKDAY -> "工作日延时"
        OvertimeType.WEEKEND -> "周末加班"
        OvertimeType.HOLIDAY -> "节假日加班"
    }

    fun addEvent(context: Context, record: OvertimeRecord): Boolean {
        val calendarId = getOrCreateCalendarId(context) ?: return false

        val typeLabel = typeStr(record.type)
        val startTime = record.startTime
        val endTime = record.endTime

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
            put(CalendarContract.Events.TITLE, "$typeLabel-${"%.2f".format(record.durationHours)}小时")
            put(CalendarContract.Events.DESCRIPTION, "金额: ¥${"%.2f".format(record.amount)}\n事由: ${record.note}")
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }

        return try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun removeEvents(context: Context, record: OvertimeRecord): Boolean {
        val calendarId = getOrCreateCalendarId(context) ?: return false

        val typeLabel = typeStr(record.type)
        val startTime = record.startTime
        val title = "$typeLabel-${"%.2f".format(record.durationHours)}小时"

        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ?"
        val selectionArgs = arrayOf(calendarId.toString(), title, startTime.toString())

        return try {
            context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs) > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 同步全部记录到系统日历（先清空本应用日历中的旧事件再写入）
     */
    fun syncAll(context: Context, records: List<OvertimeRecord>): Boolean {
        val calendarId = getOrCreateCalendarId(context) ?: return false
        return try {
            val deleteSelection = "${CalendarContract.Events.CALENDAR_ID} = ?"
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                deleteSelection,
                arrayOf(calendarId.toString())
            )
            records.forEach { addEvent(context, it) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
