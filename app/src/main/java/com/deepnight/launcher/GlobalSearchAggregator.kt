package com.deepnight.launcher

import android.app.SearchManager
import android.content.Context
import android.database.Cursor
import android.net.Network
import android.net.Uri
import android.net.Uri.parse
import android.util.Log
import com.deepnight.launcher.parser.*
import com.deepnight.launcher.radio.RadioManager
import com.deepnight.launcher.radio.RadioStation
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import okhttp3.Request

data class TMDBSearchResponse(@SerializedName("results") val results: List<TMDBMovie>?)
data class TMDBMovie(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("original_title") val originalTitle: String?,
    @SerializedName("original_name") val originalName: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?
)

class GlobalSearchAggregator(private val context: Context) {
    private val gson = Gson()
    private val tmdbApiKey = "45ddf563ac3fb845f2d5c363190d1a33"
    private val tmdbApiHosts = listOf("tmdb.torrs.ru", "nmapi.duckdns.org", "api.themoviedb.org", "tmdb.lib.id", "releases.yourok.ru")

    private val appNames = mapOf(
        "ru.yourok.num" to "NUM",
        "com.lazycatsoftware.lmd" to "Lazy",
        "ru.kinopoisk.tv" to "Кинопоиск",
        "ru.ivi.client" to "IVI",
        "ru.mts.mtstv" to "MTS TV",
        "com.ertelecom.domrutvstb" to "Movix",
        "top.rootu.lamps" to "Lampa",
        "top.rootu.lampa" to "Lampa"
    )

    private fun isPackageInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) { false }

    suspend fun fetchAllResults(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        Log.d("Aggregator", "===> fetchAllResults STARTED for: $query")
        if (query.length < 2) return@withContext emptyList()
        
        try {
            // Отключаем VPN для прямой связи с пирами и максимальной скорости поиска
            stopAnyVPN(context)
        } catch (e: Exception) {
            Log.e("Aggregator", "stopAnyVPN failed: ${e.message}")
        }

        val jobs = mutableListOf<Deferred<List<SearchResult>>>()

        Log.d("Aggregator", "Adding TMDB jobs")
        jobs.add(async { 
            try { searchTMDB(query) } catch(e: Exception) { Log.e("Aggregator", "TMDB error: ${e.message}"); emptyList() }
        })
        val cleanedQuery = cleanTitleForMatch(query)
        if (cleanedQuery.length >= 3 && cleanedQuery != query.lowercase().trim()) {
            jobs.add(async { searchTMDB(cleanedQuery) })
        }

        Log.d("Aggregator", "Adding Radio jobs")
        jobs.add(async {
            try {
                withTimeout(5000) {
                    RadioManager.searchStations(context, query)
                        .map { it.toSearchResult() }
                }
            } catch (e: Exception) {
                Log.e("Aggregator", "Radio search error: ${e.message}")
                emptyList()
            }
        })

        AppSearchConfig.providerConfigs.forEach { (pkg, configs) ->
            if (isPackageInstalled(pkg)) {
                configs.forEach { config ->
                    jobs.add(async {
                        try {
                            withTimeout(5000) {
                                queryProvider(appNames[pkg] ?: pkg, pkg, config.authority, config.uriPath, query, config)
                            }
                        } catch (_: Exception) { emptyList() }
                    })
                }
            }
        }

        val jackettHost = LauncherSettings.getJackettHost(context)
        val jackettKey = LauncherSettings.getJackettKey(context)
        val minSizeMb = 1500.0 // Исключаем мелкие файлы (минимум 1.5 ГБ)

        Log.d("Aggregator", "Adding Torrent source jobs (Jackett Host: $jackettHost)")
        jobs.add(async {
            try {
                withTimeout(7000) {
                    TorrServeClient(context).search(query)
                        .filter { 
                            val sizeMb = parseSizeToMb(it.size)
                            val titleLower = it.title.lowercase()
                            sizeMb >= minSizeMb && !titleLower.contains("720p") && !titleLower.contains("hdrip")
                        }
                        .map { it.toSearchResult("TorrServe", "ru.yourok.torrserve") }
                }
            } catch (_: Exception) { emptyList() }
        })

        jobs.add(async {
            try {
                withTimeout(10000) {
                    Log.d("Aggregator", "Starting Jackett search for: $query")
                    val results = JackettParser(jackettHost, jackettKey).search(query)
                        .filter { 
                            val sizeMb = parseSizeToMb(it.size)
                            val titleLower = it.title.lowercase()
                            sizeMb >= minSizeMb && !titleLower.contains("720p") && !titleLower.contains("hdrip")
                        }
                        .map { it.toSearchResult(it.source, "ru.yourok.torrserve") }
                    Log.d("Aggregator", "Jackett finished. Found ${results.size} filtered items")
                    results
                }
            } catch (e: Exception) { 
                Log.e("Aggregator", "Jackett error: ${e.message}")
                emptyList<SearchResult>() 
            }
        })

        jobs.add(async {
            try {
                withTimeout(7000) {
                    RutorParser().search(query)
                        .filter { 
                            val sizeMb = parseSizeToMb(it.size)
                            val titleLower = it.title.lowercase()
                            sizeMb >= minSizeMb && !titleLower.contains("720p") && !titleLower.contains("hdrip")
                        }
                        .map { it.toSearchResult("Rutor", "ru.yourok.torrserve") }
                }
            } catch (_: Exception) { emptyList() }
        })

        jobs.add(async {
            try {
                withTimeout(7000) {
                    MegaPeerParser().search(query)
                        .filter { 
                            val sizeMb = parseSizeToMb(it.size)
                            val titleLower = it.title.lowercase()
                            sizeMb >= minSizeMb && !titleLower.contains("720p") && !titleLower.contains("hdrip")
                        }
                        .map { it.toSearchResult("MegaPeer", "ru.yourok.torrserve") }
                }
            } catch (_: Exception) { emptyList() }
        })

        jobs.add(async {
            try {
                withTimeout(7000) {
                    NUMParser().search(query)
                        .filter { 
                            val sizeMb = parseSizeToMb(it.size)
                            val titleLower = it.title.lowercase()
                            sizeMb >= minSizeMb && !titleLower.contains("720p") && !titleLower.contains("hdrip")
                        }
                        .map { it.toSearchResult("NUM", "ru.yourok.torrserve") }
                }
            } catch (_: Exception) { emptyList() }
        })

        jobs.add(async {
            try {
                withTimeout(7000) {
                    TPBParser().search(query)
                        .filter { 
                            val sizeMb = parseSizeToMb(it.size)
                            val titleLower = it.title.lowercase()
                            sizeMb >= minSizeMb && !titleLower.contains("720p") && !titleLower.contains("hdrip")
                        }
                        .map { it.toSearchResult("TPB", "ru.yourok.torrserve") }
                }
            } catch (_: Exception) { emptyList() }
        })

        jobs.add(async {
            try {
                withTimeout(7000) {
                    TorrsParser().search(query)
                        .filter { 
                            val sizeMb = parseSizeToMb(it.size)
                            val titleLower = it.title.lowercase()
                            sizeMb >= minSizeMb && !titleLower.contains("720p") && !titleLower.contains("hdrip")
                        }
                        .map { it.toSearchResult("Torrs", "ru.yourok.torrserve") }
                }
            } catch (_: Exception) { emptyList() }
        })

        Log.d("Aggregator", "Waiting for all jobs: ${jobs.size} jobs launched")
        val allResults = jobs.awaitAll().flatten()
        Log.d("Aggregator", "All results collected: ${allResults.size} items")
        val tmdbList = allResults.filter { it.sourceApp == "Кино" }
        val torrentList = allResults.filter { it.sourceApp != "Кино" }

        val finalResults = allResults.map { result ->
            if (result.sourceApp == "Кино") {
                // Ищем лучший торрент для карточки Кино, чтобы показать размер и качество
                val bestMatch = findBestTorrentForTMDB(result, torrentList)
                result.copy(
                    size = bestMatch?.size,
                    quality = bestMatch?.quality,
                    intentData = bestMatch?.intentData ?: result.intentData, // Привязываем магнит лучшей раздачи или оставляем поиск
                    posterUrl = normalizePosterUrl(result.posterUrl)
                )
            } else {
                val needsPoster = result.posterUrl.isNullOrEmpty()
                val needsYear = result.year.isNullOrEmpty()

                val matchedTMDB = if (needsPoster || needsYear) {
                    findBestTMDBMatch(result.title, tmdbList)
                } else null

                result.copy(
                    posterUrl = normalizePosterUrl(if (needsPoster) matchedTMDB?.posterUrl else result.posterUrl),
                    year = if (needsYear) matchedTMDB?.year ?: extractYear(result.title) else result.year,
                    quality = if (result.quality.isNullOrEmpty()) extractQuality(result.title) else result.quality
                )
            }
        }

        finalResults.sortedWith(
            compareByDescending<SearchResult> { it.sourceApp == "Кино" }
                .thenByDescending { !it.posterUrl.isNullOrEmpty() && it.quality != null }
                .thenByDescending { it.sourceApp == "RuTracker" || it.sourceApp == "Jackett" }
                .thenByDescending { it.quality?.contains("4K") == true }
                .thenByDescending { it.quality?.contains("REMUX") == true }
                .thenByDescending { it.quality?.contains("1080p") == true }
                .thenByDescending { parseSizeToMb(it.size) }
        ).distinctBy {
            if (it.intentData?.startsWith("magnet:") == true) {
                // Извлекаем InfoHash (btih) из magnet-ссылки. Обычно это 40 символов после xt=urn:btih:
                val match = Regex("xt=urn:btih:([a-zA-Z0-9]{40})").find(it.intentData)
                match?.groupValues?.get(1)?.lowercase() ?: it.intentData
            } else {
                "${it.title?.lowercase()}_${it.sourceApp}"
            }
        }
    }

    private fun normalizePosterUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        // Если это уже наше зеркало, ничего не делаем
        if (url.contains("nmtmdb.duckdns.org")) return url
        
        // Если это ссылка на TMDB или старые зеркала
        if (url.contains("tmdb.org") || url.contains("tmdb.torrs.ru") || url.contains("releases.yourok.ru/tmdb")) {
            val path = url.substringAfter("/t/p/", "")
            if (path.isNotEmpty()) {
                return "https://nmtmdb.duckdns.org/t/p/$path"
            }
        }
        return url
    }

    // toSearchResult перенесен в Models.kt

    // extractYear перенесен в Models.kt

    // extractQuality перенесен в Models.kt

    // parseSizeToMb перенесен в Models.kt

    private fun findBestTorrentForTMDB(tmdb: SearchResult, torrents: List<SearchResult>): SearchResult? {
        val cleanTmdb = cleanTitleForMatch(tmdb.title)
        val cleanOrig = cleanTitleForMatch(tmdb.description)
        
        return torrents.filter { torrent ->
            val cleanTorrent = cleanTitleForMatch(torrent.title)
            val isMatch = cleanTorrent.isNotEmpty() && (cleanTorrent.contains(cleanTmdb) || (cleanOrig.isNotEmpty() && cleanTorrent.contains(cleanOrig)))
            val isYearMatch = tmdb.year == null || torrent.year == null || tmdb.year == torrent.year
            
            val tTitle = torrent.title?.lowercase() ?: ""
            isMatch && isYearMatch && !tTitle.contains("camrip") && !tTitle.contains("ts") && !tTitle.contains("720p") && !tTitle.contains("hdrip")
        }.maxWithOrNull(
            compareBy<SearchResult> { 
                // Приоритет источнику
                when (it.sourceApp) {
                    "RuTracker", "Jackett" -> 10
                    "Rutor" -> 8
                    "MegaPeer" -> 6
                    "Torrs" -> 5
                    else -> 1
                }
            }.thenBy { it.quality?.contains("4K") == true }
             .thenBy { it.quality?.contains("1080p") == true }
             .thenBy { parseSizeToMb(it.size) }
        )
    }

    private fun findBestTMDBMatch(torrentTitle: String?, tmdbResults: List<SearchResult>): SearchResult? {
        if (torrentTitle == null || tmdbResults.isEmpty()) return null
        val cleanTorrent = cleanTitleForMatch(torrentTitle)
        if (cleanTorrent.isEmpty()) return null
        
        val torrentYear = extractYear(torrentTitle)

        return tmdbResults.map { tmdb ->
            var score = 0
            val cleanTmdb = cleanTitleForMatch(tmdb.title)
            val cleanOrig = cleanTitleForMatch(tmdb.description)
            val tmdbYear = tmdb.year

            // 1. Точное совпадение имен (высший приоритет)
            if (cleanTorrent == cleanTmdb || (cleanOrig.isNotEmpty() && cleanTorrent == cleanOrig)) {
                score += 120
            } 
            // 2. Частичное совпадение
            else if (cleanTorrent.contains(cleanTmdb, ignoreCase = true) || 
                (cleanOrig.isNotEmpty() && cleanTorrent.contains(cleanOrig, ignoreCase = true)) ||
                cleanTmdb.contains(cleanTorrent, ignoreCase = true)) {
                score += 80
            }

            // 3. Совпадение года (критично для торрентов)
            if (torrentYear != null && tmdbYear != null) {
                if (torrentYear == tmdbYear) {
                    score += 100
                } else {
                    score -= 80 // Штраф за другой год
                }
            }

            tmdb to score
        }.filter { it.second >= 60 } // Порог 60 для точности
         .maxByOrNull { it.second }
         ?.first
    }

    private fun stopAnyVPN(context: Context) {
        try {
            // Прямая остановка через ProfileManager и VpnStatus (из OpenVPN core)
            de.blinkt.openvpn.core.ProfileManager.setConnectedVpnProfileDisconnected(context)
            // Устанавливаем флаг в App, чтобы UI не пытался переподключиться
            de.blinkt.openvpn.core.App.isStart = false
            Log.d("Aggregator", "VPN stopped for direct torrent connection")
        } catch (e: Exception) {
            Log.e("Aggregator", "Failed to stop VPN: ${e.message}")
        }
    }

    private fun queryProvider(name: String, pkg: String, authority: String, path: String, query: String, config: ProviderConfig): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val baseUri = parse("content://$authority").buildUpon().appendEncodedPath(path).build()
        val urisToTry = listOf(baseUri, baseUri.buildUpon().appendPath(query).build(), baseUri.buildUpon().appendQueryParameter(config.queryParam, query).build())

        for (uri in urisToTry) {
            try {
                Log.d("Aggregator", "Querying provider: $uri")
                context.contentResolver.query(uri, null, null, arrayOf(query), null)?.use { cursor ->
                    val titleIdx = findColumn(cursor, config.titleColumn, SearchManager.SUGGEST_COLUMN_TEXT_1, "suggest_text_1", "title")
                    val iconIdx = findColumn(cursor, config.posterColumn, SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE, SearchManager.SUGGEST_COLUMN_ICON_1, "suggest_icon_1", "poster")
                    val dataIdx = findColumn(cursor, config.idColumn, SearchManager.SUGGEST_COLUMN_INTENT_DATA, "suggest_intent_data", "_id")

                    if (cursor.count > 0) {
                        while (cursor.moveToNext()) {
                            val title = getString(cursor, titleIdx) ?: continue
                            var icon = getString(cursor, iconIdx)
                            var data = getString(cursor, dataIdx)
                            if (!icon.isNullOrEmpty() && !icon.startsWith("http") && !icon.startsWith("content")) icon = "android.resource://$pkg/$icon"
                            if (!data.isNullOrEmpty() && !data.contains(":")) {
                                AppSearchConfig.deepLinkSchemes[pkg]?.let { scheme -> data = if (scheme.contains("%s")) String.format(scheme, data) else scheme + data }
                            }
                            results.add(SearchResult(
                                title = title, 
                                intentData = data, 
                                sourceApp = name, 
                                packageName = pkg, 
                                posterUrl = normalizePosterUrl(icon), 
                                year = extractYear(title), 
                                quality = extractQuality(title)
                            ))
                        }
                        if (results.isNotEmpty()) return results
                    }
                }
            } catch (e: Exception) {
                Log.w("Aggregator", "Provider error ($name, $authority): ${e.message}")
            }
        }
        return results
    }

    private suspend fun searchTMDB(query: String): List<SearchResult> = coroutineScope {
        val network: Network? = SystemInfoRepository.getNetworkWithoutVpn(context)
        val client = if (network != null) {
            TorrentNetworkClient.client.newBuilder()
                .socketFactory(network.socketFactory)
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        } else {
            TorrentNetworkClient.client.newBuilder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }

        val deferredResults = tmdbApiHosts.map { host ->
            async(Dispatchers.IO) {
                try {
                    val paths = when {
                        host.contains("yourok") || host.contains("torrs") -> listOf("tmdb/3/")
                        host.contains("nmapi") -> listOf("")
                        else -> listOf("3/")
                    }
                    
                    for (pathPrefix in paths) {
                        val url = "https://$host/${pathPrefix}search/multi?api_key=$tmdbApiKey&language=ru-RU&query=${Uri.encode(query)}"
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string() ?: return@use null
                                val data = gson.fromJson(body, TMDBSearchResponse::class.java)
                                val resultsList = data.results?.filter { it.mediaType != "person" } ?: return@use null

                                return@async resultsList.map { movie ->
                                    val posterUrl = movie.posterPath?.let { path ->
                                        "https://nmtmdb.duckdns.org/t/p/w342$path"
                                    }
                                    SearchResult(
                                        title = movie.title ?: movie.name ?: "",
                                        description = movie.originalTitle ?: movie.originalName ?: "",
                                        posterUrl = posterUrl,
                                        sourceApp = "Кино",
                                        packageName = "tmdb",
                                        year = (movie.releaseDate ?: movie.firstAirDate)?.take(4)
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Aggregator", "TMDB error on $host: ${e.message}")
                }
                null
            }
        }

        // Возвращаем результат от первого успешного хоста
        for (deferred in deferredResults) {
            val res = deferred.await()
            if (!res.isNullOrEmpty()) return@coroutineScope res
        }
        emptyList<SearchResult>()
    }

    private fun findColumn(cursor: Cursor, vararg names: String?): Int {
        for (n in names) if (!n.isNullOrEmpty()) {
            try {
                val i = cursor.getColumnIndex(n)
                if (i != -1) return i
            } catch(_: Exception) {}
        }
        return -1
    }

    private fun getString(cursor: Cursor, index: Int): String? = try {
        if (index != -1 && !cursor.isNull(index)) cursor.getString(index) else null
    } catch(_: Exception) { null }
}
