package com.mars.overtime.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mars.overtime.R
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mars.overtime.data.database.AppDatabase
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import com.mars.overtime.data.repository.OvertimeRepository
import com.mars.overtime.data.repository.SettingsRepository
import com.mars.overtime.util.RecordSyncHelper
import com.mars.overtime.util.SalaryCalculator
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
        val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        // 服务已在同端口运行时不重建 server：设置页重复触发开关或 Activity 回前台
        // 做状态同步时，重建会造成短暂断连，客户端会误判为服务不可用。
        if (server == null || runningPort != port) {
            startServer(port)
        } else {
            // 服务已在同端口运行：重复调用 startForeground 是幂等的，
            // 保证通过 startForegroundService 再次拉起时前台状态始终有效。
            runCatching { startForeground(NOTIFICATION_ID, buildNotification(port)) }
        }
        // 服务被系统查杀后不自动复活；由 MainActivity 每次回到前台依据开关重新拉起，
        // 避免 START_STICKY 用默认端口复活导致「开关显示开、实际监听在别的端口」。
        return START_NOT_STICKY
    }

    private fun startServer(port: Int) {
        val context = applicationContext
        try {
            // 必须尽早提升为前台服务：普通后台服务在应用退到后台后会被系统 / 厂商省电策略
            // 快速回收；Android 14+ 对 startForegroundService 有 5 秒内 startForeground 的硬要求。
            startForeground(NOTIFICATION_ID, buildNotification(port))
            server?.stop(1000, 5000)
            // 显式绑定 0.0.0.0（Ktor 默认值相同），确保局域网与本机地址均可访问
            val engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                routing {
                    post("/mcp") { call.handleMcpPost(context) }
                    get("/mcp") { call.handleMcpGet() }
                    delete("/mcp") { call.handleMcpDelete() }
                }
            }
            engine.start(wait = false)
            server = engine
            runningPort = port
            Log.i(TAG, "MCP server listening on 0.0.0.0:$port")
        } catch (e: Exception) {
            // 端口占用、权限缺失、Ktor 启动失败等都会走到这里。旧实现无任何兜底：
            // 异常直接冒泡导致服务崩溃，而设置页已把开关写成「已开启」，用户完全无从判断。
            Log.e(TAG, "MCP server failed to start on port $port", e)
            runCatching { server?.stop(1000, 5000) }
            server = null
            runningPort = null
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
    }

    /** 前台服务通知：既是 Android 14+ 的合规要求，也让用户能直观看到服务确实在运行。 */
    private fun buildNotification(port: Int): Notification {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "MCP 服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "局域网 MCP 服务运行状态" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("MCP 服务运行中")
            .setContentText("端口 $port · 点击返回应用")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(1000, 5000)
        server = null
        runningPort = null
        sessions.clear()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        Log.d(TAG, "MCP server stopped")
    }

    // ----------------------------------------------------------------
    // 传输层处理
    // ----------------------------------------------------------------

    private suspend fun ApplicationCall.handleMcpPost(context: Context) {
        try {
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
        } catch (e: Exception) {
            // 任何未预期异常都必须返回标准 JSON-RPC 错误响应，避免连接被异常中断
            // （否则客户端会因收到不完整的响应而抛出 unexpected end of stream / EOFException）
            Log.w(TAG, "MCP POST 处理异常，已降级为错误响应", e)
            runCatching {
                respondRpc(errorResponse(null, CODE_INTERNAL_ERROR, "Internal error: ${e.message}"), null)
            }
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
        response.headers.append("MCP-Protocol-Version", PROTOCOL_VERSION)
        // 复用连接，避免客户端在后续请求时遭遇连接已关闭（unexpected end of stream）
        response.headers.append(HttpHeaders.Connection, "keep-alive")
        val accept = request.headers[HttpHeaders.Accept] ?: ""
        // Streamable HTTP 规范允许客户端同时声明 application/json 与 text/event-stream。
        // 此时服务端返回 JSON 即可；旧实现只要看到 event-stream 就强制 SSE，
        // 只按 JSON 解析响应的客户端会直接解析失败（表现为「连上了但没反应」）。
        val onlySse = accept.contains("text/event-stream") &&
            !accept.contains("application/json", ignoreCase = true)
        if (onlySse) {
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
            val requestedVersion = params?.takeIf { it.isJsonObject }
                ?.asJsonObject?.get("protocolVersion")
                ?.takeIf { it.isJsonPrimitive }?.asString
            DispatchResult(initializeResult(requestedVersion), newSessionId)
        }
        "ping" -> DispatchResult(JsonObject())
        // MCP Streamable HTTP 的会话 ID 是**可选**的：相当一部分客户端不会回传
        // Mcp-Session-Id，或在 initialize 之后另开连接。旧实现强制 requireSession，
        // 会让这些客户端的 tools/list 与 tools/call 一律返回 -32000，
        // 表现为「服务连得上，但工具列表为空 / 调用无响应」。
        "tools/list" -> DispatchResult(toolsList())
        "tools/call" -> DispatchResult(toolsCall(params, context))
        else -> throw RpcException(CODE_METHOD_NOT_FOUND, "Method not found: $method")
    }

    private fun handleNotification(method: String, params: JsonElement?, sessionId: String?) {
        when (method) {
            "notifications/initialized" -> sessionId?.let { sessions[it] = true }
            "notifications/cancelled" -> { /* 忽略 */ }
        }
    }

    // ----------------------------------------------------------------
    // MCP 方法实现
    // ----------------------------------------------------------------

    /**
     * 规范要求：客户端请求的版本不受支持时，服务端应返回自己支持的版本，
     * 而不是无视请求一律回固定值。这里对已知版本做回显，未知版本回退默认值。
     */
    private fun initializeResult(requestedVersion: String? = null): JsonObject = JsonObject().apply {
        val negotiated = if (requestedVersion != null && requestedVersion in SUPPORTED_PROTOCOL_VERSIONS) {
            requestedVersion
        } else {
            PROTOCOL_VERSION
        }
        addProperty("protocolVersion", negotiated)
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
                description = "添加一条加班或请假记录。参数 date(YYYY-MM-DD)、type(WORKDAY/WEEKEND/HOLIDAY)、" +
                    "durationHours(加班小时数，0.5为梯度)、note(可选)、isLeave(是否为请假)、leaveHours(请假小时数，仅 4 或 8，默认 4)。" +
                    "写入后会与 App 内保存一样触发推送、日历同步与自动备份。",
                required = listOf("date"),
                properties = mapOf(
                    "date" to schema("string", "日期，格式 YYYY-MM-DD"),
                    "type" to schemaEnum(listOf("WORKDAY", "WEEKEND", "HOLIDAY"), "加班类型"),
                    "durationHours" to schema("number", "加班小时数，0.5为梯度，默认 2.0"),
                    "note" to schema("string", "备注"),
                    "isLeave" to schema("boolean", "是否为请假记录，默认 false"),
                    "leaveHours" to schema("number", "请假小时数，只接受 4（半天）或 8（全天），默认 4")
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
        val durationHoursRaw = args.get("durationHours")?.takeIf { it.isJsonPrimitive }?.asNumber
        val isLeave = runCatching {
            args.get("isLeave")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        }.getOrDefault(false)
        val leaveHoursRaw = runCatching {
            args.get("leaveHours")?.takeIf { it.isJsonPrimitive }?.asNumber?.toDouble()
        }.getOrNull()
        // 加班：0.5 梯度、默认 2.0，夹到 [0.5, 24]；
        // 请假：与 App 内口径一致，只允许半天 -4 / 全天 -8，>=6h 视为全天，
        // 避免通过 MCP 写入 3.5h 这类 App 里已经不存在的档位。
        val durationHours = if (isLeave) {
            if (kotlin.math.abs(leaveHoursRaw ?: 4.0) >= 6.0) -8.0 else -4.0
        } else {
            val raw = durationHoursRaw?.toDouble() ?: 2.0
            (kotlin.math.round(raw * 2) / 2.0).coerceIn(0.5, 24.0)
        }
        val note = args.get("note")?.takeIf { it.isJsonPrimitive }?.asString ?: ""

        val type = try {
            OvertimeType.valueOf(typeStr.uppercase(Locale.getDefault()))
        } catch (_: Exception) {
            OvertimeType.WORKDAY
        }

        val database = AppDatabase.getDatabase(context)
        val repository = OvertimeRepository(database)
        val settingsRepository = SettingsRepository(context)

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateMs = sdfDate.parse(dateStr)?.time
            ?: throw RpcException(CODE_INVALID_PARAMS, "Invalid date format")

        val baseSalary = settingsRepository.baseSalary.first()
        val rate = when (type) {
            OvertimeType.WORKDAY -> settingsRepository.workdayRate.first()
            OvertimeType.WEEKEND -> settingsRepository.weekendRate.first()
            OvertimeType.HOLIDAY -> settingsRepository.holidayRate.first()
        }
        val amount = if (isLeave) {
            SalaryCalculator.calculateLeaveDeduction(baseSalary, durationHours)
        } else {
            SalaryCalculator.calculateOvertimeAmount(baseSalary, type, rate, durationHours)
        }

        val record = OvertimeRecord(
            date = dateMs,
            type = type,
            durationHours = durationHours,
            baseSalary = baseSalary,
            rate = rate,
            amount = amount,
            note = note,
            isLeave = isLeave
        )
        val id = repository.insert(record)
        val saved = record.copy(id = id)
        // 与 App 内「保存记录」走同一条链路：推送 / 日历同步 / 自动备份。
        // 旧实现直接 insert，导致 MCP 写入的记录不触发任何联动，看起来像「没生效」。
        // 同步属于旁路能力，失败只记日志，不影响本次工具调用的成功结果。
        try {
            RecordSyncHelper.afterSave(context, repository, settingsRepository, saved)
        } catch (e: Exception) {
            Log.w(TAG, "MCP 保存后的同步动作失败", e)
        }

        val text = if (isLeave) {
            "已添加请假记录：$dateStr，时长 ${-durationHours} 小时，扣款 ¥${-amount}"
        } else {
            "已添加加班记录：$dateStr（${type.name}），时长 $durationHours 小时，预估 ¥$amount"
        }
        return toolResult(
            text = text,
            structuredContent = JsonObject().apply {
                addProperty("success", true)
                addProperty("id", id)
                addProperty("isLeave", isLeave)
                addProperty("durationHours", durationHours)
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
        private const val PROTOCOL_VERSION = "2025-06-18"
        private val SUPPORTED_PROTOCOL_VERSIONS = setOf(
            "2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25"
        )
        private const val SERVER_NAME = "overtime-miuix"
        private const val SERVER_VERSION = "1.0.0"

        private const val CODE_PARSE_ERROR = -32700
        private const val CODE_INVALID_REQUEST = -32600
        private const val CODE_METHOD_NOT_FOUND = -32601
        private const val CODE_INVALID_PARAMS = -32602
        private const val CODE_INTERNAL_ERROR = -32603

        private const val EXTRA_PORT = "port"
        private const val DEFAULT_PORT = 8080
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "mcp_host_service"

        /**
         * 当前实际监听的端口；null 表示服务未运行或启动失败。
         * 进程内与设置页共享：进程被回收后该值自然归零，正好反映真实状态。
         */
        @Volatile
        private var runningPort: Int? = null

        fun isRunning(): Boolean = runningPort != null
        fun runningPortOrNull(): Int? = runningPort

        /** @return 是否成功发出启动请求；端口占用等运行期失败由服务内日志与状态反映。 */
        fun start(context: Context, port: Int): Boolean = try {
            val intent = Intent(context, McpHostService::class.java).apply {
                putExtra(EXTRA_PORT, port)
            }
            // Android 8+ 后台 startService 会抛 IllegalStateException；前台服务启动不受该限制，
            // 且 Android 14+ 要求配合 Manifest 的 foregroundServiceType（服务内立即 startForeground）。
            context.startForegroundService(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "start MCP service failed", e)
            false
        }

        fun stop(context: Context): Boolean = try {
            context.stopService(Intent(context, McpHostService::class.java))
            true
        } catch (e: Exception) {
            Log.e(TAG, "stop MCP service failed", e)
            false
        }
    }
}
