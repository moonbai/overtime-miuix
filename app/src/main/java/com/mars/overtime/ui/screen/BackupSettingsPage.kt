package com.mars.overtime.ui.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import com.mars.overtime.util.BackupManager
import com.mars.overtime.util.DataMigrationUtil
import com.mars.overtime.util.FeaturePermissions
import com.mars.overtime.util.WebDavManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import java.io.File
import java.io.FileOutputStream

/** 业务去重键：同日期 + 同类型 + 同时长 + 同请假标记 + 同备注 视为同一条记录 */
private fun recordKey(r: OvertimeRecord): String =
    "${r.date}_${r.type}_${r.durationHours}_${r.isLeave}_${r.note.trim()}"

@Composable
fun BackupSettingsPage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val records by repository.getAllRecords().collectAsState(initial = emptyList())

    val webdavEnabled by settingsRepository.webdavEnabled.collectAsState(initial = false)
    val webdavUrl by settingsRepository.webdavUrl.collectAsState(initial = "")
    val webdavUsername by settingsRepository.webdavUsername.collectAsState(initial = "")
    val webdavPassword by settingsRepository.webdavPassword.collectAsState(initial = "")
    val webdavPath by settingsRepository.webdavPath.collectAsState(initial = "/overtime_backup/")
    val autoBackupEnabled by settingsRepository.autoBackupEnabled.collectAsState(initial = false)
    val autoBackupLocation by settingsRepository.autoBackupLocation.collectAsState(initial = "local")

    var webdavUrlText by remember { mutableStateOf(webdavUrl) }
    var webdavUserText by remember { mutableStateOf(webdavUsername) }
    var webdavPassText by remember { mutableStateOf(webdavPassword) }
    var webdavPathText by remember { mutableStateOf(webdavPath) }

    var status by remember { mutableStateOf<String?>(null) }

    // 云端恢复：远端文件列表与选择弹窗
    var showRestoreDialog by remember { mutableStateOf(false) }
    var remoteFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingRemote by remember { mutableStateOf(false) }

    // 导入/恢复二次确认：解析完成后暂存，由确认框触发真正写入
    var pendingImport by remember { mutableStateOf<BackupData?>(null) }

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
     */
    suspend fun applyImport(data: BackupData) {
        val existingKeys = repository.getAllRecords().first().map { recordKey(it) }.toSet()
        var inserted = 0
        data.records.forEach { rec ->
            if (recordKey(rec) !in existingKeys) {
                repository.insert(rec.copy(id = 0))
                inserted++
            }
        }
        if (data.settings.isNotEmpty()) {
            settingsRepository.importSettings(data.settings)
        }
        val skipped = data.records.size - inserted
        showToast("导入完成：新增 $inserted 条，跳过 $skipped 条重复")
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

            // 导入/恢复二次确认
            BackHandler(enabled = pendingImport != null) { pendingImport = null }
            OverlayDialog(
                show = pendingImport != null,
                title = "确认导入",
                summary = if (pendingImport != null) {
                    val settingsHint = if (pendingImport!!.settings.isNotEmpty()) "，并覆盖恢复设置" else ""
                    "将导入 ${pendingImport!!.records.size} 条记录（自动跳过重复）$settingsHint，确定继续吗？"
                } else "",
                onDismissRequest = { pendingImport = null }
            ) {
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
                            val data = pendingImport
                            pendingImport = null
                            data?.let { scope.launch { applyImport(it) } }
                        },
                        modifier = Modifier.weight(1f)
                    )
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
                    summary = "从 ZIP / JSON 恢复记录与设置（自动去重）",
                    icon = MiuixIcons.Import,
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/json", "*/*")) }
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
