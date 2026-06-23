package com.overtime.miuix.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.util.SalaryCalculator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Add
import top.yukonga.miuix.kmp.icon.icons.useful.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomePage(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    val records by repository.getAllRecords().collectAsState(initial = emptyList())
    val currentMonth = remember { SalaryCalculator.getCurrentYearMonth() }
    val scope = rememberCoroutineScope()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<OvertimeRecord?>(null) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MonthlyOverviewCard(repository, currentMonth)
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部记录",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    text = "添加",
                    onClick = { navController.navigate("add_record") },
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(MiuixIcons.Useful.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加", style = MiuixTheme.textStyles.labelMedium)
                }
            }
        }
        
        if (records.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无加班记录\n点击添加按钮记录",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(records, key = { it.id }) { record ->
                RecordCard(
                    record = record,
                    onClick = { navController.navigate("edit_record/${record.id}") },
                    onDelete = {
                        selectedRecord = record
                        showDeleteDialog = true
                    }
                )
            }
        }
    }
    
    if (showDeleteDialog && selectedRecord != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = "确认删除",
            text = "确定要删除这条记录吗？",
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            selectedRecord?.let { repository.delete(it) }
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除", color = MiuixTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun MonthlyOverviewCard(repository: OvertimeRepository, month: String) {
    var stats by remember { mutableStateOf<com.overtime.miuix.data.repository.MonthlyStats?>(null) }
    
    LaunchedEffect(month) {
        stats = repository.getMonthlyStats(month)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "$month 概览",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("总时长", stats?.let { SalaryCalculator.formatHours(it.totalHours) } ?: "0小时")
                StatItem("预估薪资", stats?.let { SalaryCalculator.formatAmount(it.totalAmount) } ?: "¥0.00")
                StatItem("记录数", "${stats?.recordCount ?: 0}条")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.caption,
            color = MiuixTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordCard(
    record: OvertimeRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(record.date))
    val typeColor = when (record.type) {
        OvertimeType.WORKDAY -> MiuixTheme.colorScheme.primary
        OvertimeType.WEEKEND -> MiuixTheme.colorScheme.secondary
        OvertimeType.HOLIDAY -> MiuixTheme.colorScheme.tertiary
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 12.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .padding(end = 12.dp)
            ) {}
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.type.label,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        color = typeColor
                    )
                    if (record.isLeave) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MiuixTheme.colorScheme.error.copy(alpha = 0.1f),
                            shape = MiuixTheme.shapes.small
                        ) {
                            Text(
                                text = "请假",
                                style = MiuixTheme.textStyles.caption,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = dateStr,
                    style = MiuixTheme.textStyles.caption,
                    color = MiuixTheme.colorScheme.onSurfaceVariant
                )
                if (record.note.isNotBlank()) {
                    Text(
                        text = record.note,
                        style = MiuixTheme.textStyles.caption,
                        color = MiuixTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = SalaryCalculator.formatAmount(record.amount),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary
                )
                Text(
                    text = SalaryCalculator.formatHours(record.durationHours),
                    style = MiuixTheme.textStyles.caption,
                    color = MiuixTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(MiuixIcons.Useful.Delete, contentDescription = "删除", tint = MiuixTheme.colorScheme.error)
            }
        }
    }
}
