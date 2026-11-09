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
import androidx.compose.ui.graphics.vector.ImageVector
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

/** 导入来源：点「导入数据」后在同一个弹窗里切换，避免页面上堆一排重复入口 */
private enum class ImportSource(val label: String) {
    DEVICE("设备文件"),
    AUTO("自动备份目录"),
    CUSTOM("自定义目录"),
    CLOUD("WebDAV 云端")
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

    // 统一导入弹窗：null = 关闭，非 null = 当前选中的来源
    var importSource by remember { mutableStateOf<ImportSource?>(null) }
    var autoEntries by remember { mutableStateOf<List<BackupFileEntry>>(emptyList()) }
    var customEntries by remember { mutableStateOf<List<BackupFileEntry>>(emptyList()) }
    var customDirReadable by remember { mutableStateOf(true) }
    var remoteFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingRemote by remember { mutableStateOf(false) }

    // 云端来源仅在 WebDAV 已启用且填好服务器地址与账号时显示
    val cloudConfigured = webdavEnabled && webdavUrl.isNotBlank() && webdavUsername.isNotBlank()

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
        if (fileName.isBlank()) {
            showToast("备份文件名无效")
            return
        }
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
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        val ok = BackupManager.exportZip(records, settings, os)
                        if (!ok) throw Exception("ZIP 打包失败")
                    }
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
        if (uri == null) {
            // 取消选择后回到导入弹窗，不必重新点一次「导入数据」
            importSource = ImportSource.AUTO
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val data: BackupData? = withContext(Dispatchers.IO) {
                    val fileName = uri.lastPathSegment ?: ""
                    if (fileName.endsWith(".zip", ignoreCase = true)) {
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

    /** 扫描自动备份目录与自定义目录的备份文件（IO 线程） */
    suspend fun refreshLocalEntries(dirUri: String) {
        val (auto, custom, readable) = withContext(Dispatchers.IO) {
            val a = BackupSources.autoFiles(context)
            val c = BackupSources.customFiles(context, dirUri)
            val ok = dirUri.isBlank() || BackupSources.hasReadPermission(context, dirUri) || c.isNotEmpty()
            Triple(a, c, ok)
        }
        autoEntries = auto
        customEntries = custom
        customDirReadable = readable
    }

    /** 点「导入数据」：先扫描本地两个目录，再打开统一导入弹窗 */
    suspend fun openImportDialog() {
        refreshLocalEntries(customBackupDirUri)
        importSource = ImportSource.AUTO
    }

    /** 切到云端来源时拉取 WebDAV 上的备份文件列表 */
    suspend fun loadRemoteFiles() {
        if (loadingRemote) return
        loadingRemote = true
        showToast("正在获取云端备份列表 ...")
        val files = try {
            withContext(Dispatchers.IO) { WebDavManager.listFiles(buildWebDavConfig()) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList<String>()
        }
        // 兜底：任何来源都不允许空白文件名进入列表（旧数据/异常服务器）
        remoteFiles = files.filter { it.isNotBlank() }.sortedDescending()
        loadingRemote = false
        if (remoteFiles.isEmpty()) showToast("云端没有可用的备份文件")
    }

    /** 解析选中的备份文件，交给已有的二次确认框决定恢复范围 */
    suspend fun restoreFromEntry(entry: BackupFileEntry) {
        importSource = null
        showToast("正在读取 ${entry.name} ...")
        val data = withContext(Dispatchers.IO) { BackupSources.parse(context, entry) }
        if (data == null) {
            showToast("文件解析失败")
            return
        }
        pendingImport = data
    }

    /**
     * 选择自定义备份目录（SAF 目录树）。持久化读写授权，目录 URI 存到 DataStore；
     * 选完自动回到导入弹窗的「自定义目录」来源并刷新文件列表。
     */
    val customDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) {
            // 取消选目录后回到自定义目录来源
            importSource = ImportSource.CUSTOM
            return@rememberLauncherForActivityResult
        }
        val dirUri = uri.toString()
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
            settingsRepository.setCustomBackupDirUri(dirUri)
            refreshLocalEntries(dirUri)
            importSource = ImportSource.CUSTOM
            showToast("已设置自定义备份目录：${BackupSources.customDirLabel(dirUri)}")
        }
    }

    SettingsScreen(
        title = "备份与恢复",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState,
        dialogs = {
            // 导入来源统一在下方「导入数据」弹窗里选择（含云端恢复）
            // 统一导入弹窗：设备文件 / 自动备份目录 / 自定义目录 / WebDAV 云端
            BackHandler(enabled = importSource != null) { importSource = null }
            OverlayDialog(
                show = importSource != null,
                title = "导入数据",
                summary = "选择备份来源，再点选要恢复的文件",
                onDismissRequest = { importSource = null }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "导入来源",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        ImportSourceRow(
                            title = ImportSource.DEVICE.label,
                            summary = "从设备任意位置选择 ZIP / JSON",
                            icon = MiuixIcons.Import,
                            selected = false,
                            onClick = {
                                importSource = null
                                importLauncher.launch(arrayOf("application/zip", "application/json", "*/*"))
                            }
                        )
                        ImportSourceRow(
                            title = ImportSource.AUTO.label,
                            summary = "${autoEntries.size} 个备份 · 应用自动备份目录",
                            icon = MiuixIcons.Backup,
                            selected = importSource == ImportSource.AUTO,
                            onClick = { importSource = ImportSource.AUTO }
                        )
                        ImportSourceRow(
                            title = ImportSource.CUSTOM.label,
                            summary = if (customBackupDirUri.isBlank()) "未设置，可在下方选择目录"
                            else "${BackupSources.customDirLabel(customBackupDirUri)} · ${customEntries.size} 个备份",
                            icon = MiuixIcons.Folder,
                            selected = importSource == ImportSource.CUSTOM,
                            onClick = { importSource = ImportSource.CUSTOM }
                        )
                        if (cloudConfigured) {
                            ImportSourceRow(
                                title = ImportSource.CLOUD.label,
                                summary = "${remoteFiles.size} 个备份 · WebDAV 服务器",
                                icon = MiuixIcons.UploadCloud,
                                selected = importSource == ImportSource.CLOUD,
                                onClick = {
                                    importSource = ImportSource.CLOUD
                                    scope.launch { loadRemoteFiles() }
                                }
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "备份文件",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        when (importSource) {
                            ImportSource.AUTO -> {
                                BackupPickerHint("目录：$autoBackupDirPath")
                                if (autoEntries.isEmpty()) {
                                    BackupPickerHint("暂无自动备份文件，启用自动备份并保存一条记录后会生成")
                                } else {
                                    autoEntries.forEach { entry ->
                                        BackupEntryRow(entry) { scope.launch { restoreFromEntry(entry) } }
                                    }
                                }
                            }
                            ImportSource.CUSTOM -> {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(
                                        text = if (customBackupDirUri.isBlank()) "选择目录" else "更换目录",
                                        onClick = {
                                            importSource = null
                                            customDirLauncher.launch(null)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (customBackupDirUri.isNotBlank()) {
                                        TextButton(
                                            text = "清除",
                                            onClick = {
                                                scope.launch {
                                                    settingsRepository.setCustomBackupDirUri("")
                                                    refreshLocalEntries("")
                                                    showToast("已清除自定义备份目录")
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                when {
                                    customBackupDirUri.isBlank() ->
                                        BackupPickerHint("尚未设置自定义目录，点上方「选择目录」指定")
                                    !customDirReadable ->
                                        BackupPickerHint("目录授权已失效，请点上方「更换目录」重新授权")
                                    customEntries.isEmpty() ->
                                        BackupPickerHint("该目录下没有 .zip / .json 备份文件")
                                    else -> customEntries.forEach { entry ->
                                        BackupEntryRow(entry) { scope.launch { restoreFromEntry(entry) } }
                                    }
                                }
                            }
                            ImportSource.CLOUD -> {
                                when {
                                    !cloudConfigured -> BackupPickerHint("未配置 WebDAV 账号信息")
                                    loadingRemote -> BackupPickerHint("正在获取云端备份列表 ...")
                                    remoteFiles.isEmpty() -> BackupPickerHint("云端没有可用的备份文件")
                                    else -> remoteFiles.take(30).forEach { fileName ->
                                        BasicComponent(
                                            title = fileName,
                                            startAction = {
                                                Icon(
                                                    MiuixIcons.Download,
                                                    contentDescription = null,
                                                    tint = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            onClick = {
                                                importSource = null
                                                scope.launch { restoreFromCloud(fileName) }
                                            }
                                        )
                                    }
                                }
                            }
                            ImportSource.DEVICE, null ->
                                BackupPickerHint("点上方「设备文件」从任意位置选择备份")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        text = "取消",
                        onClick = { importSource = null },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    summary = "从设备文件 / 自动备份目录 / 自定义目录 / WebDAV 云端选择备份恢复",
                    icon = MiuixIcons.Import,
                    onClick = { scope.launch { openImportDialog() } }
                )
                SettingsNote(
                    text = "自动备份目录：$autoBackupDirPath\n" +
                        "自定义目录与云端来源在「导入数据」弹窗内设置。"
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
                    SettingsNote(
                        text = "云端备份文件在「本地备份 → 导入数据」弹窗里选择恢复。"
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

/** 导入弹窗里的一行来源，当前选中的来源右侧打勾 */
@Composable
private fun ImportSourceRow(
    title: String,
    summary: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        endActions = {
            if (selected) {
                Icon(
                    MiuixIcons.Ok,
                    contentDescription = "当前来源",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        onClick = onClick
    )
}
