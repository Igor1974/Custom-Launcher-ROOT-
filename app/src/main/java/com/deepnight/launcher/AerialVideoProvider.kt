package com.deepnight.launcher

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object AerialVideoProvider {
    private const val TAG = "AerialProvider"
    
    // Публичный клиент для ExoPlayer и Coil
    val client = createUnsafeOkHttpClient()

    private val SOURCES = listOf(
        "http://sylvan.apple.com/Aerials/2x/entries.json",
        "https://sylvan.apple.com/Aerials/2x/entries.json",
        "https://sylvan.apple.com/Aerials/resources.json",
        "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/entries.json"
    )

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            
            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "AppleTV/11.1")
                        .removeHeader("Accept") // Фикс для 400 Bad Request
                        .build()
                    chain.proceed(request)
                }
                .build()
        } catch (e: Exception) {
            return OkHttpClient.Builder().build()
        }
    }

    data class AerialVideo(
        val id: String,
        val url: String,
        val name: String,
        val is4K: Boolean = false,
        val type: String = "video"
    )

    suspend fun fetchVideos(prefer4K: Boolean = false): List<AerialVideo> = withContext(Dispatchers.IO) {
        val allVideos = mutableListOf<AerialVideo>()
        
        for (sourceUrl in SOURCES) {
            try {
                val request = Request.Builder()
                    .url(sourceUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonText = response.body?.string() ?: ""
                        if (jsonText.trim().startsWith("{")) {
                            parseJsonObject(JSONObject(jsonText), allVideos, prefer4K)
                        } else if (jsonText.trim().startsWith("[")) {
                            parseJsonArray(JSONArray(jsonText), allVideos, prefer4K)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch from $sourceUrl: ${e.message}")
            }
        }

        if (allVideos.isEmpty()) {
            allVideos.addAll(getFallbackVideos())
        }
        
        Log.d(TAG, "Fetched ${allVideos.size} videos total (Prefer4K: $prefer4K)")
        allVideos.distinctBy { it.url }.shuffled()
    }

    private fun getFallbackVideos(): List<AerialVideo> {
        return listOf(
            AerialVideo("1", "https://sylvan.apple.com/Aerials/2x/Videos/LA_A006_C008_4K_SDR_HEVC.mov", "Los Angeles", true),
            AerialVideo("2", "https://sylvan.apple.com/Aerials/2x/Videos/LW_L001_C006_4K_SDR_HEVC.mov", "Liwa", true),
            AerialVideo("3", "https://sylvan.apple.com/Aerials/2x/Videos/DB_D008_C010_4K_SDR_HEVC.mov", "Dubai", true),
            AerialVideo("4", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b10-2.mov", "New York", false)
        )
    }

    private fun parseJsonObject(obj: JSONObject, list: MutableList<AerialVideo>, prefer4K: Boolean) {
        val keys = listOf("assets", "entries")
        for (key in keys) {
            if (obj.has(key)) {
                val arr = obj.optJSONArray(key)
                if (arr != null) parseJsonArray(arr, list, prefer4K)
            }
        }
    }

    private fun parseJsonArray(arr: JSONArray, list: MutableList<AerialVideo>, prefer4K: Boolean) {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            
            if (item.has("assets")) {
                parseJsonArray(item.getJSONArray("assets"), list, prefer4K)
                continue
            }

            val id = item.optString("id", i.toString())
            
            val url4K = item.optString("url-4K-SDR", item.optString("url-4K-HDR", ""))
            val url1080 = item.optString("url-1080-SDR", item.optString("url", ""))
            
            // Логика выбора URL на основе предпочтений
            var url = if (prefer4K && url4K.isNotEmpty()) {
                url4K
            } else if (url1080.isNotEmpty()) {
                url1080
            } else if (url4K.isNotEmpty()) {
                url4K
            } else {
                item.optString("url", "")
            }
            
            if (url.isEmpty()) continue

            // Фиксы протоколов для стабильности
            // Apple перенесла почти все на https://sylvan...
            if (url.contains("sylvan.apple.com")) {
                url = url.replace("http://", "https://")
            }
            // phobos.apple.com часто не поддерживает https
            if (url.contains("phobos.apple.com")) {
                url = url.replace("https://", "http://")
            }

            // Дополнительный фикс: apple часто меняет 2x на 4k или ресурсы
            if (url.contains("/2x/Videos/")) {
                // Если мы получили 404, в следующий раз плеер попробует другой вариант, 
                // но здесь мы просто нормализуем строку
            }

            val name = item.optString("accessibilityLabel", 
                       item.optString("name", "Aerial Video"))

            list.add(AerialVideo(id, url, name, url.contains("4K")))
        }
    }
}
