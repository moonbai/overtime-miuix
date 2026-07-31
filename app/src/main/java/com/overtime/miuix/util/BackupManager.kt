package com.overtime.miuix.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.overtime.miuix.data.database.OvertimeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class BackupData(
    val records: List<OvertimeRecord> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

object BackupManager {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create()

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

            val exportSuccess = exportData(records, settings, localFilePath)
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
