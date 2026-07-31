package com.overtime.miuix.mcp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.overtime.miuix.data.database.AppDatabase
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.util.SalaryCalculator
import io.ktor.server.cio.CIO
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class McpHostService : Service() {
    private var server: EmbeddedServer<*, *>? = null
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", 8080) ?: 8080
        startServer(port)
        return START_STICKY
    }
    
    private fun startServer(port: Int) {
        val context = applicationContext
        server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                gson { }
            }
            routing {
                get("/mcp") {
                    call.respondText(
                        """
                        MCP Server Running
                        Available endpoints:
                        - GET /mcp/tools - List available tools
                        - POST /mcp/tools/add_overtime_record - Add new record
                        - POST /mcp/tools/query_overtime_records - Query records
                        - GET /mcp/tools/get_monthly_stats?month=YYYY-MM - Get monthly stats
                        """.trimIndent(),
                        ContentType.Text.Plain
                    )
                }
                
                get("/mcp/tools") {
                    call.respond(
                        mapOf(
                            "tools" to listOf(
                                mapOf(
                                    "name" to "add_overtime_record",
                                    "description" to "添加加班记录",
                                    "parameters" to listOf("date", "type", "startTime", "endTime", "note")
                                ),
                                mapOf(
                                    "name" to "query_overtime_records",
                                    "description" to "查询加班记录",
                                    "parameters" to listOf("startDate", "endDate", "type")
                                ),
                                mapOf(
                                    "name" to "get_monthly_stats",
                                    "description" to "获取月度统计",
                                    "parameters" to listOf("month")
                                )
                            )
                        )
                    )
                }
                
                post("/mcp/tools/add_overtime_record") {
                    try {
                        val body = call.receiveText()
                        val data = gson.fromJson(body, Map::class.java)
                        
                        val dateStr = data["date"] as? String ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing date")
                        val typeStr = data["type"] as? String ?: "WORKDAY"
                        val startTime = data["startTime"] as? String ?: "18:00"
                        val endTime = data["endTime"] as? String ?: "20:00"
                        val note = data["note"] as? String ?: ""
                        
                        val type = try { OvertimeType.valueOf(typeStr.uppercase()) } catch (e: Exception) { OvertimeType.WORKDAY }
                        
                        val database = AppDatabase.getDatabase(context)
                        val repository = OvertimeRepository(database)
                        val settingsRepository = SettingsRepository(context)
                        
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val date = sdf.parse("$dateStr $startTime")?.time ?: System.currentTimeMillis()
                        val end = sdf.parse("$dateStr $endTime")?.time ?: date
                        
                        val duration = SalaryCalculator.calculateDurationHours(date, end)
                        
                        // Read actual salary settings from DataStore
                        val baseSalary = settingsRepository.baseSalary.first()
                        val rate = when (type) {
                            OvertimeType.WORKDAY -> settingsRepository.workdayRate.first()
                            OvertimeType.WEEKEND -> settingsRepository.weekendRate.first()
                            OvertimeType.HOLIDAY -> settingsRepository.holidayRate.first()
                        }
                        val amount = SalaryCalculator.calculateOvertimeAmount(baseSalary, type, rate, duration)
                        
                        val record = OvertimeRecord(
                            date = date,
                            type = type,
                            startTime = date,
                            endTime = end,
                            durationHours = duration,
                            baseSalary = baseSalary,
                            rate = rate,
                            amount = amount,
                            note = note
                        )
                        
                        repository.insert(record)
                        
                        call.respond(mapOf("success" to true, "message" to "Record added"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
                    }
                }
                
                post("/mcp/tools/query_overtime_records") {
                    try {
                        val database = AppDatabase.getDatabase(context)
                        val repository = OvertimeRepository(database)
                        
                        val result = repository.getAllRecords().first()
                        
                        val formatted = result.map { record ->
                            mapOf(
                                "id" to record.id,
                                "date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(record.date)),
                                "type" to record.type.name,
                                "duration" to record.durationHours,
                                "amount" to record.amount,
                                "note" to record.note
                            )
                        }
                        
                        call.respond(mapOf("records" to formatted))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
                    }
                }
                
                get("/mcp/tools/get_monthly_stats") {
                    try {
                        val month = call.parameters["month"] ?: SalaryCalculator.getCurrentYearMonth()
                        val database = AppDatabase.getDatabase(context)
                        val repository = OvertimeRepository(database)
                        
                        val stats = repository.getMonthlyStats(month)
                        
                        call.respond(
                            mapOf(
                                "month" to month,
                                "totalHours" to stats.totalHours,
                                "totalAmount" to stats.totalAmount,
                                "recordCount" to stats.recordCount,
                                "workdayHours" to stats.workdayHours,
                                "weekendHours" to stats.weekendHours,
                                "holidayHours" to stats.holidayHours
                            )
                        )
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
                    }
                }
            }
        }
        server?.start(wait = false)
        
        Log.d("McpHostService", "Server started on port $port")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        server?.stop(1000, 5000)
        serviceScope.cancel()
        Log.d("McpHostService", "Server stopped")
    }
    
    companion object {
        fun start(context: Context, port: Int) {
            val intent = Intent(context, McpHostService::class.java)
            intent.putExtra("port", port)
            context.startService(intent)
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, McpHostService::class.java))
        }
    }
}
