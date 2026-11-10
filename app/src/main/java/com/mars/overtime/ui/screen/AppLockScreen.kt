package com.mars.overtime.ui.screen

import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mars.overtime.R
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.security.AppLockManager
import com.mars.overtime.security.BiometricKeyManager
import com.mars.overtime.security.BiometricPrompts
import com.mars.overtime.security.PasswordCrypto
import com.mars.overtime.security.findActivity
import com.mars.overtime.ui.theme.AppDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.atomic.AtomicReference

const val MIN_APP_LOCK_PIN_LENGTH = 6

/**
 * 启动锁门控：包在 [com.mars.overtime.MainNavHost] 之外。
 *
 * - 持久化开关尚未读到时只渲染主题底色，绝不先组合主界面（防冷启动闪屏泄露数据）；
 * - 通知 deep link 的导航消费放在 content 内，未解锁时不会执行；
 * - 锁屏期间加 FLAG_SECURE，禁止截屏与最近任务缩略图。
 */
@Composable
fun AppLockGate(
    settingsRepository: SettingsRepository,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // initial = null：等 DataStore 第一个值，避免「默认 false」导致主界面先渲染一帧
    val enabled by settingsRepository.appLockEnabled.collectAsState(initial = null)
    val unlocked by AppLockManager.isUnlocked.collectAsState()
    val biometricEnabled by settingsRepository.appLockBiometricEnabled.collectAsState(initial = false)

    LaunchedEffect(enabled) {
        if (enabled != null) AppLockManager.setLockEnabled(enabled == true)
    }

    val locked = enabled == true && !unlocked
    DisposableEffect(locked, activity) {
        if (locked) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    when {
        enabled == null -> Box(
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
        )
        locked -> AppLockScreen(
            settingsRepository = settingsRepository,
            biometricEnabled = biometricEnabled
        )
        else -> content()
    }
}

private sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val hint: String?) : PinCheck
    data class Wait(val seconds: Long) : PinCheck
    data object NotConfigured : PinCheck
}

/**
 * PIN 校验 + 失败退避。用 elapsedRealtime 计时，避免用户改系统时间绕过锁定。
 * 连续输错 5 次起按档位临时锁定，失败次数与截止时间只存本机。
 */
private suspend fun checkPin(repo: SettingsRepository, pin: String): PinCheck =
    withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        val until = repo.appLockLockedUntil.first()
        if (until > now) {
            return@withContext PinCheck.Wait(((until - now + 999L) / 1000L).coerceAtLeast(1L))
        }
        val saltB64 = repo.appLockSalt.first()
        val verifierB64 = repo.appLockVerifier.first()
        if (saltB64.isBlank() || verifierB64.isBlank()) return@withContext PinCheck.NotConfigured
        val salt = try {
            PasswordCrypto.unb64(saltB64)
        } catch (_: Exception) {
            return@withContext PinCheck.NotConfigured
        }
        val expected = try {
            PasswordCrypto.unb64(verifierB64)
        } catch (_: Exception) {
            return@withContext PinCheck.NotConfigured
        }

        val chars = pin.toCharArray()
        val master = PasswordCrypto.deriveMaster(chars, salt, repo.appLockIterations.first())
        chars.fill('\u0000')
        val actual = PasswordCrypto.verifier(master, PasswordCrypto.LABEL_LOCK_VERIFY)
        val ok = PasswordCrypto.constantTimeEquals(actual, expected)
        PasswordCrypto.wipe(master, actual)

        if (ok) {
            repo.setAppLockAttemptState(0, 0L)
            PinCheck.Ok
        } else {
            val failed = repo.appLockFailedAttempts.first() + 1
            val lockMs = if (failed >= 5) (failed / 5) * 30_000L else 0L
            repo.setAppLockAttemptState(failed, if (lockMs > 0) now + lockMs else 0L)
            PinCheck.Wrong(if (lockMs > 0) "连续输错，已临时锁定 ${lockMs / 1000} 秒" else null)
        }
    }

@Composable
private fun AppLockScreen(
    settingsRepository: SettingsRepository,
    biometricEnabled: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val signalRef = remember { AtomicReference<CancellationSignal?>(null) }
    // 自动弹出只做一次；用户取消后不再打扰，可手动点「使用生物识别解锁」重试
    var biometricAutoLaunched by remember { mutableStateOf(false) }
    var biometricInFlight by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { signalRef.get()?.cancel() }
    }

    fun verify() {
        if (busy || pin.length < MIN_APP_LOCK_PIN_LENGTH) return
        scope.launch {
            busy = true
            val result = checkPin(settingsRepository, pin)
            busy = false
            when (result) {
                PinCheck.Ok -> {
                    pin = ""
                    error = null
                    AppLockManager.unlock()
                }
                is PinCheck.Wrong -> {
                    pin = ""
                    error = result.hint ?: "PIN 不正确，请重试"
                }
                is PinCheck.Wait -> error = "尝试次数过多，请 ${result.seconds} 秒后重试"
                PinCheck.NotConfigured -> error = "未找到启动 PIN 配置，请清除应用数据后重设"
            }
        }
    }

    fun launchBiometric() {
        if (biometricInFlight) return
        val activity = context.findActivity()
        if (activity == null) {
            error = "无法启动生物识别"
            return
        }
        scope.launch {
            val ivB64 = settingsRepository.appLockBioIv.first()
            val wrappedB64 = settingsRepository.appLockBioWrapped.first()
            val verifierB64 = settingsRepository.appLockBioVerifier.first()
            if (ivB64.isBlank() || wrappedB64.isBlank() || verifierB64.isBlank()) {
                error = "生物识别配置缺失，请用 PIN 解锁后重新开启"
                return@launch
            }
            val iv = try {
                PasswordCrypto.unb64(ivB64)
            } catch (_: Exception) {
                error = "生物识别配置损坏，请用 PIN 解锁后重新开启"
                return@launch
            }
            val wrapped = try {
                PasswordCrypto.unb64(wrappedB64)
            } catch (_: Exception) {
                error = "生物识别配置损坏，请用 PIN 解锁后重新开启"
                return@launch
            }
            val verifier = try {
                PasswordCrypto.unb64(verifierB64)
            } catch (_: Exception) {
                error = "生物识别配置损坏，请用 PIN 解锁后重新开启"
                return@launch
            }
            val cipher = withContext(Dispatchers.IO) { BiometricKeyManager.unlockCipher(iv) }
            if (cipher == null) {
                error = "生物识别密钥已失效，请用 PIN 解锁后重新开启"
                return@launch
            }
            biometricInFlight = true
            BiometricPrompts.authenticate(
                activity = activity,
                crypto = BiometricPrompt.CryptoObject(cipher),
                title = "解锁加班记",
                subtitle = "使用已录入的生物特征",
                onSignal = { signalRef.set(it) },
                onSuccess = { authenticated ->
                    biometricInFlight = false
                    val ok = BiometricKeyManager.verifyUnlockedSecret(
                        authenticated, wrapped, verifier
                    )
                    if (ok) {
                        pin = ""
                        error = null
                        AppLockManager.unlock()
                    } else {
                        error = "生物识别校验未通过，请使用 PIN"
                    }
                },
                onError = { message ->
                    biometricInFlight = false
                    error = message
                },
                onCancel = { biometricInFlight = false }
            )
        }
    }

    // 进入锁屏即自动发起生物识别，不必多点一次按钮。
    // biometricEnabled 从 DataStore 读到 true 时才触发，未开启生物识别则只显示 PIN。
    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && !biometricAutoLaunched) {
            biometricAutoLaunched = true
            launchBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = AppDimens.pageHorizontal),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_round),
                contentDescription = "应用图标",
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "应用已锁定",
                style = MiuixTheme.textStyles.headline2,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "请输入启动 PIN 或使用生物识别",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = pin,
                onValueChange = { value ->
                    if (value.length <= 12 && value.all { it.isDigit() }) {
                        pin = value
                        error = null
                    }
                },
                label = "启动 PIN",
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                ),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error
                )
            }
            Button(
                onClick = { verify() },
                enabled = !busy && pin.length >= MIN_APP_LOCK_PIN_LENGTH,
                colors = ButtonDefaults.buttonColorsPrimary(),
                cornerRadius = AppDimens.controlCorner,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "验证中…" else "解锁")
            }
            if (biometricEnabled) {
                TextButton(
                    text = "使用生物识别解锁",
                    onClick = { launchBiometric() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
