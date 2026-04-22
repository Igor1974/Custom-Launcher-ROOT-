package com.deepnight.launcher

import kotlinx.serialization.Serializable
import java.net.URLDecoder

object VpnConfigGenerator {

    @Serializable
    data class VpnServer(
        val uuid: String,
        val address: String,
        val port: Int,
        val pbk: String? = null,
        val sid: String? = null,
        val sni: String? = null,
        val type: String? = null,
        val path: String? = null,
        val host: String? = null,
        val flow: String? = null,
        val security: String? = null,
        val encryption: String? = null,
        val headerType: String? = null,
        val fp: String? = null,
        val allowInsecure: Boolean? = null,
        val remark: String? = null
    )

    /**
     * Парсит vless строку. Извлекает все параметры для последующей генерации.
     */
    fun parseVless(line: String): VpnServer? {
        val cleanLine = line.trim()
        if (!cleanLine.startsWith("vless://")) return null
        return try {
            // Отделяем анкор (комментарий после #)
            val withoutAnchor = cleanLine.substring(8).substringBefore("#")
            val anchor = cleanLine.substringAfter("#", "")
            val remark = if (anchor.isNotBlank()) URLDecoder.decode(anchor, "UTF-8") else null

            val parts = withoutAnchor.split("@")
            if (parts.size != 2) return null

            val uuid = parts[0].trim()
            val addrPortPart = parts[1].substringBefore("?")
            val address = addrPortPart.substringBefore(":").trim()
            val port = addrPortPart.substringAfter(":").substringBefore("/").toIntOrNull() ?: 443

            val query = parts[1].substringAfter("?", "")
            val params = mutableMapOf<String, String>()

            query.split("&").forEach { param ->
                val kv = param.split("=", limit = 2)
                if (kv.size == 2) {
                    params[kv[0]] = URLDecoder.decode(kv[1], "UTF-8")
                }
            }

            val pbk = params["pbk"]
            val sid = params["sid"]
            val sni = params["sni"]
            val type = params["type"]
            val path = params["path"]
            val host = params["host"]
            val flow = params["flow"]
            val security = params["security"]
            val encryption = params["encryption"]
            val headerType = params["headerType"]
            val fp = params["fp"]
            val allowInsecure = params["allowInsecure"]?.let { it == "1" || it == "true" }

            VpnServer(
                uuid = uuid,
                address = address,
                port = port,
                pbk = pbk,
                sid = sid,
                sni = sni,
                type = type,
                path = path,
                host = host,
                flow = flow,
                security = security,
                encryption = encryption,
                headerType = headerType,
                fp = fp,
                allowInsecure = allowInsecure,
                remark = remark
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Генерирует конфиг на основе случайного сервера из списка.
     * Сохраняет оригинальные параметры, где это возможно, добавляя только недостающие.
     */
    fun generateAutonomousVless(servers: List<VpnServer>): String {
        if (servers.isEmpty()) return ""
        val server = servers.random()

        val queryParams = mutableListOf<String>()

        // Добавляем все не-null параметры, кроме тех, которые могут быть специфичны для типа
        server.security?.let { queryParams.add("security=$it") }
        server.encryption?.let { queryParams.add("encryption=$it") }
        server.pbk?.let { queryParams.add("pbk=$it") }
        server.sid?.let { queryParams.add("sid=$it") }
        server.sni?.let { queryParams.add("sni=$it") }
        server.type?.let { queryParams.add("type=$it") }
        server.path?.let { queryParams.add("path=$it") }
        server.host?.let { queryParams.add("host=$it") }
        server.flow?.let { queryParams.add("flow=$it") }
        server.headerType?.let { queryParams.add("headerType=$it") }
        server.fp?.let { queryParams.add("fp=$it") }
        server.allowInsecure?.let { queryParams.add("allowInsecure=${if (it) "1" else "0"}") }

        // Если отсутствует security и есть pbk, вероятно это reality
        if (server.security == null && server.pbk != null) {
            queryParams.add("security=reality")
        }
        // Если отсутствует encryption и pbk есть, то добавляем encryption=none
        if (server.encryption == null && server.pbk != null) {
            queryParams.add("encryption=none")
        }
        // Если отсутствует flow и есть pbk, добавляем xtls-rprx-vision
        if (server.flow == null && server.pbk != null) {
            queryParams.add("flow=xtls-rprx-vision")
        }
        // Для ws, если не указан security, ставим none (но лучше оставить как есть)
        if (server.type == "ws" && server.security == null) {
            queryParams.add("security=none")
        }

        // Удаляем дубликаты параметров (сохраняем последний)
        val uniqueParams = queryParams.distinctBy { it.substringBefore("=") }

        val queryString = uniqueParams.joinToString("&")
        val remark = server.remark ?: "DeepNight-${server.sni?.take(8) ?: "Smart"}"
        return "vless://${server.uuid}@${server.address}:${server.port}?$queryString#$remark"
    }
}
