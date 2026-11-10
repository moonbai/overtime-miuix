package com.mars.overtime.ui.screen

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.security.BackupDecryptResult
import com.mars.overtime.security.BackupEnvelope
import com.mars.overtime.security.BackupReadResult
import com.mars.overtime.security.BackupSession
import com.mars.overtime.security.PasswordCrypto
import com.mars.overtime.util.BackupData
import com.mars.overtime.util.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** 备份口令输入框的用途：决定验证成功后走哪条后续动作。 */
enum class PasswordAction {
    DECRYPT_IMPORT,
    BEFORE_EXPORT,
    BEFORE_UPLOAD,
    VERIFY_ONLY
}

data class PasswordRequest(
    val action: PasswordAction,
    val encrypted: BackupReadResult.Encrypted? = null
)

object BackupSecurity {
    /**
     * 校验本地备份口令；通过后把主密钥装进会话缓存。
     * 口令只以 CharArray 传入，调用方负责用后清零。派生在 IO 线程执行。
     */
    suspend fun verifyAndInstallSession(
        repo: SettingsRepository,
        password: CharArray
    ): Boolean = withContext(Dispatchers.IO) {
        val saltB64 = repo.backupKdfSalt.first()
        val verifierB64 = repo.backupVerifier.first()
        if (saltB64.isBlank() || verifierB64.isBlank()) return@withContext false
        val salt = try {
            PasswordCrypto.unb64(saltB64)
        } catch (_: Exception) {
            return@withContext false
        }
        val expected = try {
            PasswordCrypto.unb64(verifierB64)
        } catch (_: Exception) {
            return@withContext false
        }
        val master = try {
            PasswordCrypto.deriveMaster(password, salt, repo.backupKdfIterations.first())
        } catch (_: Exception) {
            return@withContext false
        }
        val actual = PasswordCrypto.verifier(master)
        val ok = PasswordCrypto.constantTimeEquals(actual, expected)
        if (ok) BackupSession.install(master, salt)
        PasswordCrypto.wipe(master, actual)
        ok
    }

    /**
     * 按文件头识别 ZIP / 旧版 JSON，不依赖文件名后缀（SAF 的 lastPathSegment
     * 常常是 document id，拿不到 .zip 结尾）。
     */
    fun readBackupFromFile(path: String): BackupReadResult {
        val file = File(path)
        if (!file.exists()) return BackupReadResult.Invalid
        val isZip = try {
            FileInputStream(file).use { fis ->
                val head = ByteArray(2)
                val n = fis.read(head)
                n == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
        return if (isZip) {
            BackupManager.readBackup(path)
        } else {
            BackupManager.importData(path)?.let { BackupReadResult.Plain(it) }
                ?: BackupReadResult.Invalid
        }
    }

    /** content:// 备份先落 cache，再统一按文件头识别，用完即删。 */
    suspend fun readBackupFromUri(context: Context, uri: Uri): BackupReadResult =
        withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "import_tmp.zip")
            val ok = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!ok) return@withContext BackupReadResult.Invalid
            return@withContext try {
                readBackupFromFile(tmp.absolutePath)
            } finally {
                tmp.delete()
            }
        }
}

/**
 * 统一的备份口令弹窗：既用于解密导入，也用于验证会话密钥。
 * 解密失败明确区分「口令不正确」与「文件损坏或被篡改」。
 */
@Composable
fun BackupPasswordDialog(
    request: PasswordRequest,
    settingsRepository: SettingsRepository,
    onDismiss: () -> Unit,
    onDecryptSuccess: (BackupData) -> Unit,
    onSessionVerified: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var text by remember(request) { mutableStateOf("") }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var busy by remember(request) { mutableStateOf(false) }

    val isDecrypt = request.action == PasswordAction.DECRYPT_IMPORT
    val title = if (isDecrypt) "输入备份口令" else "验证备份口令"
    val summary = when (request.action) {
        PasswordAction.DECRYPT_IMPORT -> "该备份已加密，需输入导出时设置的口令才能解密导入"
        PasswordAction.BEFORE_EXPORT -> "加密导出需要会话密钥，请验证备份口令"
        PasswordAction.BEFORE_UPLOAD -> "加密上传需要会话密钥，请验证备份口令"
        PasswordAction.VERIFY_ONLY -> "验证后本次运行期间可自动完成加密备份"
    }

    OverlayDialog(
        show = true,
        title = title,
        summary = summary,
        onDismissRequest = { if (!busy) onDismiss() }
    ) {
        Column(Modifier.fillMaxWidth()) {
            SettingsTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = "备份口令",
                isPassword = true,
                enabled = !busy
            )
            error?.let { SettingsNote(text = it, isError = true) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    text = "取消",
                    onClick = { if (!busy) onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = if (busy) "处理中…" else "确定",
                    onClick = {
                        if (!busy && text.isNotEmpty()) {
                            scope.launch {
                                busy = true
                                val chars = text.toCharArray()
                                try {
                                    if (isDecrypt) {
                                        val encrypted = request.encrypted
                                        if (encrypted == null) {
                                            error = "加密数据缺失"
                                        } else {
                                            when (val result = withContext(Dispatchers.IO) {
                                                BackupEnvelope.decrypt(encrypted, chars)
                                            }) {
                                                is BackupDecryptResult.Success -> {
                                                    onDismiss()
                                                    onDecryptSuccess(result.data)
                                                }
                                                BackupDecryptResult.WrongPassword ->
                                                    error = "口令不正确，请重试"
                                                BackupDecryptResult.Corrupted ->
                                                    error = "文件损坏或被篡改，无法解密"
                                            }
                                        }
                                    } else {
                                        val ok = BackupSecurity.verifyAndInstallSession(
                                            settingsRepository, chars
                                        )
                                        if (ok) {
                                            onDismiss()
                                            onSessionVerified()
                                        } else {
                                            error = "备份口令不正确"
                                        }
                                    }
                                } catch (e: Exception) {
                                    error = "处理失败：${e.message ?: "未知错误"}"
                                } finally {
                                    chars.fill('\u0000')
                                    text = ""
                                    busy = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
