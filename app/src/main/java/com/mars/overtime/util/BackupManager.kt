package com.overtime.miuix.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.overtime.miuix.data.database.OvertimeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupData(
    val records: List<OvertimeRecord> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

object BackupManager {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create()

    // ========== ZIP 导入导出（拆分为 records.json + settings.json） ==========

    /**
     * 将记录和设置分别序列化为 records.json / settings.json，打包成 ZIP 写入指定 OutputStream。
     */
    fun exportZip(records: List<OvertimeRecord>, settings: Map<String, String>, outputStream: OutputStream): Boolean {
        return try {
            ZipOutputStream(outputStream).use { zos ->
                // records.json
                zos.putNextEntry(ZipEntry("records.json"))
                val recordsJson = gson.toJson(records)
                zos.write(recordsJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // settings.json
                zos.putNextEntry(ZipEntry("settings.json"))
                val settingsJson = gson.toJson(settings)
                zos.write(settingsJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 将记录和设置分别序列化为 records.json / settings.json，打包成 ZIP 写入指定路径。
     */
    fun exportZip(records: List<OvertimeRecord>, settings: Map<String, String>, zipPath: String): Boolean {
        return try {
            exportZip(records, settings, FileOutputStream(zipPath))
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从 ZIP 文件中读取 records.json 和 settings.json，返回 BackupData。
     */
    fun importZip(zipPath: String): BackupData? {
        return try {
            var records: List<OvertimeRecord>? = null
            var settings: Map<String, String>? = null

            ZipInputStream(FileInputStream(zipPath)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val content = zis.readBytes()
                    val json = String(content, Charsets.UTF_8)
                    when (entry.name) {
                        "records.json" -> {
                            records = gson.fromJson(json, Array<OvertimeRecord>::class.java)?.toList()
                        }
                        "settings.json" -> {
                            @Suppress("UNCHECKED_CAST")
                            settings = gson.fromJson(json, Map::class.java) as? Map<String, String>
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (records != null) BackupData(records!!, settings ?: emptyMap()) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ========== 旧版 JSON 兼容（保留，用于向后兼容旧格式备份文件） ==========

    fun exportData(records: List<OvertimeRecord>, settings: Map<String, String>, filePath: String): Boolean {
        return try {
            val data = BackupData(records, settings)
            val json = gson.toJson(data)
            val file = File(filePath)
            FileWriter(file).use { writer ->
                writer.write(json)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importData(filePath: String): BackupData? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val reader = FileReader(file)
            gson.fromJson(reader, BackupData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun serialize(records: List<OvertimeRecord>, settings: Map<String, String>): String {
        return gson.toJson(BackupData(records, settings))
    }

    fun deserialize(json: String): BackupData? {
        return try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ========== 自动备份 ==========

    suspend fun performAutoBackup(
        context: Context,
        records: List<OvertimeRecord>,
        settings: Map<String, String>,
        webdavConfig: WebDavManager.WebDavConfig? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val autoBackupEnabled = settings["auto_backup_enabled"]?.toBoolean() ?: false
            if (!autoBackupEnabled) {
                return@withContext true
            }

            val location = settings["auto_backup_location"] ?: "local"
            val fileName = DataMigrationUtil.generateBackupFileName()
            val localFilePath = DataMigrationUtil.getBackupFilePath(context, fileName)

            val exportSuccess = exportZip(records, settings, localFilePath)
            if (!exportSuccess) {
                return@withContext false
            }

            if (location == "cloud" && webdavConfig != null) {
                WebDavManager.uploadFile(webdavConfig, localFilePath, fileName)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
