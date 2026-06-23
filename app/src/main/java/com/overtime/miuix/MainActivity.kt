package com.overtime.miuix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.overtime.miuix.data.database.AppDatabase
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.ui.screen.*
import com.overtime.miuix.ui.theme.OvertimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = AppDatabase.getDatabase(this)
        val repository = OvertimeRepository(database)
        val settingsRepository = SettingsRepository(this)
        
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = "system")
            val accentColor by settingsRepository.accentColor.collectAsState(initial = 0xFF3482FF.toInt())
            
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            
            OvertimeTheme(
                darkTheme = darkTheme,
                keyColor = androidx.compose.ui.graphics.Color(accentColor)
            ) {
                val navController = rememberNavController()
                MainNavHost(
                    navController = navController,
                    repository = repository,
                    settingsRepository = settingsRepository
                )
            }
        }
    }
}

@Composable
fun MainNavHost(
    navController: NavHostController,
    repository: OvertimeRepository,
    settingsRepository: SettingsRepository
) {
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                navController = navController,
                repository = repository,
                settingsRepository = settingsRepository
            )
        }
        composable("add_record") {
            AddEditRecordPage(
                navController = navController,
                repository = repository,
                settingsRepository = settingsRepository
            )
        }
        composable("edit_record/{recordId}") { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString("recordId")?.toLongOrNull()
            AddEditRecordPage(
                navController = navController,
                repository = repository,
                settingsRepository = settingsRepository,
                recordId = recordId
            )
        }
        composable("statistics") {
            StatisticsPage(
                navController = navController,
                repository = repository
            )
        }
        composable("settings") {
            SettingsPage(navController = navController)
        }
        composable("push_settings") {
            PushSettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("backup_settings") {
            BackupSettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("salary_settings") {
            SalarySettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("appearance_settings") {
            AppearanceSettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("calendar_settings") {
            CalendarSettingsPage(navController = navController)
        }
        composable("holiday_settings") {
            HolidaySettingsPage(navController = navController)
        }
        composable("mcp_settings") {
            McpSettingsPage(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable("about") {
            AboutPage(navController = navController)
        }
    }
}
