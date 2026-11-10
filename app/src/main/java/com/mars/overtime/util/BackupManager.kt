package com.mars.overtime.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.security.BackupEnvelope
import com.mars.overtime.security.BackupManifest
import com.mars.overtime.security.BackupReadResult
import com.mars.overtime.security.BackupSession
import com.mars.overtime.security.ENTRY_MANIFEST
import com.mars.overtime.security.ENTRY_PAYLOAD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupData(
    val records: List<OvertimeRecord> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

/** 自动备份结果：让调用方能区分「未开启」「缺会话密钥」「真失败」，而不是一个布尔值。 */
sealed interface AutoBackupResult {
    data object Success : AutoBackupResult
    data object Disabled : AutoBackupResult
    /** 已开启备份加密但进程内没有会话密钥：跳过，绝不降级为明文。 */
    data object NeedBackupPassword : AutoBackupResult
    data class Failed(val reason: String) : AutoBackupResult
}

object BackupManager {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create()

    // ========== 明文 ZIP 导入导出（records.json + settings.json） ==========

    /**
     * 将记录和设置分别序列化为 records.json / settings.json，打包成 ZIP 写入指定 OutputStream。
     * 仅在「备份加密」关闭时使用；开启后一律走 [exportEncryptedZip]。
     */
    fun exportZip(records: List<OvertimeRecord>, settings: Map<String, String>, outputStream: OutputStream): Boolean {
        return try {
            ZipOutputStream(outputStream).use { zos ->
                zos.putNextEntry(ZipEntry("records.json"))
                zos.write(gson.toJson(records).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("settings.json"))
                zos.write(gson.toJson(settings).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun exportZip(records: List<OvertimeRecord>, settings: Map<String, String>, zipPath: String): Boolean {
        return try {
            exportZip(records, settings, FileOutputStream(zipPath))
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 旧版明文 ZIP 解析（保留兼容）；新代码统一走 [readBackup]。 */
    fun importZip(zipPath: String): BackupData? {
        val result = readBackup(zipPath)
        return (result as? BackupReadResult.Plain)?.data
    }

    // ========== 加密 ZIP 容器（manifest.json + payload.bin） ==========

    /**
     * 导出加密备份。
     * @param master 与 [salt] 成对的主密钥，来自 [BackupSession] 或口令派生结果
     * @param salt   与备份口令绑定的固定 KDF salt，明文写入 manifest 供导入方派生
     */
    fun exportEncryptedZip(
        records: List<OvertimeRecord>,
        settings: Map<String, String>,
        outputStream: OutputStream,
        master: ByteArray,
        salt: ByteArray
    ): Boolean = try {
        val (manifest, payload) = BackupEnvelope.encrypt(BackupData(records, settings), master, salt)
        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zos.write(BackupEnvelope.manifestToJson(manifest).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry(ENTRY_PAYLOAD))
            zos.write(payload)
            zos.closeEntry()
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    fun exportEncryptedZip(
        records: List<OvertimeRecord>,
        settings: Map<String, String>,
        zipPath: String,
        master: ByteArray,
        salt: ByteArray
    ): Boolean = try {
        exportEncryptedZip(records, settings, FileOutputStream(zipPath), master, salt)
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /**
     * 统一识别 ZIP 格式：存在合法 manifest.json → 加密包；存在 records.json → 旧版明文包；
     * 其余（含「manifest 非法但带 payload」）一律 Invalid，杜绝任何形式的自动降级到明文。
     */
    fun readBackup(input: InputStream): BackupReadResult = try {
        var manifest: BackupManifest? = null
        var payload: ByteArray? = null
        var records: List<OvertimeRecord>? = null
        var settings: Map<String, String>? = null

        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    ENTRY_MANIFEST -> {
                        manifest = BackupEnvelope.parseManifest(
                            String(zis.readBytes(), Charsets.UTF_8)
                        )
                    }
                    ENTRY_PAYLOAD -> payload = zis.readBytes()
                    "records.json" -> {
                        val json = String(zis.readBytes(), Charsets.UTF_8)
                        records = gson.fromJson(json, Array<OvertimeRecord>::class.java)?.toList()
                    }
                    "settings.json" -> {
                        val json = String(zis.readBytes(), Charsets.UTF_8)
                        @Suppress("UNCHECKED_CAST")
                        settings = gson.fromJson(json, Map::class.java) as? Map<String, String>
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val safeManifest = manifest
        val safePayload = payload
        val safeRecords = records
        when {
            safeManifest != null && safePayload != null ->
                BackupReadResult.Encrypted(safeManifest, safePayload)
            safeRecords != null ->
                BackupReadResult.Plain(BackupData(safeRecords, settings ?: emptyMap()))
            else -> BackupReadResult.Invalid
        }
    } catch (e: Exception) {
        e.printStackTrace()
        BackupReadResult.Invalid
    }

    fun readBackup(zipPath: String): BackupReadResult = try {
        FileInputStream(zipPath).use { readBackup(it) }
    } catch (e: Exception) {
        e.printStackTrace()
        BackupReadResult.Invalid
    }

    // ========== 旧版 JSON 兼容（保留，用于向后兼容旧格式备份文件） ==========

    fun exportData(records: List<OvertimeRecord>, settings: Map<String, String>, filePath: String): Boolean {
        return try {
            val json = gson.toJson(BackupData(records, settings))
            FileWriter(File(filePath)).use { it.write(json) }
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
            gson.fromJson(FileReader(file), BackupData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun serialize(records: List<OvertimeRecord>, settings: Map<String, String>): String =
        gson.toJson(BackupData(records, settings))

    fun deserialize(json: String): BackupData? {
        return try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ========== 自动备份 ==========

    /**
     * @param encryptionEnabled 来自设置仓库的「备份加密」开关。安全键刻意不进 [exportSettingsMap]
     *                          白名单，所以必须由调用方显式传入，不能从 settings map 里读。
     */
    suspend fun performAutoBackup(
        context: Context,
        records: List<OvertimeRecord>,
        settings: Map<String, String>,
        webdavConfig: WebDavManager.WebDavConfig? = null,
        encryptionEnabled: Boolean = false
    ): AutoBackupResult = withContext(Dispatchers.IO) {
        try {
            val autoBackupEnabled = settings["auto_backup_enabled"]?.toBoolean() ?: false
            if (!autoBackupEnabled) return@withContext AutoBackupResult.Disabled

            // 开启加密但进程内没有会话密钥：跳过本次备份，绝不写出明文文件。
            val session = if (encryptionEnabled) BackupSession.snapshot() else null
            if (encryptionEnabled && session == null) {
                return@withContext AutoBackupResult.NeedBackupPassword
            }

            val location = settings["auto_backup_location"] ?: "local"
            val fileName = DataMigrationUtil.generateBackupFileName()
            val localFilePath = DataMigrationUtil.getBackupFilePath(context, fileName)

            val exportSuccess = try {
                if (session != null) {
                    exportEncryptedZip(records, settings, localFilePath, session.master, session.salt)
                } else {
                    exportZip(records, settings, localFilePath)
                }
            } finally {
                session?.wipe()
            }
            if (!exportSuccess) {
                return@withContext AutoBackupResult.Failed("备份文件生成失败")
            }

            if (location == "cloud" && webdavConfig != null) {
                WebDavManager.uploadFile(webdavConfig, localFilePath, fileName)
            }
            AutoBackupResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            AutoBackupResult.Failed(e.message ?: "未知错误")
        }
    }
}
