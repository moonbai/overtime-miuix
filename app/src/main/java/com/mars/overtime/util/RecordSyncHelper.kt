package com.overtime.miuix.util

import android.content.Context
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.push.CalendarSyncManager
import com.overtime.miuix.push.PushManager
import kotlinx.coroutines.flow.first

/**
 * 记录保存后的统一同步逻辑：推送、日历同步、自动备份。
 * 供「添加/编辑记录」与「快速提报」共用，确保行为一致。
 */
object RecordSyncHelper {

    /**
     * 在记录保存成功后触发后续同步动作。
     *
     * @param record    已保存的记录（含最终 id）
     * @param oldRecord 编辑场景下的旧记录，用于清理日历中的重复事件；新增时传 null
     * @return 日历同步结果；未开启日历同步时返回 null
     */
    suspend fun afterSave(
        context: Context,
        repository: OvertimeRepository,
        settingsRepository: SettingsRepository,
        record: OvertimeRecord,
        oldRecord: OvertimeRecord? = null
    ): CalendarSyncManager.SyncResult? {
        // 1) 推送到所选渠道。推送属于旁路能力，网络异常等失败不应影响后续同步与保存结果。
        try {
            if (settingsRepository.pushEnabled.first()) {
                val channel = settingsRepository.pushChannel.first()
                if (channel != "none") {
                    PushManager.sendToSelectedChannel(channel, settingsRepository.exportSettingsMap(), record)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2) 日历同步。失败原因回传给调用方，由 UI 给出可操作的提示，
        //    而不是静默吞掉（此前 SecurityException 会直接冒泡导致提交崩溃）。
        var calendarResult: CalendarSyncManager.SyncResult? = null
        if (settingsRepository.calendarSyncEnabled.first()) {
            calendarResult = if (!CalendarSyncManager.hasCalendarPermission(context)) {
                CalendarSyncManager.SyncResult.PermissionDenied
            } else {
                oldRecord?.let { CalendarSyncManager.removeEvents(context, it) }
                CalendarSyncManager.addEvent(context, record)
            }
        }

        // 3) 自动备份（内部会自行检查开关）。同样做兜底，避免备份失败吞掉日历同步结果。
        try {
            val settingsMap = settingsRepository.exportSettingsMap()
            val webdavEnabled = settingsMap["webdav_enabled"]?.toBoolean() ?: false
            val location = settingsMap["auto_backup_location"] ?: "local"
            val webdavConfig = if (webdavEnabled && location == "cloud") {
                WebDavManager.WebDavConfig(
                    baseUrl = settingsMap["webdav_url"] ?: "",
                    username = settingsMap["webdav_username"] ?: "",
                    password = settingsMap["webdav_password"] ?: "",
                    remotePath = settingsMap["webdav_path"] ?: "/overtime_backup/"
                )
            } else null
            val allRecords = repository.getAllRecords().first()
            BackupManager.performAutoBackup(context, allRecords, settingsMap, webdavConfig)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return calendarResult
    }

    /** 将日历同步结果转换为面向用户的提示文案；成功或未开启时返回 null。 */
    fun calendarHint(result: CalendarSyncManager.SyncResult?): String? = when (result) {
        null, is CalendarSyncManager.SyncResult.Success -> null
        is CalendarSyncManager.SyncResult.PermissionDenied -> "记录已保存，但未获得日历权限，未同步到日历"
        is CalendarSyncManager.SyncResult.ProviderRejected -> "记录已保存，但系统拒绝写入日历，请检查日历权限设置"
        is CalendarSyncManager.SyncResult.Failure -> "记录已保存，日历同步失败：${result.message}"
    }
}
