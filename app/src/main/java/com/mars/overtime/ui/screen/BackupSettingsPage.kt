package com.mars.overtime.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.ui.snackbar.LocalSnackbarHostState
import com.mars.overtime.ui.snackbar.showCustomToast
import com.mars.overtime.util.BackupData
import com.mars.overtime.util.BackupFileEntry
import com.mars.overtime.util.BackupManager
import com.mars.overtime.util.BackupSource
import com.mars.overtime.util.BackupSources
import com.mars.overtime.util.DataMigrationUtil
import com.mars.overtime.util.FeaturePermissions
import com.mars.overtime.util.WebDavManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream

/** 业务去重键：同日期 + 同类型 + 同时长 + 同请假标记 + 同备注 视为同一条记录 */
private fun recordKey(r: OvertimeRecord): String =
    "${r.date}_${r.type}_${r.durationHours}_${r.isLeave}_${r.note.trim()}"

/** 恢复范围：备份文件中可以选择性恢复的内容 */
private enum class RestoreScope(val label: String) {
    ALL("全部恢复"),
    RECORDS_ONLY("仅恢复记录"),
    SETTINGS_ONLY("仅恢复配置")
}

@Composable
fun BackupSettingsPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    // 目录路径只算一次，避免每次重组都做一次文件系统访问
    val autoBackupDirPath = remember { DataMigrationUtil.getBackupDir(context).absolutePath }

    val records by repository.getAllRecords().collectAsState(initial = emptyList())

    val webdavEnabled by settingsRepository.webdavEnabled.collectAsState(initial = false)
    val webdavUrl by settingsRepository.webdavUrl.collectAsState(initial = "")
    val webdavUsername by settingsRepository.webdavUsername.collectAsState(initial = "")
    val webdavPassword by settingsRepository.webdavPassword.collectAsState(initial = "")
    val webdavPath by settingsRepository.webdavPath.collectAsState(initial = "/overtime_backup/")
    val autoBackupEnabled by settingsRepository.autoBackupEnabled.collectAsState(initial = false)
    val autoBackupLocation by settingsRepository.autoBackupLocation.collectAsState(initial = "local")
    // 导入来源之一：用户通过系统目录选择器指定的自定义目录（本机保存，不随备份导出）
    val customBackupDirUri by settingsRepository.customBackupDirUri.collectAsState(initial = "")

    var webdavUrlText by remember { mutableStateOf(webdavUrl) }
    var webdavUserText by remember { mutableStateOf(webdavUsername) }
    var webdavPassText by remember { mutableStateOf(webdavPassword) }
    var webdavPathText by remember { mutableStateOf(webdavPath) }

    var status by remember { mutableStateOf<String?>(null) }

    // 从目录导入：自动备份目录 / 自定义目录的文件列表与选择弹窗
    var showBackupPicker by remember { mutableStateOf(false) }
    var autoEntries by remember { mutableStateOf<List<BackupFileEntry>>(emptyList()) }
    var customEntries by remember { mutableStateOf<List<BackupFileEntry>>(emptyList()) }
    var customDirReadable by remember { mutableStateOf(true) }

    // 云端恢复：远端文件列表与选择弹窗
    var showRestoreDialog by remember { mutableStateOf(false) }
    var remoteFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingRemote by remember { mutableStateOf(false) }

    // 导入/恢复二次确认：解析完成后暂存，由确认框触发真正写入
    var pendingImport by remember { mutableStateOf<BackupData?>(null) }

    // 恢复范围：全部 / 仅记录 / 仅配置；解析成功后按备份内容自动纠正默认值
    var restoreScope by remember { mutableStateOf(RestoreScope.ALL) }

    LaunchedEffect(webdavUrl) { webdavUrlText = webdavUrl }
    LaunchedEffect(webdavUsername) { webdavUserText = webdavUsername }
    LaunchedEffect(webdavPassword) { webdavPassText = webdavPassword }
    LaunchedEffect(webdavPath) { webdavPathText = webdavPath }

    suspend fun showToast(msg: String) {
        status = msg
        snackbarHostState.showCustomToast(msg)
    }

    /**
     * 本地网络权限只在用户启用 WebDAV / 云端备份那一刻按需申请；启动流程不弹权限框。
     * API < 37 或权限已授予时 missing 为空，直接跳过请求。
     */
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { !it }) {
            scope.launch { showToast("未授予本地网络权限，局域网 WebDAV 可能无法连接") }
        }
    }

    fun ensureLocalNetworkPermission() {
        val missing = FeaturePermissions.missing(context, FeaturePermissions.forLocalNetwork())
        if (missing.isNotEmpty()) localNetworkPermissionLauncher.launch(missing)
    }

    fun buildWebDavConfig(): WebDavManager.WebDavConfig = WebDavManager.WebDavConfig(
        baseUrl = webdavUrlText,
        username = webdavUserText,
        password = webdavPassText,
        remotePath = webdavPathText.ifBlank { "/overtime_backup/" }
    )

    /**
     * 真正执行导入：按业务键去重，仅插入库中不存在的记录，避免重复恢复导致记录翻倍。
     * scope 控制恢复范围：可只恢复记录、只恢复配置或全部恢复。
     */
    suspend fun applyImport(data: BackupData, scope: RestoreScope) {
        var inserted = 0
        if (scope != RestoreScope.SETTINGS_ONLY) {
            val existingKeys = repository.getAllRecords().first().map { recordKey(it) }.toSet()
            data.records.forEach { rec ->
                if (recordKey(rec) !in existingKeys) {
                    repository.insert(rec.copy(id = 0))
                    inserted++
                }
            }
        }
        if (scope != RestoreScope.RECORDS_ONLY && data.settings.isNotEmpty()) {
            settingsRepository.importSettings(data.settings)
        }
        val skipped = data.records.size - inserted
        val msg = when (scope) {
            RestoreScope.ALL -> "导入完成：新增 $inserted 条，跳过 $skipped 条重复，设置已覆盖恢复"
            RestoreScope.RECORDS_ONLY -> "导入完成：新增 $inserted 条，跳过 $skipped 条重复（未改动设置）"
            RestoreScope.SETTINGS_ONLY -> "设置已覆盖恢复（未导入记录）"
        }
        showToast(msg)
    }

    // 从云端下载指定备份文件并恢复记录与设置（支持 ZIP 和旧版 JSON）
    suspend fun restoreFromCloud(fileName: String) {
        showToast("正在下载 $fileName ...")
        val isZip = fileName.endsWith(".zip", ignoreCase = true)
        val localPath = DataMigrationUtil.getBackupFilePath(context, "cloud_restore_tmp" + if (isZip) ".zip" else ".json")
        val ok = WebDavManager.downloadFile(buildWebDavConfig(), fileName, localPath)
        if (!ok) {
            showToast("下载失败：$fileName")
            return
        }
        val data = if (isZip) BackupManager.importZip(localPath) else BackupManager.importData(localPath)
        runCatching { File(localPath).delete() }
        if (data == null) {
            showToast("文件解析失败")
            return
        }
        // 解析成功，交由二次确认框触发真正写入
        pendingImport = data
    }

    // 导出到用户选择的文件（ZIP 格式，包含 records.json + settings.json）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val settings = settingsRepository.exportSettingsMap()
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    val ok = BackupManager.exportZip(records, settings, os)
                    if (!ok) throw Exception("ZIP 打包失败")
                }
                showToast("导出成功（ZIP 包含记录与设置）")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("导出失败：${e.message}")
            }
        }
    }

    // 从用户选择的文件导入（支持 ZIP 和旧版 JSON）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val fileName = uri.lastPathSegment ?: ""
                val data: BackupData? = if (fileName.endsWith(".zip", ignoreCase = true)) {
                    val tmpFile = File(context.cacheDir, "import_tmp.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tmpFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val result = BackupManager.importZip(tmpFile.absolutePath)
                    tmpFile.delete()
                    result
                } else {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    if (json != null) BackupManager.deserialize(json) else null
                }
                if (data == null) {
                    showToast("文件解析失败")
                    return@launch
                }
                pendingImport = data
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("导入失败：${e.message}")
            }
        }
    }

    /**
     * 选择自定义备份目录（SAF 目录树）。持久化读写授权，目录 URI 存到 DataStore，
     * 之后导入时即可直接列出该目录下的备份文件，无需每次手动翻文件选择器。
     */
    val customDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val fullOk = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.isSuccess
        if (!fullOk) {
            // 少数 provider 不授予写权限，导入只需要读权限
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        scope.launch {
            settingsRepository.setCustomBackupDirUri(uri.toString())
            showToast("已设置自定义备份目录：${BackupSources.customDirLabel(uri.toString())}")
        }
    }

    /** 扫描两个来源目录的备份文件（IO 线程），再弹出选择框 */
    suspend fun loadBackupEntries() {
        val dirUri = customBackupDirUri
        val (auto, custom, readable) = withContext(Dispatchers.IO) {
            val a = BackupSources.autoFiles(context)
            val c = BackupSources.customFiles(context, dirUri)
            val ok = dirUri.isBlank() || BackupSources.hasReadPermission(context, dirUri) || c.isNotEmpty()
            Triple(a, c, ok)
        }
        autoEntries = auto
        customEntries = custom
        customDirReadable = readable
        showBackupPicker = true
    }

    /** 解析选中的备份文件，交给已有的二次确认框决定恢复范围 */
    suspend fun restoreFromEntry(entry: BackupFileEntry) {
        showBackupPicker = false
        showToast("正在读取 ${entry.name} ...")
        val data = withContext(Dispatchers.IO) { BackupSources.parse(context, entry) }
        if (data == null) {
            showToast("文件解析失败")
            return
        }
        pendingImport = data
    }

    SettingsScreen(
        title = "备份与恢复",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState,
        dialogs = {
            // 云端恢复：选择远端备份文件
            BackHandler(enabled = showRestoreDialog) { showRestoreDialog = false }
            OverlayDialog(
                show = showRestoreDialog,
                title = "选择要恢复的备份",
                summary = "点击文件名从云端下载并恢复",
                onDismissRequest = { showRestoreDialog = false }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    remoteFiles.take(30).forEach { fileName ->
                        BasicComponent(
                            title = fileName,
                            startAction = { Icon(MiuixIcons.Download, contentDescription = null) },
                            onClick = {
                                showRestoreDialog = false
                                scope.launch { restoreFromCloud(fileName) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        text = "取消",
                        onClick = { showRestoreDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 从目录导入：自动备份目录 / 自定义目录中选一个备份文件
            BackHandler(enabled = showBackupPicker) { showBackupPicker = false }
            OverlayDialog(
                show = showBackupPicker,
                title = "选择备份文件",
                summary = "从自动备份目录或自定义目录中选择要恢复的备份",
                onDismissRequest = { showBackupPicker = false }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "${BackupSource.AUTO.label}（${autoEntries.size}）",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        if (autoEntries.isEmpty()) {
                            BackupPickerHint("暂无自动备份文件，启用自动备份并保存一条记录后会生成")
                        } else {
                            autoEntries.forEach { entry ->
                                BackupEntryRow(entry) { scope.launch { restoreFromEntry(entry) } }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "${BackupSource.CUSTOM.label}（${customEntries.size}）",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        when {
                            customBackupDirUri.isBlank() ->
                                BackupPickerHint("尚未设置自定义目录，点下方「选择目录」指定")
                            !customDirReadable ->
                                BackupPickerHint("目录授权已失效，请点下方「选择目录」重新授权")
                            customEntries.isEmpty() ->
                                BackupPickerHint("该目录下没有 .zip / .json 备份文件")
                            else -> customEntries.forEach { entry ->
                                BackupEntryRow(entry) { scope.launch { restoreFromEntry(entry) } }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            text = "选择目录",
                            onClick = {
                                showBackupPicker = false
                                customDirLauncher.launch(null)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        TextButton(
                            text = "取消",
                            onClick = { showBackupPicker = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 导入/恢复二次确认：支持选择恢复范围（全部 / 仅记录 / 仅配置）
            BackHandler(enabled = pendingImport != null) { pendingImport = null }
            OverlayDialog(
                show = pendingImport != null,
                title = "确认导入",
                summary = if (pendingImport != null) {
                    when (restoreScope) {
                        RestoreScope.ALL ->
                            "将导入 ${pendingImport!!.records.size} 条记录（自动跳过重复），并覆盖恢复设置，确定继续吗？"
                        RestoreScope.RECORDS_ONLY ->
                            "将仅导入 ${pendingImport!!.records.size} 条记录（自动跳过重复），不改动任何设置，确定继续吗？"
                        RestoreScope.SETTINGS_ONLY ->
                            "将仅覆盖恢复设置，不导入任何记录，确定继续吗？"
                    }
                } else "",
                onDismissRequest = { pendingImport = null }
            ) {
                val data = pendingImport
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (data != null) {
                        // 按备份实际内容自动纠正默认范围：缺记录时选仅配置，缺设置时选仅记录
                        LaunchedEffect(data) {
                            restoreScope = when {
                                data.records.isEmpty() && data.settings.isNotEmpty() -> RestoreScope.SETTINGS_ONLY
                                data.settings.isEmpty() && data.records.isNotEmpty() -> RestoreScope.RECORDS_ONLY
                                else -> RestoreScope.ALL
                            }
                        }
                        RestoreScope.entries.forEach { scope ->
                            val enabled = when (scope) {
                                RestoreScope.RECORDS_ONLY -> data.records.isNotEmpty()
                                RestoreScope.SETTINGS_ONLY -> data.settings.isNotEmpty()
                                RestoreScope.ALL -> data.records.isNotEmpty() || data.settings.isNotEmpty()
                            }
                            BasicComponent(
                                title = scope.label,
                                summary = when (scope) {
                                    RestoreScope.ALL -> "记录与设置全部恢复"
                                    RestoreScope.RECORDS_ONLY -> "只导入记录，保留当前设置"
                                    RestoreScope.SETTINGS_ONLY -> "只覆盖设置，保留当前记录"
                                },
                                endActions = if (restoreScope == scope) {
                                    { Icon(MiuixIcons.Ok, contentDescription = "已选择") }
                                } else null,
                                enabled = enabled,
                                onClick = { restoreScope = scope }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { pendingImport = null },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            text = "导入",
                            onClick = {
                                val pending = pendingImport
                                pendingImport = null
                                pending?.let { scope.launch { applyImport(it, restoreScope) } }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) {
        status?.let { message ->
            item { SettingsStatusText(text = message) }
        }

        item {
            SettingsGroup(title = "本地备份") {
                SettingsActionItem(
                    title = "导出数据",
                    summary = "将记录与设置导出为 ZIP 文件",
                    icon = MiuixIcons.Download,
                    onClick = {
                        val name = DataMigrationUtil.generateBackupFileName()
                        exportLauncher.launch(name)
                    }
                )
                SettingsActionItem(
                    title = "导入数据",
                    summary = "从任意位置的 ZIP / JSON 恢复记录与设置（自动去重）",
                    icon = MiuixIcons.Import,
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/json", "*/*")) }
                )
                SettingsActionItem(
                    title = "从备份目录恢复",
                    summary = "列出自动备份目录与自定义目录中的备份文件，直接选择恢复",
                    icon = MiuixIcons.Backup,
                    onClick = { scope.launch { loadBackupEntries() } }
                )
                SettingsActionItem(
                    title = "自定义备份目录",
                    summary = if (customBackupDirUri.isBlank())
                        "未设置；点此选择一个目录作为导入来源"
                    else
                        "当前：${BackupSources.customDirLabel(customBackupDirUri)}（点击更换）",
                    icon = MiuixIcons.Folder,
                    onClick = { customDirLauncher.launch(null) }
                )
                if (customBackupDirUri.isNotBlank()) {
                    SettingsActionItem(
                        title = "清除自定义目录",
                        summary = "清除后仅从自动备份目录导入",
                        icon = MiuixIcons.Delete,
                        onClick = {
                            scope.launch {
                                settingsRepository.setCustomBackupDirUri("")
                                showToast("已清除自定义备份目录")
                            }
                        }
                    )
                }
                SettingsNote(
                    text = "自动备份目录：$autoBackupDirPath\n" +
                        "自定义目录仅作为导入来源；自动备份仍写入上方目录。"
                )
            }
        }

        item {
            SettingsGroup(title = "自动备份") {
                SettingsSwitchItem(
                    title = "启用自动备份",
                    summary = "新增记录时自动保存到本地或云端",
                    checked = autoBackupEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setAutoBackupEnabled(enabled) }
                        if (enabled && autoBackupLocation == "cloud") ensureLocalNetworkPermission()
                    }
                )
                if (autoBackupEnabled) {
                    val locationItems = remember {
                        listOf(
                            DropdownItem(text = "仅本地"),
                            DropdownItem(text = "本地 + WebDAV 云端")
                        )
                    }
                    val locationSelected = remember(autoBackupLocation) {
                        if (autoBackupLocation == "cloud") 1 else 0
                    }
                    OverlaySpinnerPreference(
                        items = locationItems,
                        selectedIndex = locationSelected,
                        title = "备份位置",
                        summary = "选择备份存储位置",
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                settingsRepository.setAutoBackupLocation(if (index == 1) "cloud" else "local")
                            }
                            if (index == 1) ensureLocalNetworkPermission()
                        }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "WebDAV 云端同步") {
                SettingsSwitchItem(
                    title = "启用 WebDAV",
                    summary = "配置 WebDAV 服务器进行备份同步",
                    checked = webdavEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setWebdavEnabled(enabled) }
                        if (enabled) ensureLocalNetworkPermission()
                    }
                )
                if (webdavEnabled) {
                    SettingsTextField(
                        value = webdavUrlText,
                        onValueChange = {
                            webdavUrlText = it
                            scope.launch { settingsRepository.setWebdavUrl(it) }
                        },
                        label = "服务器地址"
                    )
                    SettingsTextField(
                        value = webdavUserText,
                        onValueChange = {
                            webdavUserText = it
                            scope.launch { settingsRepository.setWebdavUsername(it) }
                        },
                        label = "用户名"
                    )
                    SettingsTextField(
                        value = webdavPassText,
                        onValueChange = {
                            webdavPassText = it
                            scope.launch { settingsRepository.setWebdavPassword(it) }
                        },
                        label = "密码",
                        isPassword = true
                    )
                    SettingsTextField(
                        value = webdavPathText,
                        onValueChange = {
                            webdavPathText = it
                            scope.launch { settingsRepository.setWebdavPath(it) }
                        },
                        label = "远程路径"
                    )
                    SettingsButtonRow(
                        firstText = "测试连接",
                        onFirstClick = {
                            scope.launch {
                                val ok = WebDavManager.testConnection(buildWebDavConfig())
                                showToast(if (ok) "连接成功" else "连接失败")
                            }
                        },
                        secondText = "立即上传",
                        onSecondClick = {
                            scope.launch {
                                val settings = settingsRepository.exportSettingsMap().toMutableMap().apply {
                                    this["auto_backup_enabled"] = "true"
                                    this["auto_backup_location"] = "cloud"
                                }
                                val fileName = DataMigrationUtil.generateBackupFileName()
                                val localPath = DataMigrationUtil.getBackupFilePath(context, fileName)
                                val okExport = BackupManager.exportZip(records, settings, localPath)
                                if (!okExport) {
                                    showToast("生成本地备份失败")
                                    return@launch
                                }
                                val okUpload = WebDavManager.uploadFile(buildWebDavConfig(), localPath, fileName)
                                showToast(if (okUpload) "上传成功" else "上传失败")
                            }
                        }
                    )
                    SettingsActionItem(
                        title = "从云端恢复",
                        summary = "列出 WebDAV 上的备份文件并下载恢复",
                        icon = MiuixIcons.Download,
                        onClick = {
                            if (!loadingRemote) {
                                loadingRemote = true
                                scope.launch {
                                    showToast("正在获取云端备份列表 ...")
                                    val files = WebDavManager.listFiles(buildWebDavConfig())
                                    remoteFiles = files.sortedDescending()
                                    loadingRemote = false
                                    if (remoteFiles.isEmpty()) {
                                        showToast("云端没有可用的备份文件")
                                    } else {
                                        showRestoreDialog = true
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 备份文件选择弹窗里的一行：文件名 + 大小 / 修改时间 */
@Composable
private fun BackupEntryRow(entry: BackupFileEntry, onClick: () -> Unit) {
    BasicComponent(
        title = entry.name,
        summary = entry.info,
        startAction = {
            Icon(
                MiuixIcons.Backup,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = onClick
    )
}

/** 备份目录分组下的空状态 / 授权失效提示 */
@Composable
private fun BackupPickerHint(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
