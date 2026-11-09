package com.mars.overtime.util

/**
 * 日志 URL 脱敏：只保留协议与主机，路径与查询参数一律折叠。
 *
 * 项目里多处外部地址把令牌 / 密钥直接放在 path 或 query：
 * - MXNZP 节假日 API：`...?app_id=...&app_secret=...`
 * - 飞书 / 钉钉 / 企业微信 webhook：token 是 path 的一段
 * - WebDAV：用户可能在 URL 内嵌 `user:pass@`，或把令牌写进 path
 *
 * 整条打印等于把凭据写进 logcat（会被 bugreport / 同机调试工具采集）。
 * 因此所有外部地址的日志统一走本函数，保留 host 便于排查 DNS / 连通性即可。
 */
fun redactUrl(raw: String): String = runCatching {
    if (raw.isBlank()) return@runCatching "<empty>"
    val schemeEnd = raw.indexOf("://")
    if (schemeEnd < 0) return@runCatching "<redacted>"
    val hostStart = schemeEnd + 3
    val slash = raw.indexOf('/', hostStart)
    val hostEnd = if (slash < 0) raw.length else slash
    val host = raw.substring(hostStart, hostEnd).substringAfterLast('@')
    raw.substring(0, hostStart) + host + "/<redacted>"
}.getOrDefault("<redacted>")
