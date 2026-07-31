package com.overtime.miuix.mcp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.overtime.miuix.data.database.AppDatabase
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import com.overtime.miuix.data.repository.OvertimeRepository
import com.overtime.miuix.data.repository.SettingsRepository
import com.overtime.miuix.util.SalaryCalculator
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 标准 MCP（Model Context Protocol）服务端。
 *
 * 传输层采用官方规范的 **Streamable HTTP**：
 *  - `POST /mcp`   接收 JSON-RPC 2.0 请求/通知；对请求以 `text/event-stream` 返回 SSE 响应
 *                    （客户端 Accept 不含 event-stream 时退化为 `application/json`）；
 *                    初始化时通过 `Mcp-Session-Id` 头建立会话。
 *  - `GET /mcp`    服务端→客户端的可恢复 SSE 流（保活），用于承载服务端主动通知。
 *  - `DELETE /mcp` 终止会话。
 *
 * 实现的 MCP 方法：`initialize` / `ping` / `tools/list` / `tools/call`，
 * 暴露的工具：add_overtime_record、query_overtime_records、get_monthly_stats。
 */
class McpHostService : Service() {
    private var server: EmbeddedServer<*, *>? = null
    private val gson = Gson()
    // sessionId -> 是否已收到 notifications/initialized
    private val sessions = ConcurrentHashMap<String, Boolean>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", 8080) ?: 8080
        startServer(port)
        return START_STICKY
    }

    private fun startServer(port: Int) {
        val context = applicationContext
        // 避免重复启动导致旧 server 泄漏与端口冲突
        server?.stop(1000, 5000)
        server = embeddedServer(CIO, port = port) {
            routing {
                post("/mcp") { call.handleMcpPost(context) }
                get("/mcp") { call.handleMcpGet() }
                delete("/mcp") { call.handleMcpDelete() }
            }
        }
        server?.start(wait = false)
        Log.d(TAG, "MCP JSON-RPC server started on port $port")
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(1000, 5000)
        server = null
        sessions.clear()
        Log.d(TAG, "MCP server stopped")
    }

    // ----------------------------------------------------------------
    // 传输层处理
    // ----------------------------------------------------------------

    private suspend fun ApplicationCall.handleMcpPost(context: Context) {
        val raw = runCatching { receiveText() }.getOrElse {
            respondRpc(errorResponse(null, CODE_PARSE_ERROR, "Invalid request body"), null)
            return
        }
        val parsed = runCatching { JsonParser.parseString(raw) }.getOrElse {
            respondRpc(errorResponse(null, CODE_PARSE_ERROR, "Parse error"), null)
            return
        }
        val headerSession = request.headers["Mcp-Session-Id"]

        if (parsed.isJsonArray) {
            val responses = JsonArray()
            var newSessionId: String? = null
            for (el in parsed.asJsonArray) {
                val outcome = processMessage(el, headerSession, context)
                outcome.sessionHeader?.let { newSessionId = it }
                outcome.response?.let { responses.add(it) }
            }
            if (responses.size() == 0) {
                respond(HttpStatusCode.Accepted)
            } else {
                respondRpc(responses, newSessionId ?: headerSession)
            }
            return
        }

        if (!parsed.isJsonObject) {
            respondRpc(errorResponse(null, CODE_INVALID_REQUEST, "Invalid Request"), null)
            return
        }

        val outcome = processMessage(parsed, headerSession, context)
        if (outcome.response == null) {
            // 通知（无 id）无需响应
            respond(HttpStatusCode.Accepted)
        } else {
            respondRpc(outcome.response, outcome.sessionHeader ?: headerSession)
        }
    }

    private suspend fun ApplicationCall.handleMcpGet() {
        response.headers.append(HttpHeaders.CacheControl, "no-cache")
        // SSE 保活流：维持服务端→客户端的可恢复连接；本服务暂不主动推送通知
        respondOutputStream(contentType = ContentType.Text.EventStream) {
            val keepAlive = ": keep-alive\n\n".encodeToByteArray()
            try {
                while (true) {
                    write(keepAlive)
                    flush()
                    delay(30_000)
                }
            } catch (_: Exception) {
                // 客户端断开，结束流
            }
        }
    }

    private suspend fun ApplicationCall.handleMcpDelete() {
        request.headers["Mcp-Session-Id"]?.let { sessions.remove(it) }
        respond(HttpStatusCode.OK)
    }

    /** 依据客户端 Accept 选择 SSE 或 JSON 响应。 */
    private suspend fun ApplicationCall.respondRpc(payload: JsonElement, sessionId: String?) {
        sessionId?.let { response.headers.append("Mcp-Session-Id", it) }
        val accept = request.headers[HttpHeaders.Accept] ?: ""
        if (accept.contains("text/event-stream")) {
            val builder = StringBuilder()
            if (payload.isJsonArray) {
                for (el in payload.asJsonArray) {
                    builder.append("event: message\r\ndata: ${gson.toJson(el)}\r\n\r\n")
                }
            } else {
                builder.append("event: message\r\ndata: ${gson.toJson(payload)}\r\n\r\n")
            }
            respondText(builder.toString(), contentType = ContentType.Text.EventStream)
        } else {
            respondText(gson.toJson(payload), contentType = ContentType.Application.Json)
        }
    }

    // ----------------------------------------------------------------
    // JSON-RPC 2.0 分发
    // ----------------------------------------------------------------

    private data class RpcOutcome(val response: JsonElement?, val sessionHeader: String? = null)

    private class RpcException(val code: Int, message: String) : Exception(message)

    private suspend fun processMessage(element: JsonElement, sessionId: String?, context: Context): RpcOutcome {
        if (!element.isJsonObject) return RpcOutcome(errorResponse(null, CODE_INVALID_REQUEST, "Invalid Request"))
        val obj = element.asJsonObject
        val id = obj.get("id") // 可能为 null（通知）
        val method = obj.get("method")?.takeIf { it.isJsonPrimitive }?.asString

        return if (method != null) {
            if (id == null || id.isJsonNull) {
                // 通知：处理但不返回响应
                handleNotification(method, obj.get("params"), sessionId)
                RpcOutcome(null)
            } else {
                // 请求
                try {
                    val (result, newSessionId) = dispatch(method, obj.get("params"), sessionId, context)
                    RpcOutcome(successResponse(id, result), newSessionId)
                } catch (e: RpcException) {
                    RpcOutcome(errorResponse(id, e.code, e.message ?: "Error"))
                } catch (e: Exception) {
                    RpcOutcome(errorResponse(id, CODE_INTERNAL_ERROR, e.message ?: "Internal error"))
                }
            }
        } else {
            // 来自客户端的响应（无 method）：忽略
            RpcOutcome(null)
        }
    }

    private data class DispatchResult(val result: JsonElement, val newSessionId: String? = null)

    private suspend fun dispatch(
        method: String,
        params: JsonElement?,
        sessionId: String?,
        context: Context
    ): DispatchResult = when (method) {
        "initialize" -> {
            val newSessionId = UUID.randomUUID().toString()
            sessions[newSessionId] = false
            DispatchResult(initializeResult(), newSessionId)
        }
        "ping" -> DispatchResult(JsonObject())
        "tools/list" -> {
            requireSession(sessionId)
            DispatchResult(toolsList())
        }
        "tools/call" -> {
            requireSession(sessionId)
            DispatchResult(toolsCall(params, context))
        }
        else -> throw RpcException(CODE_METHOD_NOT_FOUND, "Method not found: $method")
    }

    private fun handleNotification(method: String, params: JsonElement?, sessionId: String?) {
        when (method) {
            "notifications/initialized" -> sessionId?.let { sessions[it] = true }
            "notifications/cancelled" -> { /* 忽略 */ }
        }
    }

    private fun requireSession(sessionId: String?) {
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            throw RpcException(CODE_INVALID_SESSION, "Invalid or missing Mcp-Session-Id")
        }
    }

    // ----------------------------------------------------------------
    // MCP 方法实现
    // ----------------------------------------------------------------

    private fun initializeResult(): JsonObject = JsonObject().apply {
        addProperty("protocolVersion", PROTOCOL_VERSION)
        add("capabilities", JsonObject().apply {
            add("tools", JsonObject().apply { addProperty("listChanged", false) })
        })
        add("serverInfo", JsonObject().apply {
            addProperty("name", SERVER_NAME)
            addProperty("version", SERVER_VERSION)
        })
    }

    private fun toolsList(): JsonObject = JsonObject().apply {
        add("tools", JsonArray().apply {
            add(toolSpec(
                name = "add_overtime_record",
                description = "添加一条加班记录。参数 date(YYYY-MM-DD)、type(WORKDAY/WEEKEND/HOLIDAY)、" +
                    "startTime(HH:mm)、endTime(HH:mm)、note(可选)。",
                required = listOf("date"),
                properties = mapOf(
                    "date" to schema("string", "日期，格式 YYYY-MM-DD"),
                    "type" to schemaEnum(listOf("WORKDAY", "WEEKEND", "HOLIDAY"), "加班类型"),
                    "startTime" to schema("string", "开始时间，格式 HH:mm，默认 18:00"),
                    "endTime" to schema("string", "结束时间，格式 HH:mm，默认 20:00"),
                    "note" to schema("string", "备注")
                )
            ))
            add(toolSpec(
                name = "query_overtime_records",
                description = "查询加班记录，可按类型过滤。返回记录列表。",
                required = emptyList(),
                properties = mapOf(
                    "type" to schemaEnum(listOf("WORKDAY", "WEEKEND", "HOLIDAY"), "按类型过滤（可选）")
                )
            ))
            add(toolSpec(
                name = "get_monthly_stats",
                description = "获取指定月份的加班统计：总时长、预估薪资、记录数、各类别时长。",
                required = listOf("month"),
                properties = mapOf("month" to schema("string", "月份，格式 YYYY-MM"))
            ))
        })
    }

    private suspend fun toolsCall(params: JsonElement?, context: Context): JsonObject {
        val obj = params?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw RpcException(CODE_INVALID_PARAMS, "Missing tool name")
        val arguments = obj.get("arguments")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        return when (name) {
            "add_overtime_record" -> callAddOvertime(arguments, context)
            "query_overtime_records" -> callQuery(arguments, context)
            "get_monthly_stats" -> callMonthlyStats(arguments, context)
            else -> throw RpcException(CODE_METHOD_NOT_FOUND, "Unknown tool: $name")
        }
    }

    private suspend fun callAddOvertime(args: JsonObject, context: Context): JsonObject {
        val dateStr = args.get("date")?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw RpcException(CODE_INVALID_PARAMS, "Missing required parameter: date")
        val typeStr = args.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: "WORKDAY"
        val startTime = args.get("startTime")?.takeIf { it.isJsonPrimitive }?.asString ?: "18:00"
        val endTime = args.get("endTime")?.takeIf { it.isJsonPrimitive }?.asString ?: "20:00"
        val note = args.get("note")?.takeIf { it.isJsonPrimitive }?.asString ?: ""

        val type = try {
            OvertimeType.valueOf(typeStr.uppercase(Locale.getDefault()))
        } catch (_: Exception) {
            OvertimeType.WORKDAY
        }

        val database = AppDatabase.getDatabase(context)
        val repository = OvertimeRepository(database)
        val settingsRepository = SettingsRepository(context)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val start = sdf.parse("$dateStr $startTime")?.time
            ?: throw RpcException(CODE_INVALID_PARAMS, "Invalid date/time format")
        val end = sdf.parse("$dateStr $endTime")?.time ?: start

        val duration = SalaryCalculator.calculateDurationHours(start, end)
        val baseSalary = settingsRepository.baseSalary.first()
        val rate = when (type) {
            OvertimeType.WORKDAY -> settingsRepository.workdayRate.first()
            OvertimeType.WEEKEND -> settingsRepository.weekendRate.first()
            OvertimeType.HOLIDAY -> settingsRepository.holidayRate.first()
        }
        val amount = SalaryCalculator.calculateOvertimeAmount(baseSalary, type, rate, duration)

        val record = OvertimeRecord(
            date = start,
            type = type,
            startTime = start,
            endTime = end,
            durationHours = duration,
            baseSalary = baseSalary,
            rate = rate,
            amount = amount,
            note = note
        )
        repository.insert(record)

        val text = "已添加加班记录：$dateStr $startTime-$endTime（${type.name}），时长 ${duration} 小时，预估 ¥$amount"
        return toolResult(
            text = text,
            structuredContent = JsonObject().apply {
                addProperty("success", true)
                addProperty("durationHours", duration)
                addProperty("amount", amount)
            }
        )
    }

    private suspend fun callQuery(args: JsonObject, context: Context): JsonObject {
        val database = AppDatabase.getDatabase(context)
        val repository = OvertimeRepository(database)
        val all = repository.getAllRecords().first()

        val typeFilter = args.get("type")?.takeIf { it.isJsonPrimitive }?.asString?.uppercase(Locale.getDefault())
        val filtered = if (typeFilter != null) {
            runCatching { OvertimeType.valueOf(typeFilter) }.fold(
                onSuccess = { t -> all.filter { it.type == t } },
                onFailure = { all }
            )
        } else {
            all
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val recordsJson = JsonArray().apply {
            filtered.forEach { r ->
                add(JsonObject().apply {
                    addProperty("id", r.id)
                    addProperty("date", sdf.format(Date(r.date)))
                    addProperty("type", r.type.name)
                    addProperty("durationHours", r.durationHours)
                    addProperty("amount", r.amount)
                    addProperty("note", r.note)
                })
            }
        }

        return toolResult(
            text = "共查询到 ${filtered.size} 条记录",
            structuredContent = JsonObject().apply { add("records", recordsJson) }
        )
    }

    private suspend fun callMonthlyStats(args: JsonObject, context: Context): JsonObject {
        val month = args.get("month")?.takeIf { it.isJsonPrimitive }?.asString
            ?: SalaryCalculator.getCurrentYearMonth()
        val database = AppDatabase.getDatabase(context)
        val repository = OvertimeRepository(database)
        val stats = repository.getMonthlyStats(month)

        val s = JsonObject().apply {
            addProperty("month", month)
            addProperty("totalHours", stats.totalHours)
            addProperty("totalAmount", stats.totalAmount)
            addProperty("recordCount", stats.recordCount)
            addProperty("workdayHours", stats.workdayHours)
            addProperty("weekendHours", stats.weekendHours)
            addProperty("holidayHours", stats.holidayHours)
        }
        val text = "$month 统计：共 ${stats.recordCount} 条，总时长 ${stats.totalHours} 小时，预估 ¥${stats.totalAmount}"
        return toolResult(text = text, structuredContent = s)
    }

    // ----------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------

    private fun toolResult(text: String, structuredContent: JsonObject, isError: Boolean = false): JsonObject =
        JsonObject().apply {
            add("content", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", text)
                })
            })
            addProperty("isError", isError)
            add("structuredContent", structuredContent)
        }

    private fun toolSpec(
        name: String,
        description: String,
        required: List<String>,
        properties: Map<String, JsonObject>
    ): JsonObject = JsonObject().apply {
        addProperty("name", name)
        addProperty("description", description)
        add("inputSchema", JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply { properties.forEach { (k, v) -> add(k, v) } })
            add("required", JsonArray().apply { required.forEach { add(it) } })
        })
    }

    private fun schema(type: String, description: String): JsonObject = JsonObject().apply {
        addProperty("type", type)
        addProperty("description", description)
    }

    private fun schemaEnum(values: List<String>, description: String): JsonObject = JsonObject().apply {
        addProperty("type", "string")
        add("enum", JsonArray().apply { values.forEach { add(it) } })
        addProperty("description", description)
    }

    private fun successResponse(id: JsonElement, result: JsonElement): JsonObject =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id)
            add("result", result)
        }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
            add("error", JsonObject().apply {
                addProperty("code", code)
                addProperty("message", message)
            })
        }

    companion object {
        private const val TAG = "McpHostService"
        private const val PROTOCOL_VERSION = "2025-03-26"
        private const val SERVER_NAME = "overtime-miuix"
        private const val SERVER_VERSION = "1.0.0"

        private const val CODE_PARSE_ERROR = -32700
        private const val CODE_INVALID_REQUEST = -32600
        private const val CODE_METHOD_NOT_FOUND = -32601
        private const val CODE_INVALID_PARAMS = -32602
        private const val CODE_INTERNAL_ERROR = -32603
        private const val CODE_INVALID_SESSION = -32000

        fun start(context: Context, port: Int) {
            val intent = Intent(context, McpHostService::class.java).apply { putExtra("port", port) }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, McpHostService::class.java))
        }
    }
}
