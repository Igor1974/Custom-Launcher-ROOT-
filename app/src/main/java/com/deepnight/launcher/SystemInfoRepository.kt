package com.deepnight.launcher

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.util.Log
import com.deepnight.launcher.vpn.VpnController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import utils.PreferencesManager
import java.util.Calendar
import java.util.Locale

object SystemInfoRepository {

    data class LocationData(val lat: Double, val lon: Double, val city: String)

    private var lastTotalRxBytes: Long = 0
    private var lastTimeStamp: Long = 0

    // Кэш данных
    private var cachedWeatherTemp: String = "--°"
    private var cachedWeatherIcon: String = "☁️"
    private var cachedWeatherDesc: String = "Загрузка..."
    private var cachedLocation: LocationData? = null
    private var cachedWeatherContext: WeatherContext? = null
    private var cachedResolutionInfo: String? = null

    /**
     * Собирает статистику для UI.
     */
    fun fetchFullStats(context: Context): SystemStats {
        val vpnInfo = getVpnStatus(context)
        return SystemStats(
            temp = getCpuTemp(),
            ram = getRamInfo(context),
            net = getInternetSpeed(),
            res = getResolutionAndHdr(),
            volume = getVolumeInfo(context),
            audioOut = getAudioOutputName(context),
            weatherTemp = cachedWeatherTemp,
            weatherIcon = cachedWeatherIcon,
            weatherDescription = cachedWeatherDesc,
            vpnActive = vpnInfo.first,
            vpnName = vpnInfo.second
        )
    }

    private fun getCpuTemp(): String {
        return try {
            val result = Shell.cmd("cat /sys/class/thermal/thermal_zone1/temp").exec()
            if (!result.isSuccess) return ""
            val output = result.out.firstOrNull()?.trim() ?: return ""
            val rawTemp = output.toLongOrNull() ?: 0L
            if (rawTemp == 0L) return ""
            val finalTemp = if (rawTemp > 1000 || rawTemp < -1000) rawTemp / 1000 else rawTemp
            "$finalTemp°C"
        } catch (_: Exception) { "" }
    }

    private fun getRamInfo(context: Context): String {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return "${mi.availMem / 1048576L} MB"
    }

    private fun getInternetSpeed(): String {
        val rx = TrafficStats.getTotalRxBytes()
        val now = System.currentTimeMillis()
        val deltaBytes = rx - lastTotalRxBytes
        val deltaTime = now - lastTimeStamp
        if (lastTimeStamp == 0L || deltaBytes < 0) {
            lastTotalRxBytes = rx; lastTimeStamp = now
            return "0 KB/s"
        }
        val speed = if (deltaTime > 0) (deltaBytes * 1000) / deltaTime else 0
        lastTotalRxBytes = rx; lastTimeStamp = now
        return when {
            speed > 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", speed / 1048576.0)
            speed > 1024 -> "${speed / 1024} KB/s"
            else -> "$speed B/s"
        }
    }

    private fun getAudioOutputName(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val types = devices.map { it.type }
        return when {
            types.contains(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) -> "BT"
            types.contains(AudioDeviceInfo.TYPE_USB_DEVICE) -> "USB"
            types.any { it == AudioDeviceInfo.TYPE_HDMI_ARC || it == 41 /* TYPE_HDMI_EARC */ } -> "ARC"
            types.contains(AudioDeviceInfo.TYPE_HDMI) -> "HDMI"
            types.contains(AudioDeviceInfo.TYPE_WIRED_HEADSET) -> "AUX"
            else -> "TV"
        }
    }

    fun getVolumeInfo(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (am.isStreamMute(AudioManager.STREAM_MUSIC)) return "MUTE"
            val curr = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            return if (max > 0) "${(curr * 100) / max}%" else "0%"
        } catch (_: Exception) { return "N/A" }
    }

    private fun getResolutionAndHdr(): String {
        cachedResolutionInfo?.let { return it }
        val size = try {
            Shell.cmd("wm size").exec().out.firstOrNull() ?: ""
        } catch (_: Exception) { "" }
        val res = if (size.contains("3840")) "4K" else "FHD"
        if (res.isNotEmpty()) { cachedResolutionInfo = res }
        return res
    }

    fun getVpnStatus(context: Context): Pair<Boolean, String?> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val isActive = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
            if (isActive) Pair(true, "VPN") else Pair(false, null)
        } catch (_: Exception) { Pair(false, null) }
    }

    // --- ПОГОДА И ГЕОЛОКАЦИЯ ---

    private var lastFoundNetwork: android.net.Network? = null
    private var lastNetworkCheckTime: Long = 0

    fun getNetworkWithoutVpn(context: Context): android.net.Network? {
        val now = System.currentTimeMillis()
        if (now - lastNetworkCheckTime < 5000 && lastFoundNetwork != null) {
            return lastFoundNetwork
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return null

        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            lastFoundNetwork = activeNetwork
            lastNetworkCheckTime = now
            return activeNetwork
        }

        // Если активная сеть - VPN, попробуем поискать другую (редкий случай для TV)
        val networks = cm.allNetworks // Оставляем как fallback для старых API, но с ограничением
        for (network in networks) {
            val nCaps = cm.getNetworkCapabilities(network) ?: continue
            if (nCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !nCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                lastFoundNetwork = network
                lastNetworkCheckTime = now
                return network
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun fetchCoarseLocation(context: Context): LocationData? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var bestLocation: android.location.Location? = null
            
            for (provider in providers) {
                val l = lm.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
            
            bestLocation?.let {
                LocationData(
                    lat = it.latitude,
                    lon = it.longitude,
                    city = "Local Area"
                )
            }
        } catch (e: Exception) {
            Log.e("SystemInfo", "Coarse location error: ${e.message}")
            null
        }
    }

    suspend fun fetchLocationByIp(context: Context): LocationData? = withContext(Dispatchers.IO) {
        // Сначала пытаемся получить реальные координаты, если есть разрешение
        val coarseLoc = fetchCoarseLocation(context)
        if (coarseLoc != null) {
            Log.d("SystemInfo", "Using coarse location: ${coarseLoc.lat}, ${coarseLoc.lon}")
            return@withContext coarseLoc
        }

        try {
            val builder = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)

            getNetworkWithoutVpn(context)?.let { network ->
                builder.socketFactory(network.socketFactory)
                builder.dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return network.getAllByName(hostname).toList()
                    }
                })
            }

            val client = builder.build()
            val request = okhttp3.Request.Builder()
                .url("http://ip-api.com/json/?fields=status,city,lat,lon&lang=ru")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val responseBody = client.newCall(request).execute().body?.string() ?: return@withContext null
            Log.d("SystemInfo", "IP Geolocation Response: $responseBody")

            if (responseBody.contains("\"status\":\"success\"")) {
                val city = responseBody.substringAfter("\"city\":\"").substringBefore("\"")
                val lat = responseBody.substringAfter("\"lat\":").substringBefore(",").toDoubleOrNull() ?: 0.0
                val lon = responseBody.substringAfter("\"lon\":").substringBefore("}")
                    .filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull() ?: 0.0
                LocationData(lat, lon, city)
            } else {
                Log.e("SystemInfo", "IP Geolocation failed: status not success")
                null
            }
        } catch (e: Exception) {
            Log.e("SystemInfo", "IP Geolocation error: ${e.message}")
            null
        }
    }

    suspend fun updateWeather(context: Context): Triple<String, String, String> = withContext(Dispatchers.IO) {
        try {
            val loc = cachedLocation ?: fetchLocationByIp(context)
            if (loc == null) return@withContext Triple(cachedWeatherTemp, cachedWeatherIcon, cachedWeatherDesc)
            cachedLocation = loc

            // Сначала пробуем wttr.in (он надежнее в РФ напрямую)
            val wttrResult = fetchFromWttrIn(context, loc.city)
            if (wttrResult != null) {
                cachedWeatherTemp = wttrResult.first
                cachedWeatherIcon = wttrResult.second
                cachedWeatherDesc = wttrResult.third
                
                // Используем английское название города из локации для промпта
                val cityForPrompt = loc.city
                cachedWeatherContext = WeatherContext(
                    city = cityForPrompt, 
                    weatherDesc = wttrResult.third, 
                    temperature = wttrResult.first.replace("°", "").replace("+", "").toIntOrNull() ?: 0,
                    isDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 6..18
                )
                return@withContext wttrResult
            }

            // Резервный вариант: Open-Meteo
            val url = "http://api.open-meteo.com/v1/forecast?latitude=${loc.lat}&longitude=${loc.lon}&current_weather=true"
            val builder = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)

            getNetworkWithoutVpn(context)?.let { network ->
                builder.socketFactory(network.socketFactory)
                builder.dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> =
                        try { network.getAllByName(hostname).toList() } catch (_: Exception) { emptyList() }
                })
            }

            val responseBody = builder.build().newCall(okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute().body?.string()
                ?: return@withContext Triple(cachedWeatherTemp, cachedWeatherIcon, cachedWeatherDesc)

            val currentSection = responseBody.substringAfter("\"current_weather\":")
            val temp = currentSection.substringAfter("\"temperature\":").substringBefore(",").filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull() ?: 0.0
            val code = currentSection.substringAfter("\"weathercode\":").substringBefore("}").filter { it.isDigit() }.toIntOrNull() ?: 0
            val (icon, desc) = when (code) {
                0 -> "☀️" to "ясно"
                1, 2, 3 -> "🌤" to "переменная облачность"
                45, 48 -> "🌫" to "туман"
                51, 53, 55 -> "🌧" to "морось"
                61, 63, 65 -> "🌧" to "дождь"
                66, 67 -> "🌧" to "ледяной дождь"
                71, 73, 75 -> "❄️" to "снег"
                77 -> "❄️" to "снежные зерна"
                80, 81, 82 -> "🌧" to "ливень"
                85, 86 -> "❄️" to "снегопад"
                95 -> "⛈" to "гроза"
                96, 99 -> "⛈" to "гроза с градом"
                else -> "☁️" to "пасмурно"
            }

            cachedWeatherTemp = "${temp.toInt()}°"; cachedWeatherIcon = icon; cachedWeatherDesc = desc
            cachedWeatherContext = WeatherContext(loc.city, desc, temp.toInt(), Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 6..18)
            Triple(cachedWeatherTemp, cachedWeatherIcon, cachedWeatherDesc)
        } catch (e: Exception) {
            Log.e("SystemInfo", "Weather total error: ${e.message}")
            Triple(cachedWeatherTemp, cachedWeatherIcon, cachedWeatherDesc)
        }
    }

    private suspend fun fetchFromWttrIn(context: Context, city: String): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        try {
            val builder = okhttp3.OkHttpClient.Builder()
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)

            getNetworkWithoutVpn(context)?.let { network ->
                builder.socketFactory(network.socketFactory)
                builder.dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> =
                        try { network.getAllByName(hostname).toList() } catch (_: Exception) { emptyList() }
                })
            }

            val request = okhttp3.Request.Builder()
                .url("http://wttr.in/${city}?format=%t|%c|%C&lang=ru")
                .header("User-Agent", "curl/7.64.1")
                .header("Accept-Language", "ru")
                .build()

            val response = builder.build().newCall(request).execute().body?.string() ?: return@withContext null
            if (response.contains("|")) {
                val parts = response.split("|")
                val temp = parts[0].replace("+", "").trim()
                val icon = parts[1].trim()
                val rawDesc = parts[2].trim().lowercase()
                val desc = translateCondition(rawDesc)
                Log.d("SystemInfo", "Weather from wttr.in: $temp $desc (raw: $rawDesc)")
                return@withContext Triple(temp, icon, desc)
            }
            null
        } catch (e: Exception) {
            Log.w("SystemInfo", "wttr.in failed: ${e.message}")
            null
        }
    }

    private fun translateCondition(desc: String): String {
        val mapping = mapOf(
            "overcast" to "пасмурно",
            "clear" to "ясно",
            "sunny" to "солнечно",
            "partly cloudy" to "переменная облачность",
            "cloudy" to "облачно",
            "mostly cloudy" to "значительная облачность",
            "fog" to "туман",
            "mist" to "дымка",
            "rain" to "дождь",
            "light rain" to "небольшой дождь",
            "heavy rain" to "сильный дождь",
            "snow" to "снег",
            "thunderstorm" to "гроза",
            "drizzle" to "морось"
        )
        return mapping[desc.lowercase()] ?: desc
    }

    suspend fun getWeatherContext(context: Context): WeatherContext? {
        if (cachedWeatherContext == null) updateWeather(context)
        return cachedWeatherContext
    }

    // --- ОБОИ ---

    fun getSavedWallpaperUrl(context: Context): String = PreferencesManager.getWallpaperUrl(context)
    fun saveWallpaperUrl(context: Context, url: String) = PreferencesManager.saveWallpaperUrl(context, url)

    fun buildAIPrompt(weatherContext: WeatherContext?): String {
        val city = weatherContext?.city ?: "Futuristic Metropolis"
        val isDay = weatherContext?.isDay ?: false
        val desc = weatherContext?.weatherDesc?.lowercase() ?: ""
        
        val weatherStyle = when {
            desc.contains("ясно") || desc.contains("солнечно") -> "clear sky, vibrant colors"
            desc.contains("облачно") || desc.contains("пасмурно") -> "overcast, dramatic moody clouds"
            desc.contains("дождь") || desc.contains("морось") -> "rainy streets, water reflections"
            desc.contains("снег") -> "heavy snow, winter aesthetic"
            desc.contains("туман") -> "foggy atmosphere, mystical haze"
            desc.contains("гроза") -> "thunderstorm, lightning flashes, dark sky"
            else -> "cinematic landscape"
        }
        val timeStyle = if (isDay) "golden hour, natural sunlight" else "blue hour, cinematic night lighting"

        return "Dark futuristic cyberpunk version of $city, $weatherStyle, $timeStyle, " +
                "neon accents on pitch-black buildings, space, volumetric fog, Unreal Engine 5 render, 8k."
    }

    // --- ОПТИМИЗАЦИЯ И VPN ---

    fun boostSystem(context: Context): String {
        val myPkg = context.packageName
        try {
            Shell.cmd(
                $$"for p in $(pm list packages -3 | cut -f 2 -d ':'); do [ \"$p\" != \"$$myPkg\" ] && am force-stop $p; done",
                "sync; echo 3 > /proc/sys/vm/drop_caches"
            ).exec()
            return "СИСТЕМА УСКОРЕНА"
        } catch (_: Exception) { return "ROOT ERROR" }
    }

    fun stopVpn(context: Context) {
        // Пытаемся остановить наш встроенный VPN
        try {
            VpnController.stopVpn(context)
        } catch (_: Exception) {}

        // Останавливаем все остальные известные VPN приложения
        Shell.cmd(
            "am force-stop com.v2raytun.android",
            "am force-stop ru.yourok.num",
            "am force-stop de.blinkt.openvpn"
        ).submit()
    }

    /**
     * Переключает VPN (АнтиЗапрет).
     * Теперь работает автономно без NUM.
     */
    suspend fun toggleVpnSync(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (getVpnStatus(context).first) {
            stopVpn(context)
            return@withContext false
        }

        try {
            // Включаем встроенный VPN
            VpnController.downloadAndStart(context)

            // Ждем установки соединения (до 15 секунд)
            var attempts = 30
            while (attempts > 0) {
                if (getVpnStatus(context).first) return@withContext true
                delay(500)
                attempts--
            }

            return@withContext false
        } catch (e: Exception) {
            Log.e("SystemInfo", "Toggle VPN Error: ${e.message}")
            return@withContext false
        }
    }
}
