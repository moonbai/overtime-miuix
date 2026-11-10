package com.mars.overtime.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 备份会话密钥缓存：只在进程内存保存主密钥，绝不落盘、绝不写进备份。
 *
 * 冷启动后缓存为空，此时「加密自动备份」直接跳过而不是降级为明文；
 * 用户在「安全与隐私」设置或验证备份口令后，[install] 填充缓存，
 * 之后本次进程内的所有加密导出/自动备份复用同一个 master。
 *
 * 主密钥与 [PasswordCrypto.deriveMaster] 的 salt 成对保存：容器 manifest 里写入的
 * salt 必须是同一个值，导入方才能用「口令 + manifest.salt」重新派生出相同 master。
 */
class SessionKey(val master: ByteArray, val salt: ByteArray) {
    fun duplicate(): SessionKey = SessionKey(master.copyOf(), salt.copyOf())
    fun wipe() = PasswordCrypto.wipe(master, salt)
}

object BackupSession {
    private val guard = Any()

    @Volatile
    private var cached: SessionKey? = null

    private val _active = MutableStateFlow(false)
    /** 供设置页观察会话密钥是否就绪（冷启动后为 false）。 */
    val activeFlow: StateFlow<Boolean> = _active.asStateFlow()

    fun isActive(): Boolean = synchronized(guard) { cached != null }

    /** 返回副本；调用方用完必须 [SessionKey.wipe]，避免长期持有明文密钥。 */
    fun snapshot(): SessionKey? = synchronized(guard) { cached?.duplicate() }

    /** 由口令派生并缓存。派生产生的临时 master 用后即清零。 */
    fun install(password: CharArray, salt: ByteArray, iterations: Int) {
        val master = PasswordCrypto.deriveMaster(password, salt, iterations)
        try {
            install(master, salt)
        } finally {
            PasswordCrypto.wipe(master)
        }
    }

    fun install(master: ByteArray, salt: ByteArray) {
        val fresh = SessionKey(master.copyOf(), salt.copyOf())
        val previous = synchronized(guard) {
            val old = cached
            cached = fresh
            old
        }
        previous?.wipe()
        _active.value = true
    }

    fun clear() {
        val previous = synchronized(guard) {
            val old = cached
            cached = null
            old
        }
        previous?.wipe()
        _active.value = false
    }
}
