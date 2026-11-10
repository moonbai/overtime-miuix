package com.mars.overtime.security

import java.security.Key
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 口令派生与认证加密内核（全部基于平台 JCA，无第三方加密库）。
 *
 * - KDF：`PBKDF2WithHmacSHA256`，随机 16B salt，迭代数写进容器头便于日后平滑提升；
 *   默认 [DEFAULT_ITERATIONS]（OWASP 2023 建议量级），把离线爆破单次成本抬高。
 * - 域分离：用 `HMAC-SHA256(master, label)` 从主密钥派生「加密密钥」与「校验值」，
 *   启动锁与备份加密使用不同 label，避免同一派生结果跨用途复用。
 * - 加密：`AES-256-GCM`，每次随机 12B IV，128bit tag，AAD 绑定容器头全部字段
 *   （防篡改，也防「把加密标记改成不加密」的降级攻击）。
 *
 * 口令只以 `CharArray` 传入，[deriveMaster] 内 `clearPassword()`，调用方用完自行清零。
 */
object PasswordCrypto {
    const val KDF = "PBKDF2WithHmacSHA256"
    const val CIPHER = "AES-256-GCM"
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_ITERATIONS = 10_000
    const val MAX_ITERATIONS = 5_000_000
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val VERIFIER_BYTES = 16

    private const val MASTER_BITS = 256
    private const val TAG_BITS = 128

    const val LABEL_BACKUP_ENC = "com.mars.overtime/backup/enc/v1"
    const val LABEL_BACKUP_VERIFY = "com.mars.overtime/backup/verify/v1"
    const val LABEL_LOCK_VERIFY = "com.mars.overtime/applock/verify/v1"
    const val LABEL_BIO_VERIFY = "com.mars.overtime/applock/bio/v1"

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }
    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    /** 从口令派生 32B 主密钥。须在 IO 线程调用（约 0.2~1s）。 */
    fun deriveMaster(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(
            password,
            salt,
            iterations.coerceIn(MIN_ITERATIONS, MAX_ITERATIONS),
            MASTER_BITS
        )
        return try {
            SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun hmac(master: ByteArray, label: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(master, "HmacSHA256"))
        return mac.doFinal(label.toByteArray(Charsets.UTF_8))
    }

    fun backupEncKey(master: ByteArray): SecretKeySpec =
        SecretKeySpec(hmac(master, LABEL_BACKUP_ENC).copyOf(32), "AES")

    fun verifier(master: ByteArray, label: String = LABEL_BACKUP_VERIFY): ByteArray =
        hmac(master, label).copyOf(VERIFIER_BYTES)

    fun encrypt(plain: ByteArray, key: Key, iv: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(plain)
    }

    /** 口令错误、密文或 AAD 被篡改时抛 `AEADBadTagException`。 */
    fun decrypt(ciphertext: ByteArray, key: Key, iv: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun wipe(vararg arrays: ByteArray) = arrays.forEach { it.fill(0) }
}
