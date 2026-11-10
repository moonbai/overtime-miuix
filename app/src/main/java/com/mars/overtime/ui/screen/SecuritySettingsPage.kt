package com.mars.overtime.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.security.AppLockManager
import com.mars.overtime.security.BackupSession
import com.mars.overtime.security.BiometricKeyManager
import com.mars.overtime.security.BiometricPrompts
import com.mars.overtime.security.PasswordCrypto
import com.mars.overtime.security.findActivity
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog

private enum class SecurityDialog {
    NONE, SET_PIN, SET_BACKUP_PASSWORD, VERIFY_BACKUP_PASSWORD
}

private const val MIN_BACKUP_PASSWORD_LENGTH = 8

/**
 * 安全与隐私设置：启动锁（PIN + 生物识别）与备份加密相互独立，全部默认关闭。
 *
 * 口令分离：启动 PIN 只生成本机校验值，不派生任何备份密钥；生物识别由 Keystore
 * 约束，只用于快捷解锁界面。备份口令单独派生主密钥并缓存于 [BackupSession]。
 */
@Composable
fun SecuritySettingsPage(
    navController: NavHostController,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val appLockEnabled by settingsRepository.appLockEnabled.collectAsState(initial = false)
    val biometricEnabled by settingsRepository.appLockBiometricEnabled.collectAsState(initial = false)
    val pinVerifier by settingsRepository.appLockVerifier.collectAsState(initial = "")
    val backupVerifier by settingsRepository.backupVerifier.collectAsState(initial = "")
    val backupEncryptionEnabled by settingsRepository.backupEncryptionEnabled.collectAsState(initial = false)
    val sessionActive by BackupSession.activeFlow.collectAsState()
    val hasPin = pinVerifier.isNotBlank()
    val hasBackupPassword = backupVerifier.isNotBlank()
    val biometricAvailable = remember(context) { BiometricKeyManager.isAvailable(context) }

    var dialog by remember { mutableStateOf(SecurityDialog.NONE) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    suspend fun toast(message: String) {
        status = message
        snackbarHostState.showCustomToast(message)
    }

    fun closeDialog() {
        dialog = SecurityDialog.NONE
        first = ""
        second = ""
        dialogError = null
        busy = false
    }

    fun openDialog(target: SecurityDialog) {
        first = ""
        second = ""
        dialogError = null
        busy = false
        dialog = target
    }

    fun onAppLockToggle(enabled: Boolean) {
        if (enabled) {
            if (!hasPin) {
                openDialog(SecurityDialog.SET_PIN)
            } else {
                scope.launch {
                    settingsRepository.setAppLockEnabled(true)
                    AppLockManager.setLockEnabled(true)
                    // 刚在设置页开启，不立刻把当前会话锁掉
                    AppLockManager.unlock()
                    toast("启动锁已开启")
                }
            }
        } else {
            scope.launch {
                settingsRepository.clearAppLockCredentials()
                BiometricKeyManager.deleteKey()
                AppLockManager.setLockEnabled(false)
                toast("启动锁已关闭，PIN 与生物识别凭据已清除")
            }
        }
    }

    fun onBiometricToggle(enabled: Boolean) {
        if (enabled && (!hasPin || !biometricAvailable)) {
            scope.launch { toast("需先设置启动 PIN，且设备已录入强生物特征并开启系统锁屏") }
            return
        }
        if (enabled) {
            val activity = context.findActivity()
            if (activity == null) {
                scope.launch { toast("无法启动生物识别") }
                return
            }
            scope.launch {
                // auth-per-use 密钥：init 可先做，doFinal 必须在认证成功回调里完成
                val pending = withContext(Dispatchers.IO) {
                    BiometricKeyManager.prepareEnrollment()
                }
                if (pending == null) {
                    toast("无法启用：请在系统设置中录入指纹/人脸并开启锁屏")
                    return@launch
                }
                BiometricPrompts.authenticate(
                    activity = activity,
                    crypto = pending.cryptoObject,
                    title = "启用生物识别解锁",
                    subtitle = "验证一次以绑定应用启动锁",
                    negativeText = "取消",
                    onSuccess = {
                        val enrollment = pending.finish()
                        pending.wipe()
                        if (enrollment == null) {
                            BiometricKeyManager.deleteKey()
                            scope.launch { toast("绑定失败，请重试") }
                        } else {
                            scope.launch {
                                settingsRepository.setAppLockBiometricEnrollment(
                                    PasswordCrypto.b64(enrollment.iv),
                                    PasswordCrypto.b64(enrollment.wrapped),
                                    PasswordCrypto.b64(enrollment.verifier)
                                )
                                settingsRepository.setAppLockBiometricEnabled(true)
                                enrollment.wipe()
                                toast("生物识别快捷解锁已开启")
                            }
                        }
                    },
                    onError = { message ->
                        pending.wipe()
                        BiometricKeyManager.deleteKey()
                        scope.launch { toast(message) }
                    },
                    onCancel = {
                        pending.wipe()
                        BiometricKeyManager.deleteKey()
                    }
                )
            }
        } else {
            scope.launch {
                BiometricKeyManager.deleteKey()
                settingsRepository.clearAppLockBiometricEnrollment()
                toast("生物识别快捷解锁已关闭")
            }
        }
    }

    fun onBackupEncryptionToggle(enabled: Boolean) {
        if (enabled) {
            if (!hasBackupPassword) {
                openDialog(SecurityDialog.SET_BACKUP_PASSWORD)
            } else {
                scope.launch {
                    settingsRepository.setBackupEncryptionEnabled(true)
                    if (BackupSession.isActive()) {
                        toast("备份加密已启用")
                    } else {
                        // 冷启动后缺会话密钥：立即引导验证，避免自动备份静默跳过
                        openDialog(SecurityDialog.VERIFY_BACKUP_PASSWORD)
                    }
                }
            }
        } else {
            scope.launch {
                settingsRepository.setBackupEncryptionEnabled(false)
                BackupSession.clear()
                toast("备份加密已关闭，会话密钥已清除")
            }
        }
    }

    fun submit() {
        when (dialog) {
            SecurityDialog.SET_PIN -> {
                if (first.length < MIN_APP_LOCK_PIN_LENGTH || !first.all { it.isDigit() }) {
                    dialogError = "启动 PIN 需为至少 $MIN_APP_LOCK_PIN_LENGTH 位数字"
                    return
                }
                if (first != second) {
                    dialogError = "两次输入不一致"
                    return
                }
                scope.launch {
                    busy = true
                    val chars = first.toCharArray()
                    val salt = PasswordCrypto.randomBytes(PasswordCrypto.SALT_BYTES)
                    val verifier = withContext(Dispatchers.IO) {
                        val master = PasswordCrypto.deriveMaster(
                            chars, salt, PasswordCrypto.DEFAULT_ITERATIONS
                        )
                        val v = PasswordCrypto.verifier(master, PasswordCrypto.LABEL_LOCK_VERIFY)
                        PasswordCrypto.wipe(master)
                        v
                    }
                    chars.fill('\u0000')
                    settingsRepository.setAppLockPin(
                        PasswordCrypto.b64(salt),
                        PasswordCrypto.b64(verifier),
                        PasswordCrypto.DEFAULT_ITERATIONS
                    )
                    settingsRepository.setAppLockEnabled(true)
                    AppLockManager.setLockEnabled(true)
                    AppLockManager.unlock()
                    closeDialog()
                    toast("启动 PIN 已设置，启动锁已开启")
                }
            }
            SecurityDialog.SET_BACKUP_PASSWORD -> {
                if (first.length < MIN_BACKUP_PASSWORD_LENGTH) {
                    dialogError = "备份口令至少 $MIN_BACKUP_PASSWORD_LENGTH 位"
                    return
                }
                if (first != second) {
                    dialogError = "两次输入不一致"
                    return
                }
                scope.launch {
                    busy = true
                    val chars = first.toCharArray()
                    val salt = PasswordCrypto.randomBytes(PasswordCrypto.SALT_BYTES)
                    val master = withContext(Dispatchers.IO) {
                        PasswordCrypto.deriveMaster(chars, salt, PasswordCrypto.DEFAULT_ITERATIONS)
                    }
                    chars.fill('\u0000')
                    val verifier = PasswordCrypto.verifier(master)
                    settingsRepository.setBackupPassword(
                        PasswordCrypto.b64(salt),
                        PasswordCrypto.b64(verifier),
                        PasswordCrypto.DEFAULT_ITERATIONS
                    )
                    BackupSession.install(master, salt)
                    PasswordCrypto.wipe(master)
                    settingsRepository.setBackupEncryptionEnabled(true)
                    closeDialog()
                    toast("备份口令已设置，加密备份已启用")
                }
            }
            SecurityDialog.VERIFY_BACKUP_PASSWORD -> {
                scope.launch {
                    busy = true
                    val chars = first.toCharArray()
                    val ok = BackupSecurity.verifyAndInstallSession(settingsRepository, chars)
                    chars.fill('\u0000')
                    busy = false
                    if (ok) {
                        closeDialog()
                        toast("备份口令已验证，加密自动备份已就绪")
                    } else {
                        dialogError = "备份口令不正确"
                    }
                }
            }
            SecurityDialog.NONE -> Unit
        }
    }

    val dialogTitle = when (dialog) {
        SecurityDialog.SET_PIN -> "设置启动 PIN"
        SecurityDialog.SET_BACKUP_PASSWORD -> "设置备份口令"
        SecurityDialog.VERIFY_BACKUP_PASSWORD -> "验证备份口令"
        SecurityDialog.NONE -> ""
    }
    val dialogSummary = when (dialog) {
        SecurityDialog.SET_PIN -> "至少 $MIN_APP_LOCK_PIN_LENGTH 位数字"
        SecurityDialog.SET_BACKUP_PASSWORD ->
            "至少 $MIN_BACKUP_PASSWORD_LENGTH 位；丢失后加密备份无法恢复"
        SecurityDialog.VERIFY_BACKUP_PASSWORD -> "验证后本次运行期间自动加密备份"
        SecurityDialog.NONE -> ""
    }
    val inputLabel = when (dialog) {
        SecurityDialog.SET_PIN -> "启动 PIN"
        SecurityDialog.SET_BACKUP_PASSWORD -> "备份口令"
        else -> "备份口令"
    }

    SettingsScreen(
        title = "安全与隐私",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState,
        dialogs = {
            BackHandler(enabled = dialog != SecurityDialog.NONE) {
                if (!busy) closeDialog()
            }
            OverlayDialog(
                show = dialog != SecurityDialog.NONE,
                title = dialogTitle,
                summary = dialogSummary,
                onDismissRequest = { if (!busy) closeDialog() }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    SettingsTextField(
                        value = first,
                        onValueChange = { first = it; dialogError = null },
                        label = inputLabel,
                        isPassword = true,
                        keyboardType = if (dialog == SecurityDialog.SET_PIN)
                            KeyboardType.NumberPassword else null,
                        enabled = !busy
                    )
                    if (dialog == SecurityDialog.SET_PIN ||
                        dialog == SecurityDialog.SET_BACKUP_PASSWORD
                    ) {
                        SettingsTextField(
                            value = second,
                            onValueChange = { second = it; dialogError = null },
                            label = "再次输入",
                            isPassword = true,
                            keyboardType = if (dialog == SecurityDialog.SET_PIN)
                                KeyboardType.NumberPassword else null,
                            enabled = !busy
                        )
                    }
                    dialogError?.let { SettingsNote(text = it, isError = true) }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "取消",
                            onClick = { if (!busy) closeDialog() },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        TextButton(
                            text = if (busy) "处理中…" else "确定",
                            onClick = { if (!busy) submit() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) {
        status?.let { message ->
            item { SettingsStatusText(text = message) }
        }

        item {
            SettingsGroup(title = "启动锁定") {
                SettingsSwitchItem(
                    title = "启用启动锁",
                    summary = "冷启动或离屏 15 秒后需验证",
                    checked = appLockEnabled,
                    onCheckedChange = ::onAppLockToggle
                )
                SettingsActionItem(
                    title = if (hasPin) "修改启动 PIN" else "设置启动 PIN",
                    summary = "至少 $MIN_APP_LOCK_PIN_LENGTH 位数字",
                    icon = MiuixIcons.Scan,
                    onClick = { openDialog(SecurityDialog.SET_PIN) }
                )
                SettingsSwitchItem(
                    title = "生物识别快捷解锁",
                    summary = if (biometricAvailable) "指纹 / 人脸"
                    else "设备不支持或未录入",
                    checked = biometricEnabled,
                    onCheckedChange = ::onBiometricToggle
                )
                if (appLockEnabled) {
                    SettingsActionItem(
                        title = "立即锁定",
                        summary = "立即生效",
                        icon = MiuixIcons.Info,
                        onClick = { AppLockManager.lock() }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "备份加密") {
                SettingsSwitchItem(
                    title = "加密导出与自动备份",
                    summary = "AES-256-GCM 加密备份文件",
                    checked = backupEncryptionEnabled,
                    onCheckedChange = ::onBackupEncryptionToggle
                )
                SettingsActionItem(
                    title = if (hasBackupPassword) "修改备份口令" else "设置备份口令",
                    summary = "与启动 PIN 分离，至少 $MIN_BACKUP_PASSWORD_LENGTH 位",
                    icon = MiuixIcons.Scan,
                    onClick = { openDialog(SecurityDialog.SET_BACKUP_PASSWORD) }
                )
                SettingsActionItem(
                    title = "验证备份口令",
                    summary = if (sessionActive) "会话密钥已就绪"
                    else "待验证，自动备份已暂停",
                    icon = MiuixIcons.Ok,
                    onClick = {
                        openDialog(
                            if (hasBackupPassword) SecurityDialog.VERIFY_BACKUP_PASSWORD
                            else SecurityDialog.SET_BACKUP_PASSWORD
                        )
                    }
                )
                if (hasBackupPassword) {
                    SettingsActionItem(
                        title = "清除备份口令",
                        summary = "清除本机口令；旧加密包仍可用原口令解开",
                        icon = MiuixIcons.Delete,
                        onClick = {
                            scope.launch {
                                settingsRepository.clearBackupPassword()
                                BackupSession.clear()
                                toast("备份口令已清除")
                            }
                        }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "说明") {
                SettingsNote(
                    text = "启动 PIN 与备份口令相互独立，凭据仅存本机、不随备份导出；" +
                        "冷启动后需验证一次口令，加密自动备份才会执行。"
                )
            }
        }
    }
}
