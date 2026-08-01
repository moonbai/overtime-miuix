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
     */
    suspend fun afterSave(
        context: Context,
        repository: OvertimeRepository,
        settingsRepository: SettingsRepository,
        record: OvertimeRecord,
        oldRecord: OvertimeRecord? = null
    ) {
        // 1) 推送到所选渠道
        if (settingsRepository.pushEnabled.first()) {
            val channel = settingsRepository.pushChannel.first()
            if (channel != "none") {
                PushManager.sendToSelectedChannel(channel, settingsRepository.exportSettingsMap(), record)
            }
        }

        // 2) 日历同步
        if (settingsRepository.calendarSyncEnabled.first() && CalendarSyncManager.hasCalendarPermission(context)) {
            oldRecord?.let { CalendarSyncManager.removeEvents(context, it) }
            CalendarSyncManager.addEvent(context, record)
        }

        // 3) 自动备份（内部会自行检查开关）
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
    }
}
