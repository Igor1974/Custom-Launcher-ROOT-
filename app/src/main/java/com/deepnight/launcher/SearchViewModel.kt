package com.deepnight.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepnight.launcher.model.TorrentResult
import com.deepnight.launcher.parser.JackettParser
import com.deepnight.launcher.parser.MegaPeerParser
import com.deepnight.launcher.parser.NUMParser
import com.deepnight.launcher.parser.RutorParser
import com.deepnight.launcher.parser.TPBParser
import com.deepnight.launcher.parser.TorrServeClient
import com.deepnight.launcher.parser.TorrsParser
import com.deepnight.launcher.radio.RadioManager
import com.deepnight.launcher.toSearchResult
import kotlinx.coroutines.*

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    
    var query by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var globalResults by mutableStateOf<List<SearchResult>>(emptyList())
    
    var showTorrServeInstallDialog by mutableStateOf(false)
    var torrServeDownloadLink by mutableStateOf("https://github.com/YouROK/TorrServe/releases")
    
    private val aggregator = GlobalSearchAggregator(context)
    
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening by mutableStateOf(false)
    var voiceLevel by mutableStateOf(0f)
    var shouldStartVoiceImmediately by mutableStateOf(false)

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.deepnight.launcher.RESTART_VOICE_SEARCH") {
                startVoiceSearch { }
            }
        }
    }

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(voiceReceiver, IntentFilter("com.deepnight.launcher.RESTART_VOICE_SEARCH"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(voiceReceiver, IntentFilter("com.deepnight.launcher.RESTART_VOICE_SEARCH"))
        }
    }

    fun startVoiceSearch(onResultsFound: () -> Unit) {
        // Принудительно останавливаем визуализатор, чтобы освободить аудио-ресурсы
        context.sendBroadcast(Intent("com.deepnight.launcher.VIDEO_STARTED"))
        
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            null
        }

        if (recognizer == null) {
            Toast.makeText(context, "Голосовой сервис недоступен", Toast.LENGTH_SHORT).show()
            return
        }

        setupAndStartRecognizer(recognizer, onResultsFound)
    }

    private fun setupAndStartRecognizer(recognizer: SpeechRecognizer, onResultsFound: () -> Unit) {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SearchViewModel", "Error destroying old speechRecognizer: ${e.message}")
        }

        speechRecognizer = recognizer.apply {
            setRecognitionListener(object : RecognitionListener {
                // ... (остальной код listener без изменений)
                override fun onReadyForSpeech(params: Bundle?) { 
                    isListening = true 
                    voiceLevel = 0f
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    // Обновляем уровень только если изменение значительное, чтобы не спамить рекомпозицией
                    val newLevel = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
                    if (Math.abs(newLevel - voiceLevel) > 0.15f) {
                        voiceLevel = newLevel
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { 
                    isListening = false 
                    voiceLevel = 0f
                }
                override fun onError(error: Int) {
                    isListening = false
                    voiceLevel = 0f
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Не удалось распознать"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Микрофон занят"
                        SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
                        SpeechRecognizer.ERROR_NETWORK -> "Нет сети"
                        SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
                        else -> "Ошибка голоса: $error"
                    }
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        Log.e("SearchViewModel", "Speech error: $error")
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val result = matches[0]
                        
                        // Обработка спец-команд Deep Night OS
                        val cmd = result.lowercase()
                        when {
                            cmd.contains("настройки") || cmd.contains("settings") || cmd.contains("сеттингс") -> {
                                context.sendBroadcast(Intent("com.deepnight.launcher.OPEN_SETTINGS"))
                                return
                            }
                            cmd.contains("сон") || cmd.contains("выключи") || cmd.contains("спать") -> {
                                try {
                                    context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                                    com.topjohnwu.superuser.Shell.cmd("input keyevent 26").exec()
                                } catch (_: Exception) {}
                                return
                            }
                            cmd.contains("очисти") || cmd.contains("ускорь") || cmd.contains("память") -> {
                                viewModelScope.launch(Dispatchers.IO) {
                                    val msg = SystemInfoRepository.boostSystem(context)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                                    }
                                }
                                return
                            }
                            cmd.contains("перезагрузи") || cmd.contains("ребут") -> {
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        com.topjohnwu.superuser.Shell.cmd("reboot").exec()
                                    } catch (_: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Ошибка перезагрузки", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                return
                            }
                            cmd.contains("инфо") || cmd.contains("версия") || cmd.contains("о системе") -> {
                                context.sendBroadcast(Intent("com.deepnight.launcher.OPEN_ABOUT"))
                                return
                            }
                            cmd.contains("обои") || cmd.contains("смени фон") -> {
                                context.sendBroadcast(Intent("com.deepnight.launcher.REFRESH_WALLPAPER"))
                                return
                            }
                        }

                        query = getCleanQuery(result)
                        performSearch(query, onResultsFound)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        query = matches[0]
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("SearchViewModel", "Error starting listening: ${e.message}")
            isListening = false
            Toast.makeText(context, "Не удалось запустить микрофон", Toast.LENGTH_SHORT).show()
        }
    }

    fun getCleanQuery(raw: String): String {
        val stopWords = listOf("найди", "поиск", "поищи", "фильм", "мультфильм", "сериал", "покажи", "открой", "запусти")
        var clean = raw.trim()
        stopWords.forEach { word ->
            if (clean.lowercase().startsWith(word)) {
                clean = clean.substring(word.length).trim()
            }
        }
        return clean.ifEmpty { raw }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        try { context.unregisterReceiver(voiceReceiver) } catch (_: Exception) {}
    }

    fun performSearch(searchQuery: String, onResultsFound: () -> Unit) {
        if (searchQuery.trim().length < 2) return
        
        viewModelScope.launch {
            isSearching = true
            LauncherSettings.saveLastSearchQuery(context, searchQuery)

            val results = withContext(Dispatchers.IO) {
                aggregator.fetchAllResults(searchQuery.trim())
            }
            
            globalResults = results
            isSearching = false
            if (globalResults.isNotEmpty()) onResultsFound()
        }
    }

    fun launchResult(result: SearchResult) {
        viewModelScope.launch {
            try {
                val intentData = result.intentData ?: ""

                // 0. Если это радиостанция
                if (intentData.startsWith("radio:")) {
                    val uuid = intentData.substringAfter("radio:")
                    viewModelScope.launch(Dispatchers.IO) {
                        val station = RadioManager.searchStations(context, "").find { it.uuid == uuid }
                        station?.let {
                            withContext(Dispatchers.Main) {
                                RadioManager.playStation(context, it)
                            }
                        }
                    }
                    return@launch
                }
                
                // 1. Если это торрент (магнит или ссылка)
                if (intentData.startsWith("magnet:") || intentData.contains(".torrent") || 
                    (intentData.startsWith("http") && !result.packageName.equals("tmdb", ignoreCase = true))) {
                    
                    val displayTitle = if (result.sourceApp == "Кино") {
                        "${result.title} (${result.quality ?: "Auto"})"
                    } else {
                        result.title ?: ""
                    }
                    
                    startTorrServe(intentData, displayTitle, result.posterUrl)
                    return@launch
                }

                // 2. Если это карточка "Кино", но магнит не был найден в агрегаторе (хотя он там ищется)
                if (result.sourceApp == "Кино") {
                    Toast.makeText(context, "Лучшая раздача не найдена. Попробуйте ручной поиск.", Toast.LENGTH_SHORT).show()
                } else {
                    // 3. Обычный запуск приложения
                    handleStandardLaunch(result)
                }
            } catch (e: Exception) {
                Log.e("SearchDebug", "Ошибка запуска: ${e.message}")
            }
        }
    }

    private fun startTorrServe(magnet: String, title: String, poster: String?) {
        context.sendBroadcast(Intent("com.deepnight.launcher.VIDEO_STARTED"))
        if (!TorrServeManager.isInstalled(context)) {
            viewModelScope.launch {
                val release = TorrServeManager.getLatestRelease()
                torrServeDownloadLink = release.link
                showTorrServeInstallDialog = true
            }
            return
        }

        val packages = TorrServeManager.PACKAGES
        var installedPackage: String? = null
        for (pkg in packages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                installedPackage = pkg
                break
            } catch (_: Exception) {}
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnet)).apply {
                if (installedPackage != null) {
                    setPackage(installedPackage)
                }
                putExtra("title", title)
                poster?.let { putExtra("poster", it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d("SearchViewModel", "TorrServe started with: $magnet")
        } catch (e: Exception) {
            Log.e("SearchViewModel", "Failed to start TorrServe: ${e.message}")
            // Фолбэк: пробуем запустить без указания пакета
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnet)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Не удалось запустить плеер", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleStandardLaunch(result: SearchResult) {
        val dataUriString = result.intentData ?: ""
        val packageManager = context.packageManager
        
        try {
            if (dataUriString.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dataUriString)).apply {
                    if (!result.packageName.isNullOrEmpty()) setPackage(result.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (AppRepository.videoApps.contains(result.packageName)) {
                    context.sendBroadcast(Intent("com.deepnight.launcher.VIDEO_STARTED"))
                }
                context.startActivity(intent)
            } else {
                packageManager.getLaunchIntentForPackage(result.packageName ?: "")?.let {
                    if (AppRepository.videoApps.contains(result.packageName)) {
                        context.sendBroadcast(Intent("com.deepnight.launcher.VIDEO_STARTED"))
                    }
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
        } catch (_: Exception) {
            packageManager.getLaunchIntentForPackage(result.packageName ?: "")?.let {
                if (AppRepository.videoApps.contains(result.packageName)) {
                    context.sendBroadcast(Intent("com.deepnight.launcher.VIDEO_STARTED"))
                }
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        }
    }
}
