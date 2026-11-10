package com.mars.overtime.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import javax.crypto.Cipher

/** 从任意 Context（含 Compose 的 ContextWrapper）向上找到 Activity。 */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 平台 BiometricPrompt 的统一封装：开启生物识别与解锁共用同一套认证流程。 */
object BiometricPrompts {
    private fun mainExecutor(): Executor =
        Executor { command -> Handler(Looper.getMainLooper()).post(command) }

    /**
     * @param crypto 必须携带已 init 的 Cipher（ENCRYPT 或 DECRYPT 模式）。
     * @param onSuccess 认证成功后回调，参数是 CryptoObject 中已获授权的同一 Cipher。
     * @param onSignal 回传 CancellationSignal，供调用方在页面销毁时取消。
     */
    fun authenticate(
        activity: Activity,
        crypto: BiometricPrompt.CryptoObject,
        title: String,
        subtitle: String,
        negativeText: String = "使用 PIN",
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
        onSignal: (CancellationSignal) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        val executor = mainExecutor()
        val signal = CancellationSignal()
        onSignal(signal)
        val prompt = try {
            BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton(
                    negativeText,
                    executor,
                    DialogInterface.OnClickListener { _, _ -> }
                )
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        } catch (e: Exception) {
            onError("无法启动生物识别：${e.message ?: "未知错误"}")
            return
        }
        try {
            prompt.authenticate(
                crypto,
                signal,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        val cipher = result.cryptoObject?.cipher
                        if (cipher != null) onSuccess(cipher)
                        else onError("生物识别未返回解密凭据")
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        ) {
                            onCancel()
                        } else {
                            onError(errString.toString().ifBlank { "生物识别失败" })
                        }
                    }

                    override fun onAuthenticationFailed() = Unit
                }
            )
        } catch (e: Exception) {
            onError("生物识别启动失败：${e.message ?: "未知错误"}")
        }
    }
}
