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

class McpHostService : Service() {
    private var server: EmbeddedServer<*, *>? = null
    private val gson = Gson()
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", 8080) ?: 8080
        startServer(port)
        return START_STICKY
    }
    
    private fun startServer(port: Int) {
        val scope = CoroutineScope(Dispatchers.IO)
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
                        
                        val database = AppDatabase.getDatabase(applicationContext)
                        val repository = OvertimeRepository(database)
                        
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val date = sdf.parse("$dateStr $startTime")?.time ?: System.currentTimeMillis()
                        val end = sdf.parse("$dateStr $endTime")?.time ?: date
                        
                        val duration = SalaryCalculator.calculateDurationHours(date, end)
                        val amount = SalaryCalculator.calculateOvertimeAmount(2200.0, type, 1.5, duration)
                        
                        val record = OvertimeRecord(
                            date = date,
                            type = type,
                            startTime = date,
                            endTime = end,
                            durationHours = duration,
                            baseSalary = 2200.0,
                            rate = 1.5,
                            amount = amount,
                            note = note
                        )
                        
                        scope.launch {
                            repository.insert(record)
                        }
                        
                        call.respond(mapOf("success" to true, "message" to "Record added"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
                    }
                }
                
                post("/mcp/tools/query_overtime_records") {
                    try {
                        val database = AppDatabase.getDatabase(applicationContext)
                        val repository = OvertimeRepository(database)
                        
                        var result: List<OvertimeRecord> = emptyList()
                        repository.getAllRecords().collect { records ->
                            result = records
                        }
                        
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
                        val database = AppDatabase.getDatabase(applicationContext)
                        val repository = OvertimeRepository(database)
                        
                        var stats: com.overtime.miuix.data.repository.MonthlyStats? = null
                        scope.launch {
                            stats = repository.getMonthlyStats(month)
                        }
                        
                        stats?.let {
                            call.respond(
                                mapOf(
                                    "month" to month,
                                    "totalHours" to it.totalHours,
                                    "totalAmount" to it.totalAmount,
                                    "recordCount" to it.recordCount,
                                    "workdayHours" to it.workdayHours,
                                    "weekendHours" to it.weekendHours,
                                    "holidayHours" to it.holidayHours
                                )
                            )
                        } ?: call.respond(mapOf("error" to "Stats not available"))
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
        server?.stop()
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
