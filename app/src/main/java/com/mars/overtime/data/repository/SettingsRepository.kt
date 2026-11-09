package com.mars.overtime.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mars.overtime.util.HolidayDataSource
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

    // 删除二次确认开关（关闭后点删除立即移除记录，配合可撤销通知条兜底）
    val deleteConfirmEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DELETE_CONFIRM] ?: true }

    // 首页小组件：启用并按此顺序展示（逗号分隔的组件 id）。
    // 读取时过滤掉未知 id（版本演进后旧配置不崩），空配置回退默认值。
    // 仅用于旧备份导入/迁移兜底，新布局由 [homeLayout] 管理。
    val homeWidgets: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val saved = prefs[KEY_HOME_WIDGETS]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && HomeWidgetIds.contains(it) }
            .orEmpty()
        if (saved.isEmpty()) DEFAULT_HOME_LAYOUT.map { it.id } else saved
    }

    // 首页小组件布局：带跨度的有序列表（id + spanX + spanY）。跨度由组件目录固定，
    // 读取时由 ui 层 normalizeLayout 校正，保留字段仅为兼容旧版本与备份导入。
    // 优先读新格式 `home_layout`；缺失时回退旧 `home_widgets`（全部按 1x1 处理），
    // 再缺失回退默认布局。写入统一走 [setHomeLayout] 落到 `home_layout`。
    val homeLayout: Flow<List<WidgetPlacement>> = context.dataStore.data.map { prefs ->
        val layoutRaw = prefs[KEY_HOME_LAYOUT]
        val items = if (layoutRaw != null) {
            parseLayout(layoutRaw)
        } else {
            val legacy = prefs[KEY_HOME_WIDGETS]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && HomeWidgetIds.contains(it) }
                .orEmpty()
            if (legacy.isEmpty()) DEFAULT_HOME_LAYOUT else legacy.map { WidgetPlacement(it) }
        }
        if (items.isEmpty()) DEFAULT_HOME_LAYOUT else items
    }

    // 首页概览卡展示模式：total=总时长/薪资/记录数；by_type=净时长+分类型工时
    val homeOverviewMode: Flow<String> = context.dataStore.data.map { it[KEY_HOME_OVERVIEW_MODE] ?: "total" }

    // Monet 动态取色（使用系统壁纸动态配色）
    val monetEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_MONET_ENABLED] ?: false }

    // 悬浮底栏（FloatingNavigationBar）
    val useFloatingNav: Flow<Boolean> = context.dataStore.data.map { it[KEY_FLOATING_NAV] ?: false }

    // 悬浮底栏与屏幕底部的距离（dp，不含系统导航栏安全区），范围 0..64
    val floatingNavBottomOffset: Flow<Int> = context.dataStore.data
        .map { (it[KEY_FLOATING_NAV_BOTTOM_OFFSET] ?: DEFAULT_FLOATING_NAV_BOTTOM_OFFSET).coerceIn(0, 64) }

    // 预测性返回总开关：关闭后返回不做任何动画（瞬时返回），也禁用系统返回手势预览
    val predictiveBackEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_PREDICTIVE_BACK_ENABLED] ?: DEFAULT_PREDICTIVE_BACK_ENABLED }

    // 页面返回转场动画（预测性返回手势预览复用同一组规格），仅在总开关开启时生效
    val predictiveBackTransition: Flow<String> = context.dataStore.data
        .map { it[KEY_PREDICTIVE_BACK_TRANSITION] ?: DEFAULT_PREDICTIVE_BACK_TRANSITION }

    // 横向返回的退出方向：跟随手势 / 恒定向右 / 恒定向左，仅在动画为 SLIDE_HORIZONTAL 时生效
    val backExitDirection: Flow<String> = context.dataStore.data
        .map { it[KEY_BACK_EXIT_DIRECTION] ?: DEFAULT_BACK_EXIT_DIRECTION }

    // 薪资计算口径：月计薪天数 / 每日工时，可在「基础设置」中调整
    val workingDaysPerMonth: Flow<Double> = context.dataStore.data
        .map { it[KEY_WORKING_DAYS_PER_MONTH] ?: DEFAULT_WORKING_DAYS_PER_MONTH }
    val hoursPerDay: Flow<Double> = context.dataStore.data
        .map { it[KEY_HOURS_PER_DAY] ?: DEFAULT_HOURS_PER_DAY }

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
    // Timor 服务已下线：旧配置 / 旧备份里的 TIMOR 与其它未知值统一迁移到默认数据源，
    // 避免各页面 valueOf 失败后回退结果不一致。
    val holidayDataSource: Flow<String> = context.dataStore.data.map { prefs ->
        val saved = prefs[KEY_HOLIDAY_DATA_SOURCE]
        if (saved != null && HolidayDataSource.entries.any { it.name == saved }) saved
        else HolidayDataSource.UAPIS.name
    }
    val holidayCustomUrl: Flow<String> = context.dataStore.data.map { it[KEY_HOLIDAY_CUSTOM_URL] ?: "" }
    val holidayMxnzpAppId: Flow<String> = context.dataStore.data.map { it[KEY_HOLIDAY_MXNZP_APP_ID] ?: "" }
    val holidayMxnzpAppSecret: Flow<String> = context.dataStore.data.map { it[KEY_HOLIDAY_MXNZP_APP_SECRET] ?: "" }
    val holidayIgnoreHoliday: Flow<Boolean> = context.dataStore.data.map { it[KEY_HOLIDAY_IGNORE_HOLIDAY] ?: false }

    suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[KEY_THEME_MODE] = mode } }
    suspend fun setAccentColor(color: Int) { context.dataStore.edit { it[KEY_ACCENT_COLOR] = color } }
    suspend fun setBottomBarStyle(style: String) { context.dataStore.edit { it[KEY_BOTTOM_BAR_STYLE] = style } }
    suspend fun setQuickSubmit(enabled: Boolean) { context.dataStore.edit { it[KEY_QUICK_SUBMIT] = enabled } }
    suspend fun setDeleteConfirmEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_DELETE_CONFIRM] = enabled } }
    suspend fun setHomeWidgets(ids: List<String>) {
        context.dataStore.edit { it[KEY_HOME_WIDGETS] = ids.joinToString(",") }
    }
    suspend fun setHomeLayout(items: List<WidgetPlacement>) {
        context.dataStore.edit { it[KEY_HOME_LAYOUT] = serializeLayout(items) }
    }
    suspend fun setHomeOverviewMode(mode: String) {
        context.dataStore.edit { it[KEY_HOME_OVERVIEW_MODE] = if (mode == "by_type") "by_type" else "total" }
    }
    suspend fun setMonetEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_MONET_ENABLED] = enabled } }
    suspend fun setUseFloatingNav(enabled: Boolean) { context.dataStore.edit { it[KEY_FLOATING_NAV] = enabled } }
    suspend fun setFloatingNavBottomOffset(dp: Int) {
        context.dataStore.edit { it[KEY_FLOATING_NAV_BOTTOM_OFFSET] = dp.coerceIn(0, 64) }
    }
    suspend fun setPredictiveBackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PREDICTIVE_BACK_ENABLED] = enabled }
    }
    suspend fun setPredictiveBackTransition(mode: String) {
        context.dataStore.edit { it[KEY_PREDICTIVE_BACK_TRANSITION] = mode }
    }
    suspend fun setBackExitDirection(direction: String) {
        context.dataStore.edit { it[KEY_BACK_EXIT_DIRECTION] = direction }
    }
    suspend fun setWorkingDaysPerMonth(days: Double) {
        context.dataStore.edit { it[KEY_WORKING_DAYS_PER_MONTH] = days }
    }
    suspend fun setHoursPerDay(hours: Double) {
        context.dataStore.edit { it[KEY_HOURS_PER_DAY] = hours }
    }
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
                    KEY_MONET_ENABLED.name -> prefs[KEY_MONET_ENABLED] = value.toBoolean()
                    KEY_FLOATING_NAV.name -> prefs[KEY_FLOATING_NAV] = value.toBoolean()
                    KEY_FLOATING_NAV_BOTTOM_OFFSET.name ->
                        prefs[KEY_FLOATING_NAV_BOTTOM_OFFSET] =
                            (value.toIntOrNull() ?: DEFAULT_FLOATING_NAV_BOTTOM_OFFSET).coerceIn(0, 64)
                    KEY_PREDICTIVE_BACK_TRANSITION.name ->
                        prefs[KEY_PREDICTIVE_BACK_TRANSITION] = value
                    KEY_BACK_EXIT_DIRECTION.name ->
                        prefs[KEY_BACK_EXIT_DIRECTION] = value
                    KEY_PREDICTIVE_BACK_ENABLED.name ->
                        prefs[KEY_PREDICTIVE_BACK_ENABLED] = value.toBoolean()
                    KEY_WORKING_DAYS_PER_MONTH.name ->
                        prefs[KEY_WORKING_DAYS_PER_MONTH] =
                            (value.toDoubleOrNull() ?: DEFAULT_WORKING_DAYS_PER_MONTH).coerceAtLeast(0.01)
                    KEY_HOURS_PER_DAY.name ->
                        prefs[KEY_HOURS_PER_DAY] =
                            (value.toDoubleOrNull() ?: DEFAULT_HOURS_PER_DAY).coerceAtLeast(0.01)
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
                    KEY_DELETE_CONFIRM.name -> prefs[KEY_DELETE_CONFIRM] = value.toBoolean()
                    KEY_HOME_LAYOUT.name -> prefs[KEY_HOME_LAYOUT] = value
                    // 兼容旧备份：旧 `home_widgets`（逗号分隔 id）转换为新布局（全部 1x1）
                    KEY_HOME_WIDGETS.name -> prefs[KEY_HOME_LAYOUT] = serializeLayout(parseLayout(value))
                    KEY_HOME_OVERVIEW_MODE.name -> prefs[KEY_HOME_OVERVIEW_MODE] = value
                    KEY_CALENDAR_SYNC.name -> prefs[KEY_CALENDAR_SYNC] = value.toBoolean()
                    KEY_WEBDAV_ENABLED.name -> prefs[KEY_WEBDAV_ENABLED] = value.toBoolean()
                    KEY_WEBDAV_URL.name -> prefs[KEY_WEBDAV_URL] = value
                    KEY_WEBDAV_USERNAME.name -> prefs[KEY_WEBDAV_USERNAME] = value
                    KEY_WEBDAV_PASSWORD.name -> prefs[KEY_WEBDAV_PASSWORD] = value
                    KEY_WEBDAV_PATH.name -> prefs[KEY_WEBDAV_PATH] = value
                    KEY_AUTO_BACKUP_ENABLED.name -> prefs[KEY_AUTO_BACKUP_ENABLED] = value.toBoolean()
                    KEY_AUTO_BACKUP_LOCATION.name -> prefs[KEY_AUTO_BACKUP_LOCATION] = value
                    // 节假日/云端数据源配置
                    KEY_HOLIDAY_DATA_SOURCE.name -> prefs[KEY_HOLIDAY_DATA_SOURCE] = value
                    KEY_HOLIDAY_CUSTOM_URL.name -> prefs[KEY_HOLIDAY_CUSTOM_URL] = value
                    KEY_HOLIDAY_MXNZP_APP_ID.name -> prefs[KEY_HOLIDAY_MXNZP_APP_ID] = value
                    KEY_HOLIDAY_MXNZP_APP_SECRET.name -> prefs[KEY_HOLIDAY_MXNZP_APP_SECRET] = value
                    KEY_HOLIDAY_IGNORE_HOLIDAY.name -> prefs[KEY_HOLIDAY_IGNORE_HOLIDAY] = value.toBoolean()
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
            KEY_MONET_ENABLED.name to monetEnabled.first().toString(),
            KEY_FLOATING_NAV.name to useFloatingNav.first().toString(),
            KEY_FLOATING_NAV_BOTTOM_OFFSET.name to floatingNavBottomOffset.first().toString(),
            KEY_PREDICTIVE_BACK_TRANSITION.name to predictiveBackTransition.first(),
            KEY_BACK_EXIT_DIRECTION.name to backExitDirection.first(),
            KEY_PREDICTIVE_BACK_ENABLED.name to predictiveBackEnabled.first().toString(),
            KEY_WORKING_DAYS_PER_MONTH.name to workingDaysPerMonth.first().toString(),
            KEY_HOURS_PER_DAY.name to hoursPerDay.first().toString(),
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
            KEY_DELETE_CONFIRM.name to deleteConfirmEnabled.first().toString(),
            KEY_HOME_LAYOUT.name to serializeLayout(homeLayout.first()),
            KEY_HOME_OVERVIEW_MODE.name to homeOverviewMode.first(),
            KEY_CALENDAR_SYNC.name to calendarSyncEnabled.first().toString(),
            KEY_WEBDAV_ENABLED.name to webdavEnabled.first().toString(),
            KEY_WEBDAV_URL.name to webdavUrl.first(),
            KEY_WEBDAV_USERNAME.name to webdavUsername.first(),
            KEY_WEBDAV_PASSWORD.name to webdavPassword.first(),
            KEY_WEBDAV_PATH.name to webdavPath.first(),
            KEY_AUTO_BACKUP_ENABLED.name to autoBackupEnabled.first().toString(),
            KEY_AUTO_BACKUP_LOCATION.name to autoBackupLocation.first(),
            KEY_HOLIDAY_DATA_SOURCE.name to holidayDataSource.first(),
            KEY_HOLIDAY_CUSTOM_URL.name to holidayCustomUrl.first(),
            KEY_HOLIDAY_MXNZP_APP_ID.name to holidayMxnzpAppId.first(),
            KEY_HOLIDAY_MXNZP_APP_SECRET.name to holidayMxnzpAppSecret.first(),
            KEY_HOLIDAY_IGNORE_HOLIDAY.name to holidayIgnoreHoliday.first().toString()
        )
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
        private val KEY_BOTTOM_BAR_STYLE = stringPreferencesKey("bottom_bar_style")
        private val KEY_QUICK_SUBMIT = booleanPreferencesKey("quick_submit")
        private val KEY_DELETE_CONFIRM = booleanPreferencesKey("delete_confirm")
        private val KEY_HOME_WIDGETS = stringPreferencesKey("home_widgets")
        private val KEY_HOME_LAYOUT = stringPreferencesKey("home_layout")
        private val KEY_HOME_OVERVIEW_MODE = stringPreferencesKey("home_overview_mode")

        private val KEY_MONET_ENABLED = booleanPreferencesKey("monet_enabled")
        private val KEY_FLOATING_NAV = booleanPreferencesKey("floating_nav")
        private val KEY_FLOATING_NAV_BOTTOM_OFFSET = intPreferencesKey("floating_nav_bottom_offset")
        private const val DEFAULT_FLOATING_NAV_BOTTOM_OFFSET = 5
        private val KEY_PREDICTIVE_BACK_ENABLED = booleanPreferencesKey("predictive_back_enabled")
        private const val DEFAULT_PREDICTIVE_BACK_ENABLED = true
        private val KEY_PREDICTIVE_BACK_TRANSITION = stringPreferencesKey("predictive_back_transition")
        private const val DEFAULT_PREDICTIVE_BACK_TRANSITION = "SCALE_FADE"
        private val KEY_BACK_EXIT_DIRECTION = stringPreferencesKey("back_exit_direction")
        private const val DEFAULT_BACK_EXIT_DIRECTION = "FOLLOW_GESTURE"
        private val KEY_WORKING_DAYS_PER_MONTH = doublePreferencesKey("working_days_per_month")
        private val KEY_HOURS_PER_DAY = doublePreferencesKey("hours_per_day")
        private const val DEFAULT_WORKING_DAYS_PER_MONTH = 21.75
        private const val DEFAULT_HOURS_PER_DAY = 8.0

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

/**
 * 首页小组件全量 id 集合，与 ui/screen/HomeWidgetCore 的目录保持一致。
 * 顶层声明，供 parseLayout / 各页面导入共用。
 */
val HomeWidgetIds = setOf(
    "month_hours", "last_month_hours", "month_salary",
    "year_total", "month_leave", "latest_record",
    "month_type", "month_daily", "year_month", "type_donut"
)

/** 未配置时的默认布局：当月工时 / 本月薪资 / 上月工时 / 年度累计（均 1x1） */
val DEFAULT_HOME_LAYOUT = listOf(
    WidgetPlacement("month_hours"),
    WidgetPlacement("month_salary"),
    WidgetPlacement("last_month_hours"),
    WidgetPlacement("year_total")
)

/**
 * 首页单个小组件的布局单元：组件 id + 网格跨度（列 spanX、行 spanY）。
 * 跨度用于 2 列网格排版；值由组件目录固定，用户不可调整。
 */
data class WidgetPlacement(
    val id: String,
    val spanX: Int = 1,
    val spanY: Int = 1
) {
    /** 归一化跨度，避免存储脏值导致排版异常 */
    fun normalized(): WidgetPlacement = WidgetPlacement(
        id = id,
        spanX = spanX.coerceIn(1, 2),
        spanY = spanY.coerceIn(1, 2)
    )
}

/** 将布局序列化为持久化字符串：`id:spanX:spanY` 以 `;` 连接 */
fun serializeLayout(items: List<WidgetPlacement>): String =
    items.joinToString(";") { "${it.id}:${it.spanX}:${it.spanY}" }

/**
 * 解析布局字符串，兼容三种来源：
 * - 新格式 `id:spanX:spanY`（以 `;` 分隔）；
 * - 旧格式 `id,id,...`（逗号分隔，全部按 1x1 处理）；
 * - 单 id。
 * 未知 id 会被过滤，保证旧配置/备份升级后不崩。
 */
fun parseLayout(raw: String?): List<WidgetPlacement> {
    if (raw.isNullOrBlank()) return emptyList()
    val segments = raw.split(';', ',')
    return segments.mapNotNull { seg ->
        val t = seg.trim()
        if (t.isEmpty()) return@mapNotNull null
        when {
            t.contains(':') -> {
                val p = t.split(':')
                val id = p[0].trim()
                if (!HomeWidgetIds.contains(id)) null
                else WidgetPlacement(
                    id,
                    p.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 2) ?: 1,
                    p.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 2) ?: 1
                ).normalized()
            }
            HomeWidgetIds.contains(t) -> WidgetPlacement(t)
            else -> null
        }
    }
}
