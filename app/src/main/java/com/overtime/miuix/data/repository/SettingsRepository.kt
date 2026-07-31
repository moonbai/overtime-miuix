package com.overtime.miuix.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    // Theme settings
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
    val accentColor: Flow<Int> = context.dataStore.data.map { it[KEY_ACCENT_COLOR] ?: 0xFF3482FF.toInt() }
    val bottomBarStyle: Flow<String> = context.dataStore.data.map { it[KEY_BOTTOM_BAR_STYLE] ?: "ICON_TEXT" }
    val quickSubmit: Flow<Boolean> = context.dataStore.data.map { it[KEY_QUICK_SUBMIT] ?: false }

    // 加班类型配色（统计日历等使用）
    val typeColorWorkday: Flow<Int> = context.dataStore.data.map { it[KEY_TYPE_COLOR_WORKDAY] ?: 0xFF3482FF.toInt() }
    val typeColorWeekend: Flow<Int> = context.dataStore.data.map { it[KEY_TYPE_COLOR_WEEKEND] ?: 0xFF34C759.toInt() }
    val typeColorHoliday: Flow<Int> = context.dataStore.data.map { it[KEY_TYPE_COLOR_HOLIDAY] ?: 0xFFFF7043.toInt() }

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
    val pushDingTalk: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_DINGTALK] ?: "" }
    val pushDingTalkSecret: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_DINGTALK_SECRET] ?: "" }
    val pushFeishu: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_FEISHU] ?: "" }
    val pushFeishuSecret: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_FEISHU_SECRET] ?: "" }
    val pushWeCom: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_WECOM] ?: "" }
    val pushWeComSecret: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_WECOM_SECRET] ?: "" }
    val pushWxPusher: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_WXPUSHER] ?: "" }
    val pushTelegram: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_TELEGRAM] ?: "" }
    val pushTelegramChatId: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_TELEGRAM_CHATID] ?: "" }
    val pushDiscord: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_DISCORD] ?: "" }
    val pushDiscordUsername: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_DISCORD_USERNAME] ?: "" }
    val pushCustom: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_CUSTOM] ?: "" }
    val pushCustomHeaders: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_CUSTOM_HEADERS] ?: "" }

    // Calendar sync
    val calendarSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALENDAR_SYNC] ?: false }

    // WebDAV / Cloud backup
    val webdavEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_WEBDAV_ENABLED] ?: false }
    val webdavUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_URL] ?: "" }
    val webdavUsername: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_USERNAME] ?: "" }
    val webdavPassword: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_PASSWORD] ?: "" }
    val webdavPath: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_PATH] ?: "/overtime_backup/" }

    // Auto backup
    val autoBackupEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_BACKUP_ENABLED] ?: false }
    val autoBackupLocation: Flow<String> = context.dataStore.data.map { it[KEY_AUTO_BACKUP_LOCATION] ?: "local" }

    // Holiday settings
    val holidayDataSource: Flow<String> = context.dataStore.data.map { it[KEY_HOLIDAY_DATA_SOURCE] ?: "TIMOR" }
    val holidayCustomUrl: Flow<String> = context.dataStore.data.map { it[KEY_HOLIDAY_CUSTOM_URL] ?: "" }
    val holidayMxnzpAppId: Flow<String> = context.dataStore.data.map { it[KEY_HOLIDAY_MXNZP_APP_ID] ?: "" }
    val holidayMxnzpAppSecret: Flow<String> = context.dataStore.data.map { it[KEY_HOLIDAY_MXNZP_APP_SECRET] ?: "" }
    val holidayIgnoreHoliday: Flow<Boolean> = context.dataStore.data.map { it[KEY_HOLIDAY_IGNORE_HOLIDAY] ?: false }

    suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[KEY_THEME_MODE] = mode } }
    suspend fun setAccentColor(color: Int) { context.dataStore.edit { it[KEY_ACCENT_COLOR] = color } }
    suspend fun setBottomBarStyle(style: String) { context.dataStore.edit { it[KEY_BOTTOM_BAR_STYLE] = style } }
    suspend fun setQuickSubmit(enabled: Boolean) { context.dataStore.edit { it[KEY_QUICK_SUBMIT] = enabled } }
    suspend fun setTypeColorWorkday(color: Int) { context.dataStore.edit { it[KEY_TYPE_COLOR_WORKDAY] = color } }
    suspend fun setTypeColorWeekend(color: Int) { context.dataStore.edit { it[KEY_TYPE_COLOR_WEEKEND] = color } }
    suspend fun setTypeColorHoliday(color: Int) { context.dataStore.edit { it[KEY_TYPE_COLOR_HOLIDAY] = color } }

    suspend fun setBaseSalary(salary: Double) { context.dataStore.edit { it[KEY_BASE_SALARY] = salary } }
    suspend fun setWorkdayRate(rate: Double) { context.dataStore.edit { it[KEY_WORKDAY_RATE] = rate } }
    suspend fun setWeekendRate(rate: Double) { context.dataStore.edit { it[KEY_WEEKEND_RATE] = rate } }
    suspend fun setHolidayRate(rate: Double) { context.dataStore.edit { it[KEY_HOLIDAY_RATE] = rate } }

    suspend fun setMcpEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_MCP_ENABLED] = enabled } }
    suspend fun setMcpPort(port: Int) { context.dataStore.edit { it[KEY_MCP_PORT] = port } }

    suspend fun setPushEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_PUSH_ENABLED] = enabled } }
    suspend fun setPushChannel(channel: String) { context.dataStore.edit { it[KEY_PUSH_CHANNEL] = channel } }
    suspend fun setPushDingTalk(value: String) { context.dataStore.edit { it[KEY_PUSH_DINGTALK] = value } }
    suspend fun setPushDingTalkSecret(value: String) { context.dataStore.edit { it[KEY_PUSH_DINGTALK_SECRET] = value } }
    suspend fun setPushFeishu(value: String) { context.dataStore.edit { it[KEY_PUSH_FEISHU] = value } }
    suspend fun setPushFeishuSecret(value: String) { context.dataStore.edit { it[KEY_PUSH_FEISHU_SECRET] = value } }
    suspend fun setPushWeCom(value: String) { context.dataStore.edit { it[KEY_PUSH_WECOM] = value } }
    suspend fun setPushWeComSecret(value: String) { context.dataStore.edit { it[KEY_PUSH_WECOM_SECRET] = value } }
    suspend fun setPushWxPusher(value: String) { context.dataStore.edit { it[KEY_PUSH_WXPUSHER] = value } }
    suspend fun setPushTelegram(value: String) { context.dataStore.edit { it[KEY_PUSH_TELEGRAM] = value } }
    suspend fun setPushTelegramChatId(value: String) { context.dataStore.edit { it[KEY_PUSH_TELEGRAM_CHATID] = value } }
    suspend fun setPushDiscord(value: String) { context.dataStore.edit { it[KEY_PUSH_DISCORD] = value } }
    suspend fun setPushDiscordUsername(value: String) { context.dataStore.edit { it[KEY_PUSH_DISCORD_USERNAME] = value } }
    suspend fun setPushCustom(value: String) { context.dataStore.edit { it[KEY_PUSH_CUSTOM] = value } }
    suspend fun setPushCustomHeaders(value: String) { context.dataStore.edit { it[KEY_PUSH_CUSTOM_HEADERS] = value } }

    suspend fun setCalendarSyncEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_CALENDAR_SYNC] = enabled } }

    suspend fun setWebdavEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_WEBDAV_ENABLED] = enabled } }
    suspend fun setWebdavUrl(value: String) { context.dataStore.edit { it[KEY_WEBDAV_URL] = value } }
    suspend fun setWebdavUsername(value: String) { context.dataStore.edit { it[KEY_WEBDAV_USERNAME] = value } }
    suspend fun setWebdavPassword(value: String) { context.dataStore.edit { it[KEY_WEBDAV_PASSWORD] = value } }
    suspend fun setWebdavPath(value: String) { context.dataStore.edit { it[KEY_WEBDAV_PATH] = value } }

    suspend fun setAutoBackupEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = enabled } }
    suspend fun setAutoBackupLocation(location: String) { context.dataStore.edit { it[KEY_AUTO_BACKUP_LOCATION] = location } }

    suspend fun setHolidayDataSource(source: String) { context.dataStore.edit { it[KEY_HOLIDAY_DATA_SOURCE] = source } }
    suspend fun setHolidayCustomUrl(url: String) { context.dataStore.edit { it[KEY_HOLIDAY_CUSTOM_URL] = url } }
    suspend fun setHolidayMxnzpAppId(id: String) { context.dataStore.edit { it[KEY_HOLIDAY_MXNZP_APP_ID] = id } }
    suspend fun setHolidayMxnzpAppSecret(secret: String) { context.dataStore.edit { it[KEY_HOLIDAY_MXNZP_APP_SECRET] = secret } }
    suspend fun setHolidayIgnoreHoliday(ignore: Boolean) { context.dataStore.edit { it[KEY_HOLIDAY_IGNORE_HOLIDAY] = ignore } }

    /**
     * 批量导入设置（用于备份恢复）。仅写入备份中存在的已知键。
     */
    suspend fun importSettings(settings: Map<String, String>) {
        context.dataStore.edit { prefs ->
            settings.forEach { (key, value) ->
                when (key) {
                    KEY_THEME_MODE.name -> prefs[KEY_THEME_MODE] = value
                    KEY_BOTTOM_BAR_STYLE.name -> prefs[KEY_BOTTOM_BAR_STYLE] = value
                    KEY_TYPE_COLOR_WORKDAY.name -> prefs[KEY_TYPE_COLOR_WORKDAY] = value.toIntOrNull() ?: 0xFF3482FF.toInt()
                    KEY_TYPE_COLOR_WEEKEND.name -> prefs[KEY_TYPE_COLOR_WEEKEND] = value.toIntOrNull() ?: 0xFF34C759.toInt()
                    KEY_TYPE_COLOR_HOLIDAY.name -> prefs[KEY_TYPE_COLOR_HOLIDAY] = value.toIntOrNull() ?: 0xFFFF7043.toInt()
                    KEY_BASE_SALARY.name -> prefs[KEY_BASE_SALARY] = value.toDoubleOrNull() ?: 2200.0
                    KEY_WORKDAY_RATE.name -> prefs[KEY_WORKDAY_RATE] = value.toDoubleOrNull() ?: 1.5
                    KEY_WEEKEND_RATE.name -> prefs[KEY_WEEKEND_RATE] = value.toDoubleOrNull() ?: 2.0
                    KEY_HOLIDAY_RATE.name -> prefs[KEY_HOLIDAY_RATE] = value.toDoubleOrNull() ?: 3.0
                    KEY_PUSH_ENABLED.name -> prefs[KEY_PUSH_ENABLED] = value.toBoolean()
                    KEY_PUSH_CHANNEL.name -> prefs[KEY_PUSH_CHANNEL] = value
                    KEY_PUSH_DINGTALK.name -> prefs[KEY_PUSH_DINGTALK] = value
                    KEY_PUSH_DINGTALK_SECRET.name -> prefs[KEY_PUSH_DINGTALK_SECRET] = value
                    KEY_PUSH_FEISHU.name -> prefs[KEY_PUSH_FEISHU] = value
                    KEY_PUSH_FEISHU_SECRET.name -> prefs[KEY_PUSH_FEISHU_SECRET] = value
                    KEY_PUSH_WECOM.name -> prefs[KEY_PUSH_WECOM] = value
                    KEY_PUSH_WECOM_SECRET.name -> prefs[KEY_PUSH_WECOM_SECRET] = value
                    KEY_PUSH_WXPUSHER.name -> prefs[KEY_PUSH_WXPUSHER] = value
                    KEY_PUSH_TELEGRAM.name -> prefs[KEY_PUSH_TELEGRAM] = value
                    KEY_PUSH_TELEGRAM_CHATID.name -> prefs[KEY_PUSH_TELEGRAM_CHATID] = value
                    KEY_PUSH_DISCORD.name -> prefs[KEY_PUSH_DISCORD] = value
                    KEY_PUSH_DISCORD_USERNAME.name -> prefs[KEY_PUSH_DISCORD_USERNAME] = value
                    KEY_PUSH_CUSTOM.name -> prefs[KEY_PUSH_CUSTOM] = value
                    KEY_PUSH_CUSTOM_HEADERS.name -> prefs[KEY_PUSH_CUSTOM_HEADERS] = value
                    KEY_CALENDAR_SYNC.name -> prefs[KEY_CALENDAR_SYNC] = value.toBoolean()
                    KEY_WEBDAV_ENABLED.name -> prefs[KEY_WEBDAV_ENABLED] = value.toBoolean()
                    KEY_WEBDAV_URL.name -> prefs[KEY_WEBDAV_URL] = value
                    KEY_WEBDAV_USERNAME.name -> prefs[KEY_WEBDAV_USERNAME] = value
                    KEY_WEBDAV_PASSWORD.name -> prefs[KEY_WEBDAV_PASSWORD] = value
                    KEY_WEBDAV_PATH.name -> prefs[KEY_WEBDAV_PATH] = value
                    KEY_AUTO_BACKUP_ENABLED.name -> prefs[KEY_AUTO_BACKUP_ENABLED] = value.toBoolean()
                    KEY_AUTO_BACKUP_LOCATION.name -> prefs[KEY_AUTO_BACKUP_LOCATION] = value
                }
            }
        }
    }

    /**
     * 收集全部设置，返回以偏好键名为 key 的字符串映射（用于备份导出）。
     */
    suspend fun exportSettingsMap(): Map<String, String> {
        return mapOf(
            KEY_THEME_MODE.name to themeMode.first(),
            KEY_BOTTOM_BAR_STYLE.name to bottomBarStyle.first(),
            KEY_TYPE_COLOR_WORKDAY.name to typeColorWorkday.first().toString(),
            KEY_TYPE_COLOR_WEEKEND.name to typeColorWeekend.first().toString(),
            KEY_TYPE_COLOR_HOLIDAY.name to typeColorHoliday.first().toString(),
            KEY_BASE_SALARY.name to baseSalary.first().toString(),
            KEY_WORKDAY_RATE.name to workdayRate.first().toString(),
            KEY_WEEKEND_RATE.name to weekendRate.first().toString(),
            KEY_HOLIDAY_RATE.name to holidayRate.first().toString(),
            KEY_PUSH_ENABLED.name to pushEnabled.first().toString(),
            KEY_PUSH_CHANNEL.name to pushChannel.first(),
            KEY_PUSH_DINGTALK.name to pushDingTalk.first(),
            KEY_PUSH_DINGTALK_SECRET.name to pushDingTalkSecret.first(),
            KEY_PUSH_FEISHU.name to pushFeishu.first(),
            KEY_PUSH_FEISHU_SECRET.name to pushFeishuSecret.first(),
            KEY_PUSH_WECOM.name to pushWeCom.first(),
            KEY_PUSH_WECOM_SECRET.name to pushWeComSecret.first(),
            KEY_PUSH_WXPUSHER.name to pushWxPusher.first(),
            KEY_PUSH_TELEGRAM.name to pushTelegram.first(),
            KEY_PUSH_TELEGRAM_CHATID.name to pushTelegramChatId.first(),
            KEY_PUSH_DISCORD.name to pushDiscord.first(),
            KEY_PUSH_DISCORD_USERNAME.name to pushDiscordUsername.first(),
            KEY_PUSH_CUSTOM.name to pushCustom.first(),
            KEY_PUSH_CUSTOM_HEADERS.name to pushCustomHeaders.first(),
            KEY_CALENDAR_SYNC.name to calendarSyncEnabled.first().toString(),
            KEY_WEBDAV_ENABLED.name to webdavEnabled.first().toString(),
            KEY_WEBDAV_URL.name to webdavUrl.first(),
            KEY_WEBDAV_USERNAME.name to webdavUsername.first(),
            KEY_WEBDAV_PASSWORD.name to webdavPassword.first(),
            KEY_WEBDAV_PATH.name to webdavPath.first(),
            KEY_AUTO_BACKUP_ENABLED.name to autoBackupEnabled.first().toString(),
            KEY_AUTO_BACKUP_LOCATION.name to autoBackupLocation.first()
        )
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
        private val KEY_BOTTOM_BAR_STYLE = stringPreferencesKey("bottom_bar_style")
        private val KEY_QUICK_SUBMIT = booleanPreferencesKey("quick_submit")

        private val KEY_TYPE_COLOR_WORKDAY = intPreferencesKey("type_color_workday")
        private val KEY_TYPE_COLOR_WEEKEND = intPreferencesKey("type_color_weekend")
        private val KEY_TYPE_COLOR_HOLIDAY = intPreferencesKey("type_color_holiday")

        private val KEY_BASE_SALARY = doublePreferencesKey("base_salary")
        private val KEY_WORKDAY_RATE = doublePreferencesKey("workday_rate")
        private val KEY_WEEKEND_RATE = doublePreferencesKey("weekend_rate")
        private val KEY_HOLIDAY_RATE = doublePreferencesKey("holiday_rate")

        private val KEY_MCP_ENABLED = booleanPreferencesKey("mcp_enabled")
        private val KEY_MCP_PORT = intPreferencesKey("mcp_port")

        private val KEY_PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        private val KEY_PUSH_CHANNEL = stringPreferencesKey("push_channel")
        private val KEY_PUSH_DINGTALK = stringPreferencesKey("push_dingtalk")
        private val KEY_PUSH_DINGTALK_SECRET = stringPreferencesKey("push_dingtalk_secret")
        private val KEY_PUSH_FEISHU = stringPreferencesKey("push_feishu")
        private val KEY_PUSH_FEISHU_SECRET = stringPreferencesKey("push_feishu_secret")
        private val KEY_PUSH_WECOM = stringPreferencesKey("push_wecom")
        private val KEY_PUSH_WECOM_SECRET = stringPreferencesKey("push_wecom_secret")
        private val KEY_PUSH_WXPUSHER = stringPreferencesKey("push_wxpusher")
        private val KEY_PUSH_TELEGRAM = stringPreferencesKey("push_telegram")
        private val KEY_PUSH_TELEGRAM_CHATID = stringPreferencesKey("push_telegram_chatid")
        private val KEY_PUSH_DISCORD = stringPreferencesKey("push_discord")
        private val KEY_PUSH_DISCORD_USERNAME = stringPreferencesKey("push_discord_username")
        private val KEY_PUSH_CUSTOM = stringPreferencesKey("push_custom")
        private val KEY_PUSH_CUSTOM_HEADERS = stringPreferencesKey("push_custom_headers")

        private val KEY_CALENDAR_SYNC = booleanPreferencesKey("calendar_sync")

        private val KEY_WEBDAV_ENABLED = booleanPreferencesKey("webdav_enabled")
        private val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        private val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        private val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        private val KEY_WEBDAV_PATH = stringPreferencesKey("webdav_path")

        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_AUTO_BACKUP_LOCATION = stringPreferencesKey("auto_backup_location")

        private val KEY_HOLIDAY_DATA_SOURCE = stringPreferencesKey("holiday_data_source")
        private val KEY_HOLIDAY_CUSTOM_URL = stringPreferencesKey("holiday_custom_url")
        private val KEY_HOLIDAY_MXNZP_APP_ID = stringPreferencesKey("holiday_mxnzp_app_id")
        private val KEY_HOLIDAY_MXNZP_APP_SECRET = stringPreferencesKey("holiday_mxnzp_app_secret")
        private val KEY_HOLIDAY_IGNORE_HOLIDAY = booleanPreferencesKey("holiday_ignore_holiday")
    }
}
