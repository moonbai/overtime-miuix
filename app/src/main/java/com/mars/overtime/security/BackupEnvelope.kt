package com.mars.overtime.security

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.util.BackupData
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException

/**
 * 加密备份容器（v1）。
 *
 * 沿用 `.zip` 外壳以便复用现有 SAF 导出与 `.zip` 后缀过滤：
 * ```
 * overtime_backup_YYYYMMDD_HHMMSS.zip
 *  ├─ manifest.json   明文元数据（不含任何密钥）
 *  └─ payload.bin     AES-256-GCM 密文，明文 = BackupData 的 JSON
 * ```
 * 旧版明文 ZIP（records.json + settings.json）与旧版单文件 JSON 继续可导入，
 * 通过「ZIP 内是否存在 manifest.json」区分，杜绝任何形式的自动降级到明文。
 */
const val BACKUP_MAGIC = "OTBK"
const val BACKUP_VERSION = 1
const val ENTRY_MANIFEST = "manifest.json"
const val ENTRY_PAYLOAD = "payload.bin"

data class BackupManifest(
    val magic: String = BACKUP_MAGIC,
    val version: Int = BACKUP_VERSION,
    val kdf: String = PasswordCrypto.KDF,
    val iterations: Int = PasswordCrypto.DEFAULT_ITERATIONS,
    val salt: String = "",
    val iv: String = "",
    val verifier: String = "",
    val cipher: String = PasswordCrypto.CIPHER
) {
    /**
     * AAD 覆盖所有影响解密的字段并按固定顺序拼接：改 magic / version / kdf /
     * iterations / salt / iv / cipher 任一字节都会让 GCM 校验失败，
     * 从而堵死「把加密标记改成不加密」的降级攻击。
     */
    fun aadBytes(): ByteArray = listOf(
        magic, version.toString(), kdf, iterations.toString(),
        salt, iv, verifier, cipher
    ).joinToString("|").toByteArray(StandardCharsets.UTF_8)

    fun isValid(): Boolean =
        magic == BACKUP_MAGIC &&
            version == BACKUP_VERSION &&
            kdf == PasswordCrypto.KDF &&
            cipher == PasswordCrypto.CIPHER &&
            iterations in PasswordCrypto.MIN_ITERATIONS..PasswordCrypto.MAX_ITERATIONS &&
            salt.isNotBlank() && iv.isNotBlank() && verifier.isNotBlank()
}

/** 读取备份文件的结果：明文 / 加密待解密 / 无效。 */
sealed interface BackupReadResult {
    data class Plain(val data: BackupData) : BackupReadResult
    data class Encrypted(val manifest: BackupManifest, val payload: ByteArray) : BackupReadResult
    data object Invalid : BackupReadResult
}

/** 解密结果：区分「口令不正确」与「文件损坏或被篡改」。 */
sealed interface BackupDecryptResult {
    data class Success(val data: BackupData) : BackupDecryptResult
    data object WrongPassword : BackupDecryptResult
    data object Corrupted : BackupDecryptResult
}

object BackupEnvelope {
    private val gson = Gson()

    fun manifestToJson(manifest: BackupManifest): String = gson.toJson(manifest)

    fun parseManifest(json: String): BackupManifest? = try {
        gson.fromJson(json, BackupManifest::class.java)?.takeIf { it.isValid() }
    } catch (_: JsonSyntaxException) {
        null
    } catch (_: Exception) {
        null
    }

    /**
     * 用主密钥加密一份备份数据。
     *
     * @param master 由 [PasswordCrypto.deriveMaster] 从「备份口令 + [salt]」派生的主密钥，
     *               与 [BackupSession] 缓存的是同一个值。
     * @param salt   **与备份口令绑定的固定 KDF salt**（设置口令时生成并持久化），不是每次导出随机值。
     *               它会明文写进 manifest，导入方才能用「口令 + manifest.salt」派生出相同 master。
     *               每次导出的随机性由 [iv] 提供，与 salt 无关。
     */
    fun encrypt(
        data: BackupData,
        master: ByteArray,
        salt: ByteArray
    ): Pair<BackupManifest, ByteArray> {
        val iv = PasswordCrypto.randomBytes(PasswordCrypto.IV_BYTES)
        val manifest = BackupManifest(
            iterations = PasswordCrypto.DEFAULT_ITERATIONS,
            salt = PasswordCrypto.b64(salt),
            iv = PasswordCrypto.b64(iv),
            verifier = PasswordCrypto.b64(PasswordCrypto.verifier(master))
        )
        val plain = gson.toJson(data).toByteArray(StandardCharsets.UTF_8)
        val ciphertext = PasswordCrypto.encrypt(
            plain,
            PasswordCrypto.backupEncKey(master),
            iv,
            manifest.aadBytes()
        )
        return manifest to ciphertext
    }

    /**
     * 用口令解密 [BackupReadResult.Encrypted]。口令以 CharArray 传入，用完由调用方清零。
     * 派生与解密均为 CPU/IO 操作，调用方应切到 Dispatchers.IO。
     */
    fun decrypt(encrypted: BackupReadResult.Encrypted, password: CharArray): BackupDecryptResult {
        val manifest = encrypted.manifest
        val salt = try {
            PasswordCrypto.unb64(manifest.salt)
        } catch (_: IllegalArgumentException) {
            return BackupDecryptResult.Corrupted
        }
        val iv = try {
            PasswordCrypto.unb64(manifest.iv)
        } catch (_: IllegalArgumentException) {
            return BackupDecryptResult.Corrupted
        }
        val expectedVerifier = try {
            PasswordCrypto.unb64(manifest.verifier)
        } catch (_: IllegalArgumentException) {
            return BackupDecryptResult.Corrupted
        }

        val master = try {
            PasswordCrypto.deriveMaster(password, salt, manifest.iterations)
        } catch (_: Exception) {
            return BackupDecryptResult.Corrupted
        }
        val actualVerifier = PasswordCrypto.verifier(master)
        if (!PasswordCrypto.constantTimeEquals(actualVerifier, expectedVerifier)) {
            // manifest 的 KDF 参数被篡改同样会派生出不同的 master。用导出时的默认迭代数
            // 复核一次：若能对上 verifier，说明是容器头被改而不是口令错，应报「损坏/被篡改」。
            // 正常导出的头就是默认值，不会额外付一次 PBKDF2 开销。
            val headerTampered = manifest.iterations != PasswordCrypto.DEFAULT_ITERATIONS &&
                run {
                    val defaultMaster = try {
                        PasswordCrypto.deriveMaster(
                            password, salt, PasswordCrypto.DEFAULT_ITERATIONS
                        )
                    } catch (_: Exception) {
                        null
                    }
                    val matches = defaultMaster?.let {
                        val v = PasswordCrypto.verifier(it)
                        val equal = PasswordCrypto.constantTimeEquals(v, expectedVerifier)
                        PasswordCrypto.wipe(v)
                        equal
                    } ?: false
                    defaultMaster?.let { PasswordCrypto.wipe(it) }
                    matches
                }
            PasswordCrypto.wipe(master, actualVerifier)
            return if (headerTampered) BackupDecryptResult.Corrupted
            else BackupDecryptResult.WrongPassword
        }

        return try {
            val plain = PasswordCrypto.decrypt(
                encrypted.payload,
                PasswordCrypto.backupEncKey(master),
                iv,
                manifest.aadBytes()
            )
            val json = String(plain, StandardCharsets.UTF_8)
            val data = parseData(json) ?: return BackupDecryptResult.Corrupted
            BackupDecryptResult.Success(data)
        } catch (_: AEADBadTagException) {
            BackupDecryptResult.Corrupted
        } catch (_: Exception) {
            BackupDecryptResult.Corrupted
        } finally {
            PasswordCrypto.wipe(master, actualVerifier)
        }
    }

    /**
     * 解析 payload 明文。Gson 用 Unsafe 构造对象，字段声明虽非空仍可能为 null，
     * 因此按 JsonObject 逐字段读取并显式兜底，避免把损坏文件当成「空备份」静默导入。
     */
    private fun parseData(json: String): BackupData? {
        val root = try {
            gson.fromJson(json, JsonObject::class.java)
        } catch (_: Exception) {
            return null
        } ?: return null
        if (!root.isJsonObject) return null

        val records = try {
            val array = root.getAsJsonArray("records")
            if (array == null) emptyList()
            else gson.fromJson(array, Array<OvertimeRecord>::class.java)?.toList() ?: return null
        } catch (_: Exception) {
            return null
        }
        val settings = try {
            val obj = root.getAsJsonObject("settings")
            if (obj == null) emptyMap()
            else {
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(obj, Map::class.java) as? Map<String, String> ?: emptyMap()
            }
        } catch (_: Exception) {
            return null
        }
        return BackupData(records = records, settings = settings)
    }

    /** 供测试/自检：把 manifest + payload 打成 ZIP 字节（导出走 BackupManager 的流式实现）。 */
    fun toZipBytes(manifest: BackupManifest, payload: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry(ENTRY_MANIFEST))
            zos.write(manifestToJson(manifest).toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry(ENTRY_PAYLOAD))
            zos.write(payload)
            zos.closeEntry()
        }
        return bos.toByteArray()
    }
}
