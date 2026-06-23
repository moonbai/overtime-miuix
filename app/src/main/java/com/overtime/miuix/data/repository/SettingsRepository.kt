package com.overtime.miuix.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    // Theme settings
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
    val accentColor: Flow<Int> = context.dataStore.data.map { it[KEY_ACCENT_COLOR] ?: 0xFF3482FF.toInt() }
    val bottomBarStyle: Flow<String> = context.dataStore.data.map { it[KEY_BOTTOM_BAR_STYLE] ?: "ICON_TEXT" }
    val quickSubmit: Flow<Boolean> = context.dataStore.data.map { it[KEY_QUICK_SUBMIT] ?: false }
    
    // Salary settings
    val baseSalary: Flow<Double> = context.dataStore.data.map { it[KEY_BASE_SALARY] ?: 2200.0 }
    val workdayRate: Flow<Double> = context.dataStore.data.map { it[KEY_WORKDAY_RATE] ?: 1.5 }
    val weekendRate: Flow<Double> = context.dataStore.data.map { it[KEY_WEEKEND_RATE] ?: 2.0 }
    val holidayRate: Flow<Double> = context.dataStore.data.map { it[KEY_HOLIDAY_RATE] ?: 3.0 }
    
    // MCP settings
    val mcpEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_MCP_ENABLED] ?: false }
    val mcpPort: Flow<Int> = context.dataStore.data.map { it[KEY_MCP_PORT] ?: 8080 }
    
    // Push settings
    val pushEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PUSH_ENABLED] ?: false }
    val pushChannel: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_CHANNEL] ?: "none" }
    
    // Calendar sync
    val calendarSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALENDAR_SYNC] ?: false }
    
    suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[KEY_THEME_MODE] = mode } }
    suspend fun setAccentColor(color: Int) { context.dataStore.edit { it[KEY_ACCENT_COLOR] = color } }
    suspend fun setBottomBarStyle(style: String) { context.dataStore.edit { it[KEY_BOTTOM_BAR_STYLE] = style } }
    suspend fun setQuickSubmit(enabled: Boolean) { context.dataStore.edit { it[KEY_QUICK_SUBMIT] = enabled } }
    
    suspend fun setBaseSalary(salary: Double) { context.dataStore.edit { it[KEY_BASE_SALARY] = salary } }
    suspend fun setWorkdayRate(rate: Double) { context.dataStore.edit { it[KEY_WORKDAY_RATE] = rate } }
    suspend fun setWeekendRate(rate: Double) { context.dataStore.edit { it[KEY_WEEKEND_RATE] = rate } }
    suspend fun setHolidayRate(rate: Double) { context.dataStore.edit { it[KEY_HOLIDAY_RATE] = rate } }
    
    suspend fun setMcpEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_MCP_ENABLED] = enabled } }
    suspend fun setMcpPort(port: Int) { context.dataStore.edit { it[KEY_MCP_PORT] = port } }
    
    suspend fun setPushEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_PUSH_ENABLED] = enabled } }
    suspend fun setPushChannel(channel: String) { context.dataStore.edit { it[KEY_PUSH_CHANNEL] = channel } }
    
    suspend fun setCalendarSyncEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_CALENDAR_SYNC] = enabled } }
    
    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
        private val KEY_BOTTOM_BAR_STYLE = stringPreferencesKey("bottom_bar_style")
        private val KEY_QUICK_SUBMIT = booleanPreferencesKey("quick_submit")
        
        private val KEY_BASE_SALARY = doublePreferencesKey("base_salary")
        private val KEY_WORKDAY_RATE = doublePreferencesKey("workday_rate")
        private val KEY_WEEKEND_RATE = doublePreferencesKey("weekend_rate")
        private val KEY_HOLIDAY_RATE = doublePreferencesKey("holiday_rate")
        
        private val KEY_MCP_ENABLED = booleanPreferencesKey("mcp_enabled")
        private val KEY_MCP_PORT = intPreferencesKey("mcp_port")
        
        private val KEY_PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        private val KEY_PUSH_CHANNEL = stringPreferencesKey("push_channel")
        
        private val KEY_CALENDAR_SYNC = booleanPreferencesKey("calendar_sync")
    }
}
