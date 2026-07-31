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
import com.overtime.miuix.util.BackupManager
import com.overtime.miuix.util.DataMigrationUtil
import com.overtime.miuix.util.WebDavManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    // 导出到用户选择的文件
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val settings = settingsRepository.exportSettingsMap()
            val json = BackupManager.serialize(records, settings)
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                showToast("导出成功")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("导出失败：${e.message}")
            }
        }
    }

    // 从用户选择的文件导入
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                val data = if (json != null) BackupManager.deserialize(json) else null
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
                Column {
                    SmallTitle(text = "本地备份")
                    BasicComponent(
                        title = "导出数据",
                        summary = "将记录与设置导出为 JSON 文件",
                        startAction = { Icon(MiuixIcons.Download, contentDescription = null) },
                        onClick = {
                            val name = DataMigrationUtil.generateBackupFileName()
                            exportLauncher.launch(name)
                        }
                    )
                    BasicComponent(
                        title = "导入数据",
                        summary = "从 JSON 文件恢复记录与设置",
                        startAction = { Icon(MiuixIcons.Import, contentDescription = null) },
                        onClick = { importLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }

            item {
                Column {
                    SmallTitle(text = "自动备份")
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
                        SmallTitle(text = "备份位置")
                        BasicComponent(
                            title = "仅本地",
                            onClick = { scope.launch { settingsRepository.setAutoBackupLocation("local") } },
                            endActions = { RadioButton(selected = autoBackupLocation == "local", onClick = null) }
                        )
                        BasicComponent(
                            title = "本地 + WebDAV 云端",
                            onClick = { scope.launch { settingsRepository.setAutoBackupLocation("cloud") } },
                            endActions = { RadioButton(selected = autoBackupLocation == "cloud", onClick = null) }
                        )
                    }
                }
            }

            item {
                Column {
                    SmallTitle(text = "WebDAV 云端同步")
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
                                            val okExport = BackupManager.exportData(records, settings, localPath)
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
                        }
                    }
                }
            }
        }
    }
}
