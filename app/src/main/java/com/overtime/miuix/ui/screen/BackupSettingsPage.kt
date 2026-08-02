package com.overtime.miuix.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.snackbar.LocalSnackbarHostState
import com.overtime.miuix.ui.snackbar.showCustomToast
import com.overtime.miuix.util.BackupData
import com.overtime.miuix.util.BackupManager
import com.overtime.miuix.util.DataMigrationUtil
import com.overtime.miuix.util.WebDavManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream

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

    LaunchedEffect(webdavUrl) { webdavUrlText = webdavUrl }
    LaunchedEffect(webdavUsername) { webdavUserText = webdavUsername }
    LaunchedEffect(webdavPassword) { webdavPassText = webdavPassword }
    LaunchedEffect(webdavPath) { webdavPathText = webdavPath }

    suspend fun showToast(msg: String) {
        status = msg
        snackbarHostState.showCustomToast(msg)
    }

    fun buildWebDavConfig(): WebDavManager.WebDavConfig = WebDavManager.WebDavConfig(
        baseUrl = webdavUrlText,
        username = webdavUserText,
        password = webdavPassText,
        remotePath = webdavPathText.ifBlank { "/overtime_backup/" }
    )

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
        if (data == null) {
            showToast("文件解析失败")
            return
        }
        data.records.forEach { repository.insert(it.copy(id = 0)) }
        if (data.settings.isNotEmpty()) {
            settingsRepository.importSettings(data.settings)
        }
        runCatching { File(localPath).delete() }
        showToast("云端恢复成功，共 ${data.records.size} 条记录")
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
                // 先判断文件扩展名，决定解析方式
                val fileName = uri.lastPathSegment ?: ""
                val data: BackupData? = if (fileName.endsWith(".zip", ignoreCase = true)) {
                    // ZIP 格式：复制到临时文件再解析
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
                    // 旧版 JSON 格式（向后兼容）
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    if (json != null) BackupManager.deserialize(json) else null
                }
                if (data == null) {
                    showToast("文件解析失败")
                    return@launch
                }
                data.records.forEach { repository.insert(it.copy(id = 0)) }
                if (data.settings.isNotEmpty()) {
                    settingsRepository.importSettings(data.settings)
                }
                showToast("导入成功，共 ${data.records.size} 条记录")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("导入失败：${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "备份与恢复",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (status != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = status ?: "",
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.footnote1
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "本地备份") {
                    BasicComponent(
                        title = "导出数据",
                        summary = "将记录与设置导出为 ZIP 文件",
                        startAction = { Icon(MiuixIcons.Download, contentDescription = null) },
                        onClick = {
                            val name = DataMigrationUtil.generateBackupFileName()
                            exportLauncher.launch(name)
                        }
                    )
                    BasicComponent(
                        title = "导入数据",
                        summary = "从 ZIP / JSON 文件恢复记录与设置",
                        startAction = { Icon(MiuixIcons.Import, contentDescription = null) },
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/json", "*/*")) }
                    )
                }
            }

            item {
                SettingsGroup(title = "自动备份") {
                    BasicComponent(
                        title = "启用自动备份",
                        summary = "新增记录时自动保存到本地或云端",
                        endActions = {
                            Switch(
                                checked = autoBackupEnabled,
                                onCheckedChange = { scope.launch { settingsRepository.setAutoBackupEnabled(it) } }
                            )
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
                            }
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "WebDAV 云端同步") {
                    BasicComponent(
                        title = "启用 WebDAV",
                        summary = "配置 WebDAV 服务器进行备份同步",
                        endActions = {
                            Switch(
                                checked = webdavEnabled,
                                onCheckedChange = { scope.launch { settingsRepository.setWebdavEnabled(it) } }
                            )
                        }
                    )
                    if (webdavEnabled) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TextField(
                                value = webdavUrlText,
                                onValueChange = { webdavUrlText = it; scope.launch { settingsRepository.setWebdavUrl(it) } },
                                label = "服务器地址",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextField(
                                    value = webdavUserText,
                                    onValueChange = { webdavUserText = it; scope.launch { settingsRepository.setWebdavUsername(it) } },
                                    label = "用户名",
                                    modifier = Modifier.weight(1f)
                                )
                                TextField(
                                    value = webdavPassText,
                                    onValueChange = { webdavPassText = it; scope.launch { settingsRepository.setWebdavPassword(it) } },
                                    label = "密码",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = webdavPathText,
                                onValueChange = { webdavPathText = it; scope.launch { settingsRepository.setWebdavPath(it) } },
                                label = "远程路径",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val ok = WebDavManager.testConnection(buildWebDavConfig())
                                            showToast(if (ok) "连接成功" else "连接失败")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("测试连接") }
                                Button(
                                    onClick = {
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
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("立即上传") }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // 云端下载/恢复：列出远端备份文件供选择
                            BasicComponent(
                                title = "从云端恢复",
                                summary = "列出 WebDAV 上的备份文件并下载恢复",
                                startAction = { Icon(MiuixIcons.Download, contentDescription = null) },
                                onClick = {
                                    if (loadingRemote) return@BasicComponent
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
                            )
                        }
                    }
                }
            }
        }

        // 云端恢复：选择远端备份文件对话框
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
    }
}
