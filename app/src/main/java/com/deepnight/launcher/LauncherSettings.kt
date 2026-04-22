package com.deepnight.launcher

import android.content.Context
import androidx.core.content.edit

object LauncherSettings {
    private const val PREFS_NAME = "launcher_prefs"
    private const val KEY_PROXY_ENABLED = "proxy_enabled"
    private const val KEY_PROXY_HOST = "proxy_host"
    private const val KEY_PROXY_PORT = "proxy_port"
    private const val KEY_PROXY_TYPE = "proxy_type" // "HTTP" или "SOCKS"

    fun isProxyEnabled(context: Context): Boolean = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PROXY_ENABLED, false)

    fun setProxyEnabled(context: Context, enabled: Boolean) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_PROXY_ENABLED, enabled) }

    fun getProxyHost(context: Context): String = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PROXY_HOST, "") ?: ""

    fun getProxyPort(context: Context): Int = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_PROXY_PORT, 8080)

    fun getProxyType(context: Context): String = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PROXY_TYPE, "HTTP") ?: "HTTP"

    private const val KEY_ICON_SIZE = "icon_size"

    fun getIconSize(context: Context): Int = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_ICON_SIZE, 80)

    fun saveIconSize(context: Context, size: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putInt(KEY_ICON_SIZE, size) }

    private const val KEY_JACKETT_HOST = "jackett_host"
    private const val KEY_JACKETT_KEY = "jackett_key"
    private const val KEY_TORRSERVE_HOST = "torrserve_host"
    
    private const val KEY_LAST_SEARCH_QUERY = "last_search_query"
    private const val KEY_SCREENSAVER_ENABLED = "screensaver_enabled"

    fun getJackettHost(context: Context): String = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_JACKETT_HOST, "https://jac.red") ?: "https://jac.red"
    fun getJackettKey(context: Context): String = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_JACKETT_KEY, "1") ?: "1"
    fun getTorrServeHost(context: Context): String = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TORRSERVE_HOST, "http://127.0.0.1:8090") ?: "http://127.0.0.1:8090"
    fun saveJackettHost(context: Context, host: String) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_JACKETT_HOST, host) }
    fun saveJackettKey(context: Context, key: String) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_JACKETT_KEY, key) }

    fun saveLastSearchQuery(context: Context, query: String) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_LAST_SEARCH_QUERY, query) }

    fun getLastSearchQuery(context: Context): String = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_SEARCH_QUERY, "") ?: ""

    fun clearLastSearchQuery(context: Context) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { remove(KEY_LAST_SEARCH_QUERY) }

    fun isScreensaverEnabled(context: Context): Boolean = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SCREENSAVER_ENABLED, true)

    fun setScreensaverEnabled(context: Context, enabled: Boolean) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_SCREENSAVER_ENABLED, enabled) }

    private const val KEY_SCREENSAVER_TYPE = "screensaver_type"
    private const val KEY_SCREENSAVER_TIMEOUT = "screensaver_timeout"
    private const val KEY_SCREENSAVER_PREFER_4K = "screensaver_prefer_4k"

    fun getScreensaverType(context: Context): String = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SCREENSAVER_TYPE, "DEEP_NIGHT") ?: "DEEP_NIGHT"
    fun setScreensaverType(context: Context, type: String) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_SCREENSAVER_TYPE, type) }

    fun getScreensaverTimeout(context: Context): Long = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_SCREENSAVER_TIMEOUT, 300_000L) // Default 5 min
    fun setScreensaverTimeout(context: Context, timeoutMs: Long) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putLong(KEY_SCREENSAVER_TIMEOUT, timeoutMs) }

    fun isScreensaverPrefer4K(context: Context): Boolean? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_SCREENSAVER_PREFER_4K)) {
            prefs.getBoolean(KEY_SCREENSAVER_PREFER_4K, true)
        } else null
    }

    fun setScreensaverPrefer4K(context: Context, prefer: Boolean) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_SCREENSAVER_PREFER_4K, prefer) }

    // --- AUDIO ENGINE SETTINGS ---
    private const val KEY_AUDIO_ENABLED = "audio_effects_enabled"
    private const val KEY_AUDIO_NIGHT_MODE = "audio_night_mode"
    private const val KEY_BASS_BOOST = "audio_bass_boost_strength"
    private const val KEY_VIRTUALIZER = "audio_virtualizer_strength"
    private const val KEY_AUDIO_PRESET = "audio_preset"
    private const val KEY_AUDIO_CUSTOM_GAINS = "audio_custom_gains"
    private const val KEY_AUDIO_LOUDNESS = "audio_loudness"

    fun isAudioEffectEnabled(context: Context): Boolean = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUDIO_ENABLED, false)

    fun setAudioEffectEnabled(context: Context, enabled: Boolean) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_AUDIO_ENABLED, enabled) }

    fun isAudioNightMode(context: Context): Boolean = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUDIO_NIGHT_MODE, false)

    fun setAudioNightMode(context: Context, enabled: Boolean) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_AUDIO_NIGHT_MODE, enabled) }

    fun getBassBoostStrength(context: Context): Int = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_BASS_BOOST, 0)

    fun setBassBoostStrength(context: Context, strength: Int) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putInt(KEY_BASS_BOOST, strength) }

    fun getVirtualizerStrength(context: Context): Int = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_VIRTUALIZER, 0)

    fun setVirtualizerStrength(context: Context, strength: Int) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putInt(KEY_VIRTUALIZER, strength) }

    fun getAudioPreset(context: Context): String = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_AUDIO_PRESET, "Movie") ?: "Movie"

    fun setAudioPreset(context: Context, preset: String) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_AUDIO_PRESET, preset) }

    fun getAudioCustomGains(context: Context): FloatArray {
        val str = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_AUDIO_CUSTOM_GAINS, null)
        if (str.isNullOrEmpty()) return floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        return try {
            str.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
    }

    fun setAudioCustomGains(context: Context, gains: FloatArray) {
        val str = gains.joinToString(",")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_AUDIO_CUSTOM_GAINS, str) }
    }

    fun getAudioLoudness(context: Context): Float = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_AUDIO_LOUDNESS, 0.0f)

    fun setAudioLoudness(context: Context, loudness: Float) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putFloat(KEY_AUDIO_LOUDNESS, loudness) }

    private const val KEY_VISUALIZER_OVERLAY = "visualizer_overlay_enabled"
    
    fun isVisualizerOverlayEnabled(context: Context): Boolean = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_VISUALIZER_OVERLAY, true)

    fun setVisualizerOverlayEnabled(context: Context, enabled: Boolean) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_VISUALIZER_OVERLAY, enabled) }

    // --- FAVORITES ---
    private const val KEY_FAVORITE_STATIONS = "favorite_stations"

    fun getFavoriteStations(context: Context): Set<String> = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getStringSet(KEY_FAVORITE_STATIONS, emptySet()) ?: emptySet()

    fun toggleFavoriteStation(context: Context, uuid: String) {
        val current = getFavoriteStations(context).toMutableSet()
        if (current.contains(uuid)) current.remove(uuid) else current.add(uuid)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putStringSet(KEY_FAVORITE_STATIONS, current) }
    }

    fun isStationFavorite(context: Context, uuid: String): Boolean = 
        getFavoriteStations(context).contains(uuid)
}
