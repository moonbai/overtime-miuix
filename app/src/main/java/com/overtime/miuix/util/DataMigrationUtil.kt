package com.overtime.miuix.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataMigrationUtil {
    fun getAppDataDir(context: Context): File {
        val externalDir = context.getExternalFilesDir(null)
        return externalDir ?: File(context.filesDir, "data")
    }

    fun getBackupDir(context: Context): File {
        val dir = File(getAppDataDir(context), "backup")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun generateBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "overtime_backup_$timestamp.json"
    }

    fun getBackupFilePath(context: Context, fileName: String? = null): String {
        val name = fileName ?: generateBackupFileName()
        return File(getBackupDir(context), name).absolutePath
    }

    fun listBackupFiles(context: Context): List<File> {
        val dir = getBackupDir(context)
        return dir.listFiles { file ->
            file.name.endsWith(".json") && file.name.startsWith("overtime_backup_")
        }?.toList() ?: emptyList()
    }
}
