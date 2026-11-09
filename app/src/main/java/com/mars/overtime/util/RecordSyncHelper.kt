package com.mars.overtime.util

import android.content.Context
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.push.CalendarSyncManager
import com.mars.overtime.push.PushManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
     * @return 需要逐项提示的联动失败短句；全部成功或未开启时返回空列表
     */
    suspend fun afterSave(
        context: Context,
        repository: OvertimeRepository,
        settingsRepository: SettingsRepository,
        record: OvertimeRecord,
        oldRecord: OvertimeRecord? = null
    ): List<String> {
        val failures = mutableListOf<String>()
        // 以下三类动作都涉及 ContentResolver / 文件 / 网络 IO，统一切到 IO 线程，
        // 避免保存记录时在主线程做磁盘或 Provider 操作造成掉帧。

        // 1) 推送到所选渠道。旁路能力失败不影响保存结果，但必须回传给 UI 提示。
        try {
            withContext(Dispatchers.IO) {
                if (settingsRepository.pushEnabled.first()) {
                    val channel = settingsRepository.pushChannel.first()
                    if (channel != "none") {
                        val ok = PushManager.sendToSelectedChannel(
                            channel, settingsRepository.exportSettingsMap(), record
                        )
                        if (!ok) failures += "推送未成功，请检查推送配置"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            failures += "推送未成功：${e.message ?: "未知错误"}"
        }

        // 2) 日历同步。失败原因回传给调用方，由 UI 给出可操作的提示，
        //    而不是静默吞掉（此前 SecurityException 会直接冒泡导致提交崩溃）。
        var calendarResult: CalendarSyncManager.SyncResult? = null
        if (settingsRepository.calendarSyncEnabled.first()) {
            calendarResult = withContext(Dispatchers.IO) {
                if (!CalendarSyncManager.hasCalendarPermission(context)) {
                    CalendarSyncManager.SyncResult.PermissionDenied
                } else {
                    oldRecord?.let { CalendarSyncManager.removeEvents(context, it) }
                    CalendarSyncManager.addEvent(context, record)
                }
            }
        }
        calendarFailure(calendarResult)?.let { failures += it }

        // 3) 自动备份（内部会自行检查开关）。
        try {
            withContext(Dispatchers.IO) {
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
        } catch (e: Exception) {
            e.printStackTrace()
            failures += "自动备份未成功：${e.message ?: "未知错误"}"
        }

        return failures
    }

    /** 把基础成功文案与逐项失败提示拼成一条完整提示。 */
    fun formatHint(baseMessage: String, failures: List<String>): String =
        if (failures.isEmpty()) baseMessage
        else "$baseMessage（${failures.joinToString("；")}）"

    private fun calendarFailure(result: CalendarSyncManager.SyncResult?): String? = when (result) {
        null, is CalendarSyncManager.SyncResult.Success -> null
        is CalendarSyncManager.SyncResult.PermissionDenied -> "未获得日历权限，未同步到日历"
        is CalendarSyncManager.SyncResult.ProviderRejected -> "系统拒绝写入日历，请检查权限设置"
        is CalendarSyncManager.SyncResult.Failure -> "日历同步失败：${result.message}"
    }
}
