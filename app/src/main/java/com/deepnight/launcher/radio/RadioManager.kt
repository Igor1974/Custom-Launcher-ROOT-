package com.deepnight.launcher.radio

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.deepnight.launcher.visualizer.AudioVisualizerManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RadioManager {
    private var player: ExoPlayer? = null
    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private val _currentStation = mutableStateOf<RadioStation?>(null)
    val currentStation: State<RadioStation?> = _currentStation

    private val _visualizerManager = mutableStateOf<AudioVisualizerManager?>(null)
    val visualizer: State<AudioVisualizerManager?> = _visualizerManager

    private var cachedLocalStations: List<RadioStation>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            // Убираем автоматическое выключение по флагу воспроизведения, 
            // чтобы визуализатор не мигал при буферизации
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            Log.d("RadioManager", "Audio session ID changed: $audioSessionId")
            if (audioSessionId != 0) {
                if (_visualizerManager.value == null || _visualizerManager.value?.sessionId != audioSessionId) {
                    _visualizerManager.value?.release()
                    _visualizerManager.value = AudioVisualizerManager(audioSessionId)
                }
                // Всегда включаем при смене сессии
                _visualizerManager.value?.setEnabled(true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("RadioManager", "Player error: ${error.message}")
            _isPlaying.value = false
            // При ошибке можно оставить включенным или выключить, но не release
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                _isPlaying.value = false
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getExoPlayer(context: Context): ExoPlayer {
        if (player == null) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,  // minBufferMs
                    90_000, // maxBufferMs (увеличено для стабильности на плохом соединении)
                    5_000,   // bufferForPlaybackMs (увеличено для более надежного старта)
                    10_000   // bufferForPlaybackAfterRebufferMs
                )
                .setBackBuffer(30_000, true) // Обратный буфер для предотвращения пауз
                .build()

            player = ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl)
                .build().apply {
                    addListener(playerListener)
                }
        }
        return player!!
    }

    private fun getLocalStations(context: Context): List<RadioStation> {
        if (cachedLocalStations != null) return cachedLocalStations!!
        
        return try {
            val jsonString = context.assets.open("stations.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<RadioStation>>() {}.type
            val stations: List<RadioStation> = Gson().fromJson(jsonString, listType)
            cachedLocalStations = stations
            stations
        } catch (e: Exception) {
            Log.e("RadioManager", "Error loading stations: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchBestStations(context: Context): List<RadioStation> {
        return searchStations(context, "", null)
    }

    suspend fun fetchFavoriteStations(context: Context): List<RadioStation> {
        val favoriteUuids = com.deepnight.launcher.LauncherSettings.getFavoriteStations(context)
        if (favoriteUuids.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            getLocalStations(context).filter { favoriteUuids.contains(it.uuid) }
        }
    }

    suspend fun searchStations(context: Context, query: String = "", tag: String? = null): List<RadioStation> {
        return withContext(Dispatchers.IO) {
            val allStations = getLocalStations(context)

            allStations
                .filter { station ->
                    val matchesQuery = query.isEmpty() || station.name.contains(query, ignoreCase = true)
                    val matchesTag = tag == null || station.tags?.contains(tag, ignoreCase = true) == true
                    matchesQuery && matchesTag
                }
                .map { station ->
                    station.copy(
                        name = station.name.replace(Regex("[\t\r\n]"), " ").trim(),
                        codec = station.codec.lowercase()
                    )
                }
                .sortedWith(
                    compareByDescending<RadioStation> { it.bitrate }
                        .thenBy { station ->
                            when {
                                station.codec.contains("opus") -> 1
                                station.codec.contains("aac") -> 2
                                station.codec.contains("mp3") -> 3
                                else -> 4
                            }
                        }
                )
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playStation(context: Context, station: RadioStation) {
        val player = getExoPlayer(context)
        
        if (_currentStation.value?.url == station.url && _isPlaying.value) {
            return
        }

        _currentStation.value = station
        val mediaItem = MediaItem.fromUri(station.url)
        player.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }

        // Уведомляем систему о новой аудио-сессии для глобальной визуализации
        val sessionId = player.audioSessionId
        if (sessionId != 0) {
            if (_visualizerManager.value == null || _visualizerManager.value?.sessionId != sessionId) {
                _visualizerManager.value?.release()
                _visualizerManager.value = AudioVisualizerManager(sessionId)
            }
            _visualizerManager.value?.setEnabled(true)

            val sessionIntent = Intent("com.deepnight.launcher.AUDIO_SESSION_CHANGE").apply {
                putExtra("session_id", sessionId)
                setPackage(context.packageName)
            }
            context.sendBroadcast(sessionIntent)
        }

        // Запуск сервиса для фонового воспроизведения
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            Log.e("RadioManager", "Error starting PlaybackService: ${e.message}")
        }
    }

    fun stop(context: Context) {
        _visualizerManager.value?.setEnabled(false)
        _visualizerManager.value?.release()
        _visualizerManager.value = null

        player?.stop()
        player?.release()
        player = null
        _isPlaying.value = false
        _currentStation.value = null
        
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            Log.e("RadioManager", "Error stopping PlaybackService: ${e.message}")
        }
    }
}
