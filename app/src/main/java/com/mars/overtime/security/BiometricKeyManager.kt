package com.mars.overtime.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 开启生物识别快捷解锁时生成的包裹材料：IV + 被 Keystore 密钥加密的随机 secret + 校验值。 */
data class BiometricEnrollment(
    val iv: ByteArray,
    val wrapped: ByteArray,
    val verifier: ByteArray
) {
    fun wipe() = PasswordCrypto.wipe(iv, wrapped, verifier)
}

/**
 * 待认证完成的注册材料。
 *
 * [cipher] 已按 ENCRYPT_MODE 初始化（init 本身不要求认证），但 auth-per-use 密钥的
 * `doFinal` 必须在 BiometricPrompt 认证成功回调里、用 CryptoObject 携带的同一个 Cipher 执行，
 * 否则抛 UserNotAuthenticatedException。见 Android 官方 BiometricPrompt 加密示例。
 */
class PendingEnrollment(private val cipher: Cipher, private val secret: ByteArray) {
    val cryptoObject: BiometricPrompt.CryptoObject
        get() = BiometricPrompt.CryptoObject(cipher)

    /** 只能在 onAuthenticationSucceeded 回调里调用。 */
    fun finish(): BiometricEnrollment? = try {
        val wrapped = cipher.doFinal(secret)
        BiometricEnrollment(
            iv = cipher.iv,
            wrapped = wrapped,
            verifier = PasswordCrypto.verifier(secret, PasswordCrypto.LABEL_BIO_VERIFY)
        )
    } catch (_: Exception) {
        null
    }

    fun wipe() = PasswordCrypto.wipe(secret)
}

/**
 * 生物识别快捷解锁的 Keystore 锚点。
 *
 * 生物识别**不派生任何备份密钥**（指纹不能稳定派生口令），这里只用它解开一个
 * 随机 32B secret：Keystore 密钥设置 `setUserAuthenticationParameters(0, BIOMETRIC_STRONG)`，
 * 每次使用都必须通过系统生物识别；只有解开后 secret 与本地 verifier 一致才放行启动锁。
 * 这样生物识别不是「UI 层把布尔值翻成 true」，而是受硬件级认证约束。
 *
 * 新增指纹会让密钥永久失效（setInvalidatedByBiometricEnrollment(true)），
 * 此时 [unlockCipher] 返回 null，UI 提示用户用 PIN 解锁后重新开启生物识别。
 */
object BiometricKeyManager {
    private const val KEY_ALIAS = "ot_applock_bio_v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val SECRET_BYTES = 32
    private const val TAG_BITS = 128

    fun isAvailable(context: Context): Boolean = try {
        // 平台 BiometricManager 构造器包私有，也没有 from()；统一走系统服务获取。
        val manager = context.getSystemService(BiometricManager::class.java)
        manager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Exception) {
        false
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun secretKey(): SecretKey = keyStore().getKey(KEY_ALIAS, null) as SecretKey

    fun deleteKey() {
        try {
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    /**
     * 重建 Keystore 密钥并准备 ENCRYPT_MODE Cipher，返回待认证材料。
     * 设备未设置系统锁屏 / 未录入强生物特征时会抛异常，返回 null 由 UI 提示。
     * 真正的加密在 [PendingEnrollment.finish] 中、生物识别成功后完成。
     */
    fun prepareEnrollment(): PendingEnrollment? = try {
        deleteKey()
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(spec)
        generator.generateKey()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        PendingEnrollment(cipher, PasswordCrypto.randomBytes(SECRET_BYTES))
    } catch (_: Exception) {
        deleteKey()
        null
    }

    /** 交给 `BiometricPrompt.CryptoObject` 的解密 Cipher；密钥失效时删除并返回 null。 */
    fun unlockCipher(iv: ByteArray): Cipher? = try {
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        }
    } catch (_: KeyPermanentlyInvalidatedException) {
        deleteKey()
        null
    } catch (_: Exception) {
        null
    }

    /** 生物识别成功后用已认证 Cipher 解出 secret 并常量时间比对校验值。 */
    fun verifyUnlockedSecret(
        cipher: Cipher,
        wrapped: ByteArray,
        expectedVerifier: ByteArray
    ): Boolean = try {
        val secret = cipher.doFinal(wrapped)
        val actual = PasswordCrypto.verifier(secret, PasswordCrypto.LABEL_BIO_VERIFY)
        val ok = PasswordCrypto.constantTimeEquals(actual, expectedVerifier)
        PasswordCrypto.wipe(secret, actual)
        ok
    } catch (_: Exception) {
        false
    }
}
