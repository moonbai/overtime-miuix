package com.mars.overtime

import android.app.Application
import android.content.ComponentCallbacks2
import com.mars.overtime.data.database.AppDatabase
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.security.AppLockManager
import com.mars.overtime.util.FairMemoryManager

class OvertimeApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var overtimeRepository: OvertimeRepository
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        overtimeRepository = OvertimeRepository(database)
        settingsRepository = SettingsRepository(this)
        // 启动锁的后台超时重锁依赖进程级生命周期回调，必须尽早注册
        AppLockManager.install(this)
        runLeaveTypeMigration()
    }

    /**
     * 一次性数据迁移：历史请假记录 type 统一为休息日（WEEKEND），落实「请假 = 负数的休息日」。
     * 用 SharedPreferences 标记保证只跑一次；迁移本身是单条 UPDATE，幂等、几乎无开销。
     */
    private fun runLeaveTypeMigration() {
        val prefs = getSharedPreferences("app_migration", MODE_PRIVATE)
        if (prefs.getBoolean("leave_type_to_weekend_v1", false)) return
        kotlinx.coroutines.runBlocking {
            try {
                overtimeRepository.migrateLeaveTypeToWeekend()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        prefs.edit().putBoolean("leave_type_to_weekend_v1", true).apply()
    }

    // 公平运行内存机制：内存压力统一入口——预警时按档位释放缓存/空闲连接/非常驻服务
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        FairMemoryManager.onTrimMemory(this, level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        FairMemoryManager.onTrimMemory(this, ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}
