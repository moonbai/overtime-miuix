package com.mars.overtime.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备份文件的来源目录：App 自动备份目录，或用户通过系统目录选择器指定的自定义目录 */
enum class BackupSource(val label: String) {
    AUTO("自动备份目录"),
    CUSTOM("自定义目录")
}

/** 导入列表中一条备份文件。uri 可能是 file://（自动备份）或 content://（自定义目录 SAF）。 */
data class BackupFileEntry(
    val name: String,
    val uri: Uri,
    val source: BackupSource,
    val sizeBytes: Long,
    val lastModified: Long
) {
    val info: String get() = "${formatBackupSize(sizeBytes)} · ${formatBackupTime(lastModified)}"
}

fun formatBackupSize(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024f / 1024f)
}

fun formatBackupTime(millis: Long): String =
    if (millis <= 0L) "时间未知"
    else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/**
 * 备份文件的目录枚举与统一解析。
 *
 * 自动备份写在应用专属外部目录（Android/data/...），普通文件选择器难以直接访问，
 * 因此导入时提供「自动备份目录」与「自定义目录」两个来源，让写出去的文件能读回来。
 */
object BackupSources {

    /** 自动备份目录里的 ZIP / JSON，按修改时间倒序 */
    fun autoFiles(context: Context, limit: Int = 30): List<BackupFileEntry> =
        DataMigrationUtil.listBackupFiles(context)
            .map { file ->
                BackupFileEntry(
                    name = file.name,
                    uri = Uri.fromFile(file),
                    source = BackupSource.AUTO,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
            .sortedByDescending { it.lastModified }
            .take(limit)

    /**
     * 自定义目录（SAF 目录树 URI）里的 ZIP / JSON。
     * 目录权限丢失、URI 失效或 provider 不支持查询时返回空列表，由 UI 提示重新选择目录。
     */
    fun customFiles(context: Context, treeUriString: String, limit: Int = 30): List<BackupFileEntry> {
        if (treeUriString.isBlank()) return emptyList()
        return try {
            val treeUri = Uri.parse(treeUriString)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            val result = mutableListOf<BackupFileEntry>()
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val timeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    if (name.isNullOrBlank() ||
                        !(name.endsWith(".zip", true) || name.endsWith(".json", true))
                    ) continue
                    val docId = (if (idIdx >= 0) cursor.getString(idIdx) else null) ?: continue
                    if (docId.isBlank()) continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val time = if (timeIdx >= 0 && !cursor.isNull(timeIdx)) cursor.getLong(timeIdx) else 0L
                    result.add(BackupFileEntry(name, docUri, BackupSource.CUSTOM, size, time))
                }
            }
            result.sortedByDescending { it.lastModified }.take(limit)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** 目录树 URI 的友好显示名（取 document id 末段） */
    fun customDirLabel(treeUriString: String): String {
        if (treeUriString.isBlank()) return "未设置"
        return try {
            val id = DocumentsContract.getTreeDocumentId(Uri.parse(treeUriString))
            val tail = id.substringAfterLast(':', id).trimEnd('/')
            tail.substringAfterLast('/').ifBlank { tail }.ifBlank { "已设置目录" }
        } catch (e: Exception) {
            "已设置目录"
        }
    }

    /** 检查是否仍持有该目录的持久化读权限（重新安装/清除数据后会丢失） */
    fun hasReadPermission(context: Context, treeUriString: String): Boolean {
        if (treeUriString.isBlank()) return false
        return try {
            val target = Uri.parse(treeUriString)
            context.contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == target && permission.isReadPermission
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 统一解析：file:// 直接按路径读（避免跨进程暴露 file Uri），
     * content:// 的 ZIP 先落到 cache 再交给 [BackupManager]，JSON 直接读文本。
     */
    fun parse(context: Context, entry: BackupFileEntry): BackupData? = try {
        val isZip = entry.name.endsWith(".zip", true)
        if ("file" == entry.uri.scheme) {
            val path = entry.uri.path
            if (path == null) null
            else if (isZip) BackupManager.importZip(path) else BackupManager.importData(path)
        } else if (isZip) {
            // 与「导入数据」直接选文件时的临时文件错开，避免连续操作互相覆盖
            val tmp = File(context.cacheDir, "import_dir_tmp.zip")
            val ok = context.contentResolver.openInputStream(entry.uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!ok) {
                null
            } else {
                val data = BackupManager.importZip(tmp.absolutePath)
                tmp.delete()
                data
            }
        } else {
            val json = context.contentResolver.openInputStream(entry.uri)
                ?.bufferedReader()?.use { it.readText() }
            json?.let { BackupManager.deserialize(it) }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
