package com.overtime.miuix

import android.app.Application
import android.content.ComponentCallbacks2
import com.overtime.miuix.data.database.AppDatabase
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.util.FairMemoryManager

class OvertimeApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var overtimeRepository: OvertimeRepository
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        overtimeRepository = OvertimeRepository(database)
        settingsRepository = SettingsRepository(this)
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
