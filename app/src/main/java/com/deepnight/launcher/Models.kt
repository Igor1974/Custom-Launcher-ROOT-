package com.deepnight.launcher

import com.deepnight.launcher.model.TorrentResult
import android.graphics.drawable.Drawable

data class SystemStats(
    val temp: String = "--°C",
    val ram: String = "-- MB",
    val net: String = "0 KB/s",
    val res: String = "FHD",
    val volume: String = "0%",
    val audioOut: String = "TV",
    val weatherTemp: String = "?°",
    val weatherIcon: String = "☁️",
    val weatherDescription: String = "Загрузка...",
    val vpnActive: Boolean = false,
    val vpnName: String? = null
)

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
) {
    val iconDrawable: Drawable get() = icon
}

data class WeatherContext(
    val city: String,
    val weatherDesc: String,
    val temperature: Int,
    val isDay: Boolean
)

// TorrentResult перенесен в package com.deepnight.launcher.model

data class SearchResult(
    val title: String? = null,
    val description: String? = null,
    val posterUrl: String? = null,
    val intentData: String? = null,
    val sourceApp: String,
    val packageName: String? = null,
    val intentAction: String? = null,
    val hash: String? = null,
    val icon: Drawable? = null,
    val size: String? = null,
    val year: String? = null,
    val quality: String? = null
)

fun TorrentResult.toSearchResult(source: String, pkg: String): SearchResult {
    return SearchResult(
        title = this.title,
        intentData = this.magnet,
        sourceApp = source,
        packageName = pkg,
        size = this.size,
        year = extractYear(this.title),
        quality = extractQuality(this.title),
        posterUrl = null // Будет заполнено позже агрегатором
    )
}

fun com.deepnight.launcher.radio.RadioStation.toSearchResult(): SearchResult {
    return SearchResult(
        title = this.name,
        description = this.tags,
        posterUrl = this.favicon,
        intentData = "radio:${this.uuid}", // Специальный формат для RadioManager
        sourceApp = "Радио",
        packageName = "com.deepnight.launcher.radio",
        quality = if (this.bitrate > 0) "${this.bitrate} kbps" else null
    )
}

fun parseSizeToMb(sizeStr: String?): Double {
    if (sizeStr.isNullOrBlank()) return 0.0
    try {
        val cleanSize = sizeStr.replace(",", ".").uppercase().trim()
        val value = Regex("[0-9.]+").find(cleanSize)?.value?.toDoubleOrNull() ?: return 0.0

        return when {
            cleanSize.contains("GB") || cleanSize.contains("ГБ") -> value * 1024.0
            cleanSize.contains("TB") || cleanSize.contains("ТБ") -> value * 1024.0 * 1024.0
            cleanSize.contains("MB") || cleanSize.contains("МБ") -> value
            cleanSize.contains("KB") || cleanSize.contains("КБ") -> value / 1024.0
            else -> if (value > 500) value / 1024.0 / 1024.0 else value
        }
    } catch (_: Exception) {
        return 0.0
    }
}

fun extractYear(title: String?): String? {
    if (title == null) return null
    val match = Regex("(?:\\[|\\(|\\s|\\.|^)(19|20)(\\d{2})(?:]|\\)|\\s|\\.|$)").find(title)
    return match?.let { it.groupValues[1] + it.groupValues[2] }
}

fun extractQuality(title: String?): String? {
    if (title == null) return null
    val t = title.uppercase()
    return when {
        t.contains("2160") || t.contains("4K") || t.contains("UHD") -> "4K UHD"
        t.contains("BDREMUX") || t.contains("REMUX") -> "REMUX"
        t.contains("1080") || t.contains("FHD") -> "1080p"
        t.contains("720") || t.contains("HD") -> "720p"
        t.contains("HDR10") || t.contains("HDR") -> "HDR"
        t.contains("10BIT") || t.contains("HEVC") || t.contains("H.264") || t.contains("H.265") || t.contains("X264") || t.contains("X265") -> "HEVC"
        t.contains("WEB-DL") || t.contains("WEBRIP") || t.contains("WEB DL") -> "WEB-DL"
        t.contains("BDRIP") || t.contains("BLURAY") -> "BDRip"
        else -> null
    }
}

fun cleanTitleForMatch(title: String?): String {
    if (title == null) return ""
    return title.lowercase()
        .replace(Regex("(?i)🧲|j:|ts:|mp:|s\\d+e\\d+|сезон|серия|полная|версия|\\d{1,2}-\\d{1,2}|перевод|дубляж|лицензия|itunes|звук"), " ")
        .replace(Regex("\\[.*?]|\\(.*?\\)"), " ")
        .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
        .replace(Regex("\\b(1080p|720p|2160p|4k|fhd|uhd|h\\.264|h\\.265|x264|x265|avc|hevc|web-dl|bluray|bdrip|dvdrip|mkv|avi|mp4|rus|eng|itunes|line|remux|bdremux)\\b"), " ")
        .replace(Regex("[^a-zа-я0-9]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
