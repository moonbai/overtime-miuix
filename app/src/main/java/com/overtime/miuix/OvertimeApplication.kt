package com.overtime.miuix

import android.app.Application
import com.overtime.miuix.data.database.AppDatabase
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository

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
}
