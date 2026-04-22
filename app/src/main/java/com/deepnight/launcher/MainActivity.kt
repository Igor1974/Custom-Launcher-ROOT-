package com.deepnight.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_BACK
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Speed
import com.deepnight.launcher.radio.RadioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceDefaults.glow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.deepnight.launcher.visualizer.AudioVisualizerManager
import com.deepnight.launcher.parser.TorrentNetworkClient
import com.deepnight.launcher.ui.LauncherViewModel
import com.deepnight.launcher.ui.theme.CustomLauncherRootTheme
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


val LocalIsHighRes = staticCompositionLocalOf { false }

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private val launcherViewModel: LauncherViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private var visualizerManager: AudioVisualizerManager? = null
    private var isUiReady by mutableStateOf(false)
    private var shouldOpenSettingsTrigger by mutableStateOf(false)
    private var shouldOpenAboutTrigger by mutableStateOf(false)
    private var shouldRefreshWallpaperTrigger by mutableStateOf(false)
    private var shouldResetToHome by mutableStateOf(false)
    private var shouldStartVoiceSearchTrigger by mutableStateOf(false)
    private var pendingVoiceSearchQuery by mutableStateOf<String?>(null)
    private var forceDismissScreensaver by mutableIntStateOf(0)
    private var updateInfo by mutableStateOf<AppUpdateManager.UpdateInfo?>(null)
    private var packageReceiver: PackageReceiver? = null

    override fun onPause() {
        super.onPause()
        forceDismissScreensaver++
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        // Перехватываем кнопку поиска/микрофона
        if (action == KeyEvent.ACTION_DOWN && (
            keyCode == KeyEvent.KEYCODE_SEARCH ||
            keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
            keyCode == KeyEvent.KEYCODE_ASSIST ||
            keyCode == 174
        )) {
            Log.d("DeepNightKey", "Search/Voice key pressed, hijacking...")

            // Пытаемся закрыть системные окна, если они вылезли
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    Shell.cmd("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS").exec()
                } catch (_: Exception) {}
            }

            shouldStartVoiceSearchTrigger = true
            return true // Поглощаем событие
        }

        return super.dispatchKeyEvent(event)
    }


    override fun onDestroy() {
        super.onDestroy()
        packageReceiver?.let { unregisterReceiver(it) }
        visualizerManager?.release()
        RadioManager.stop(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        sendBroadcast(Intent("com.deepnight.launcher.ACTION_USER_ACTIVITY"))
        Log.d("DeepNightKey", "KeyDown: $keyCode")

        // Коды кнопок поиска: SEARCH, ASSIST, VOICE_ASSIST и специфичные для TCL (219, 119, 85)
        if (keyCode == KeyEvent.KEYCODE_SEARCH ||
                keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                keyCode == KeyEvent.KEYCODE_ASSIST || keyCode == 119 || keyCode == 85
        ) {

            // ПРИНУДИТЕЛЬНО закрываем системные диалоги (включая TCL Assistant)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    Shell.cmd("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS").exec()
                } catch (_: Exception) {}
            }

            shouldStartVoiceSearchTrigger = true
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        packageReceiver = PackageReceiver {
            // Список приложений в AppRepository уже обновлен в ресивере,
            // Compose перерисует UI автоматически, так как использует SnapshotStateList
            Log.d("MainActivity", "Apps list updated via PackageReceiver")
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }

        // Оптимизированный клиент для Coil: разумные таймауты вместо бесконечных, чтобы избежать зависания сети
        val posterHttpClient = okhttp3.OkHttpClient.Builder()
            .dns(TorrentNetworkClient.client.dns)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .hostnameVerifier(TorrentNetworkClient.client.hostnameVerifier)
            .proxySelector(TorrentNetworkClient.client.proxySelector)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()

        // Оптимизация Coil для ТВ: RGB_565 + кэширование + оптимизированный клиент
        val imageLoader = coil.ImageLoader.Builder(this)
            .okHttpClient(posterHttpClient)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .build()
        coil.Coil.setImageLoader(imageLoader)

        lifecycleScope.launch {
            // 1. Сразу проверяем Root и применяем базовые фиксы
            withContext(Dispatchers.IO) {
                try {
                    Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
                    if (Shell.getShell().isRoot) {
                        // Только легкие фиксы. Полная трансформация - только через диалог вручную
                        DeepNightOSManager.applyCriticalFixes(this@MainActivity)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Root init failed", e)
                }
            }

            repeatOnLifecycle(Lifecycle.State.CREATED) {
                delay(100)
                isUiReady = true

                // Мгновенно обновляем погоду при старте
                try { SystemInfoRepository.updateWeather(this@MainActivity) } catch (_: Exception) {}

                // Автозапуск выбранного приложения
                AppRepository.getAutostartApp(this@MainActivity)?.let { pkg ->
                    if (pkg.isNotEmpty() && pkg != packageName) {
                        try {
                            val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                                ?: packageManager.getLaunchIntentForPackage(pkg)
                            launchIntent?.let { startActivity(it) }
                        } catch (_: Exception) {}
                    }
                }

                val update = AppUpdateManager.checkForUpdates(this@MainActivity)
                if (update != null) {
                    updateInfo = update
                }
            }
        }

        val isHighResSystem = is4KSupported(this)

        setContent {
            CompositionLocalProvider(LocalIsHighRes provides isHighResSystem) {
                CustomLauncherRootTheme {
                    val wallpaperUrl by launcherViewModel.wallpaperUrl.collectAsStateWithLifecycle()

                    var selectedAppForMenu by remember { mutableStateOf<AppInfo?>(null) }
                    var movingApp by remember { mutableStateOf<AppInfo?>(null) }
                    var showSettings by remember { mutableStateOf(false) }
                    var showAlarmSettings by remember { mutableStateOf(false) }
                    var showSearch by remember { mutableStateOf(false) }
                    var showAboutDialog by remember { mutableStateOf(false) }
                    var showOsUpgrade by remember { mutableStateOf(false) }
                    var showRecents by remember { mutableStateOf(false) }

                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(shouldResetToHome) {
                        if (shouldResetToHome) {
                            selectedAppForMenu = null
                            movingApp = null
                            showSettings = false
                            showAlarmSettings = false
                            showSearch = false
                            showAboutDialog = false
                            shouldResetToHome = false
                        }
                    }


                    val videoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                        onResult = { uri ->
                            uri?.let { selectedUri ->
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val folder = File(context.getExternalFilesDir(null), "Aerial")
                                        if (!folder.exists()) folder.mkdirs()

                                        // Очищаем старые видео перед добавлением нового
                                        folder.listFiles()?.forEach { it.delete() }

                                        val file = File(folder, "screensaver_video.mp4")
                                        context.contentResolver.openInputStream(selectedUri)?.use { input ->
                                            file.outputStream().use { output -> input.copyTo(output) }
                                        }

                                        withContext(Dispatchers.Main) {
                                            LauncherSettings.setScreensaverEnabled(context, true)
                                            LauncherSettings.setScreensaverType(context, "AERIAL")
                                            Toast.makeText(context, "Видео заставки обновлено", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    )

                    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                        onResult = { uri ->
                            uri?.let { selectedUri ->
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val file = File(context.filesDir, "custom_wallpaper.jpg")
                                        context.contentResolver.openInputStream(selectedUri)?.use { input ->
                                            file.outputStream().use { output -> input.copyTo(output) }
                                        }
                                        withContext(Dispatchers.Main) {
                                            launcherViewModel.updateUrl(file.absolutePath)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    )

                    LaunchedEffect(shouldOpenSettingsTrigger) {
                        if (shouldOpenSettingsTrigger) {
                            showSettings = true
                            shouldOpenSettingsTrigger = false
                        }
                    }

                    LaunchedEffect(shouldStartVoiceSearchTrigger) {
                        if (shouldStartVoiceSearchTrigger) {
                            if (showSearch) {
                                sendBroadcast(Intent("com.deepnight.launcher.RESTART_VOICE_SEARCH"))
                            } else {
                                showSearch = true
                            }
                            shouldStartVoiceSearchTrigger = false
                        }
                    }

                    LaunchedEffect(shouldOpenAboutTrigger) {
                        if (shouldOpenAboutTrigger) {
                            showAboutDialog = true
                            shouldOpenAboutTrigger = false
                        }
                    }

                    var showRadio by remember { mutableStateOf(false) }
                    
                    // Глобальный визуализатор для радио
                    var radioVisualizerSession by remember { mutableIntStateOf(0) }
                    var radioVisualizerManager by remember { mutableStateOf<AudioVisualizerManager?>(null) }

                    LaunchedEffect(Unit) {
                        val filter = IntentFilter()
                        filter.addAction("com.deepnight.launcher.OPEN_RADIO")
                        filter.addAction("com.deepnight.launcher.AUDIO_SESSION_CHANGE")
                        
                        val receiver = object : android.content.BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                when (intent?.action) {
                                    "com.deepnight.launcher.OPEN_RADIO" -> showRadio = true
                                    "com.deepnight.launcher.AUDIO_SESSION_CHANGE" -> {
                                        val sid = intent.getIntExtra("session_id", 0)
                                        if (sid > 0 && sid != radioVisualizerSession) {
                                            radioVisualizerSession = sid
                                            radioVisualizerManager?.release()
                                            radioVisualizerManager = AudioVisualizerManager(sid)
                                        }
                                    }
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
                        } else {
                            registerReceiver(receiver, filter)
                        }
                    }

                    LaunchedEffect(shouldRefreshWallpaperTrigger) {
                        if (shouldRefreshWallpaperTrigger) {
                            launcherViewModel.refreshWallpaper()
                            shouldRefreshWallpaperTrigger = false
                        }
                    }

                    var showAppNames by remember { mutableStateOf(!AppRepository.areNamesHidden(context)) }
                    var iconSize by remember { mutableIntStateOf(LauncherSettings.getIconSize(context)) }

                    val contentAlpha = animateFloatAsState(
                        targetValue = if (isUiReady) 1f else 0f,
                        animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
                        label = "ui_fade_in"
                    )

                    var showScreensaver by remember { mutableStateOf(false) }
                    var isScreensaverClosing by remember { mutableStateOf(false) }
                    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

                    val lifecycleState by lifecycle.currentStateAsState()

                    // Сброс скринсейвера при возврате в приложение или внешнем событии
                    LaunchedEffect(lifecycleState, forceDismissScreensaver) {
                        if (showScreensaver) {
                            showScreensaver = false
                            isScreensaverClosing = false
                        }
                        lastInteractionTime = System.currentTimeMillis()
                    }

                    LaunchedEffect(lastInteractionTime, lifecycleState, showScreensaver) {
                        if (lifecycleState == Lifecycle.State.RESUMED && !showScreensaver) {
                            if (LauncherSettings.isScreensaverEnabled(context)) {
                                val idleDelay = LauncherSettings.getScreensaverTimeout(context)
                                delay(idleDelay)
                                showScreensaver = true
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent {
                                if (!showScreensaver) {
                                    lastInteractionTime = System.currentTimeMillis()
                                    sendBroadcast(Intent("com.deepnight.launcher.ACTION_USER_ACTIVITY"))
                                }
                                false
                            }
                    ) {
                        val isPlaying by RadioManager.isPlaying
                        val spectrum by (if (isPlaying) radioVisualizerManager?.spectrum else visualizerManager?.spectrum)
                            ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(FloatArray(64)) }

                        val bass by (if (isPlaying) radioVisualizerManager?.bassLevel else visualizerManager?.bassLevel)
                            ?.collectAsStateWithLifecycle() ?: remember { mutableFloatStateOf(0f) }

                        val mid by (if (isPlaying) radioVisualizerManager?.midLevel else visualizerManager?.midLevel)
                            ?.collectAsStateWithLifecycle() ?: remember { mutableFloatStateOf(0f) }

                        val high by (if (isPlaying) radioVisualizerManager?.highLevel else visualizerManager?.highLevel)
                            ?.collectAsStateWithLifecycle() ?: remember { mutableFloatStateOf(0f) }

                        AiWallpaperBackground(
                            currentUrl = wallpaperUrl,
                            bassBoost = bass,
                            midBoost = mid,
                            highBoost = high,
                            spectrum = spectrum
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = contentAlpha.value
                                    val uiScale = 0.9f + (0.1f * contentAlpha.value)
                                    scaleX = uiScale
                                    scaleY = uiScale
                                    translationY = (1f - contentAlpha.value) * 40f
                                },
                            shape = RectangleShape,
                            colors = SurfaceDefaults.colors(containerColor = Color.Transparent)
                        ) {
                            MainDashboard(
                                modifier = Modifier.graphicsLayer {
                                    alpha = if (selectedAppForMenu != null || showSettings || showAlarmSettings || showSearch || showAboutDialog || showRecents) 0.5f else 1f
                                },
                                onAppMenuChange = { selectedAppForMenu = it },
                                movingApp = movingApp,
                                onMovingAppChange = { movingApp = it },
                                iconSize = iconSize,
                                wallpaperUrl = wallpaperUrl,
                                showAppNames = showAppNames,
                                onOpenSettings = { showSettings = true },
                                showSearch = showSearch,
                                onShowSearchChange = { showSearch = it },
                                onShowAboutDialogChange = { showAboutDialog = it },
                                showRecents = showRecents,
                                onShowRecentsChange = { showRecents = it },
                                showRadio = showRadio,
                                onShowRadioChange = { showRadio = it },
                                isAnyOverlayOpen = selectedAppForMenu != null || showSettings || showAlarmSettings || showSearch || showAboutDialog || showRecents || showRadio,
                                showScreensaver = showScreensaver,
                                isScreensaverClosing = isScreensaverClosing,
                                onScreensaverDismiss = {
                                    showScreensaver = false
                                    isScreensaverClosing = false
                                },
                                onScreensaverRequestClose = {
                                    isScreensaverClosing = true
                                }
                            )
                        }

                        if (showSettings) {
                            BackHandler { showSettings = false }
                            SettingsOverlay(
                                onDismiss = { showSettings = false },
                                onInteraction = { lastInteractionTime = System.currentTimeMillis() },
                                onSizeChanged = { iconSize = it },
                                onOpenAlarms = {
                                    showSettings = false
                                    showAppNames = !AppRepository.areNamesHidden(context)
                                    showAlarmSettings = true
                                },
                                onCheckUpdate = {
                                    scope.launch {
                                        val update = AppUpdateManager.checkForUpdates(context)
                                        if (update != null) {
                                            updateInfo = update
                                        } else {
                                            Toast.makeText(context, "Обновлений не найдено", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onOpenOsUpgrade = { showOsUpgrade = true },
                                onRefreshWallpaper = {
                                    launcherViewModel.refreshWallpaper()
                                    showSettings = false
                                },
                                onPickFileWallpaper = {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                onPickVideoScreensaver = {
                                    videoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly
                                        )
                                    )
                                },
                                onPreviewScreensaver = {
                                    showScreensaver = true
                                }
                            )
                        }

                        if (showAlarmSettings) {
                            AlarmSettingsScreen(onClose = { showAlarmSettings = false })
                            BackHandler { showAlarmSettings = false }
                        }

                        if (selectedAppForMenu != null) {
                            BackHandler { selectedAppForMenu = null }
                            AppActionsDialog(
                                app = selectedAppForMenu!!,
                                onDismiss = { selectedAppForMenu = null },
                                onMoveManual = {
                                    movingApp = selectedAppForMenu
                                    selectedAppForMenu = null
                                },
                                onNamesVisibilityChanged = { isHidden -> showAppNames = !isHidden }
                            )
                        }

                        if (showSearch) {
                            BackHandler { showSearch = false }
                            SearchOverlay(
                                viewModel = searchViewModel,
                                initialQuery = pendingVoiceSearchQuery,
                                onDismiss = {
                                    showSearch = false
                                    pendingVoiceSearchQuery = null
                                }
                            )
                        }

                        if (showRecents) {
                            BackHandler { showRecents = false }
                            RecentAppsOverlay(onDismiss = { showRecents = false })
                        }

                        // Добавляем экран Радио
                        if (showRadio) {
                            BackHandler { showRadio = false }
                            com.deepnight.launcher.radio.RadioScreen(onClose = { showRadio = false })
                        }

                        if (showAboutDialog) {
                            BackHandler { showAboutDialog = false }
                            AboutDialog(
                                onDismiss = { showAboutDialog = false },
                                onCheckUpdate = {
                                    showAboutDialog = false
                                    scope.launch {
                                        val update = AppUpdateManager.checkForUpdates(context)
                                        if (update != null) {
                                            updateInfo = update
                                        } else {
                                            Toast.makeText(context, "Обновлений не найдено", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }

                        if (showOsUpgrade) {
                            BackHandler { showOsUpgrade = false }
                            OsUpgradeDialog(onDismiss = { showOsUpgrade = false })
                        }

                        updateInfo?.let { update ->
                            UpdateDialog(
                                update = update,
                                onDismiss = { updateInfo = null },
                                onDownload = { onProgress ->
                                    scope.launch {
                                        AppUpdateManager.downloadAndInstallUpdate(context, update, onProgress)
                                        updateInfo = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        handleIntents(intent)
    }

    override fun onResume() {
        super.onResume()
        isUiReady = true

        // Когда мы возвращаемся в лаунчер - видео (если было) точно остановлено
        sendBroadcast(Intent("com.deepnight.launcher.VIDEO_STOPPED"))


        lifecycleScope.launch {
            delay(3000)
            if (!android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                startActivity(intent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        // Мгновенная реакция на интент ассистента
        handleIntents(intent)
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onSearchRequested(): Boolean {
        shouldStartVoiceSearchTrigger = true
        return true
    }

    private fun handleIntents(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        Log.d("DeepNightIntent", "Received intent: action=$action, data=${intent.dataString}")

        // Логика перехвата поиска от всех возможных ассистентов
        val isSearchAction = action == Intent.ACTION_SEARCH ||
                action == "android.intent.action.VOICE_SEARCH_RESULTS" ||
                action == Intent.ACTION_ASSIST ||
                action == Intent.ACTION_VOICE_COMMAND ||
                action == "android.intent.action.VOICE_ASSIST" ||
                action == "com.tcl.assistant.VOICE_SEARCH" ||
                action == "com.tcl.voice.SEARCH" ||
                action == "com.deepnight.launcher.VOICE_SEARCH_TRIGGER" ||
                action == "com.google.android.gms.actions.SEARCH_ACTION" ||
                action == "android.search.action.GLOBAL_SEARCH"

        if (isSearchAction || intent.hasExtra("query") || intent.hasExtra("voice_search_query") || intent.hasExtra("key_word")) {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY)
                ?: intent.getStringExtra("query")
                ?: intent.getStringExtra("voice_search_query")
                ?: intent.getStringExtra("key_word")
                ?: intent.getStringExtra("android.intent.extra.TEXT")
                ?: intent.dataString

            Log.d("DeepNightIntent", "Extracted query: $query")

            // ПРИНУДИТЕЛЬНО закрываем системные диалоги (включая ассистента),
            // чтобы наш оверлей был виден. Используем Shell так как это надежнее на Android 12+
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    Shell.cmd("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS").exec()
                } catch (_: Exception) {}
            }

            // Сбрасываем текущее состояние поиска, чтобы он открылся заново
            shouldStartVoiceSearchTrigger = false
            pendingVoiceSearchQuery = query

            // Используем delay через lifecycleScope, чтобы Compose успел заметить сброс
            lifecycleScope.launch {
                delay(10)
                shouldStartVoiceSearchTrigger = true
            }
        }

        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.action == Intent.ACTION_MAIN) {
            shouldResetToHome = true
        }
        if (intent.getBooleanExtra("OPEN_SETTINGS", false) || intent.action == "com.deepnight.launcher.OPEN_SETTINGS") {
            shouldOpenSettingsTrigger = true
        }
        if (intent.action == "com.deepnight.launcher.OPEN_ABOUT") {
            shouldOpenAboutTrigger = true
        }
        if (intent.action == "com.deepnight.launcher.REFRESH_WALLPAPER") {
            shouldRefreshWallpaperTrigger = true
        }
    }

    fun is4KSupported(context: Context): Boolean {
        val displayManager = context.getSystemService(DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        return display.supportedModes.any { it.physicalWidth >= 3840 }
    }

    @Composable
    fun AiWallpaperBackground(
        currentUrl: String,
        targetOffset: Offset = Offset.Zero,
        bassBoost: Float = 0f,
        midBoost: Float = 0f,
        highBoost: Float = 0f,
        spectrum: FloatArray = FloatArray(0)
    ) {
        val context = LocalContext.current
        val isHighRes = LocalIsHighRes.current

        val animatedOffset by animateOffsetAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessLow
            ),
            label = "parallax"
        )

        val animatedBass by animateFloatAsState(
            targetValue = 1f + (bassBoost * 0.12f),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "bass_pulse"
        )

        val animatedMidOffset by animateFloatAsState(
            targetValue = midBoost * 15f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "mid_float"
        )

        val animatedGlowAlpha by animateFloatAsState(
            targetValue = 0.2f + (highBoost * 0.5f),
            animationSpec = tween(150),
            label = "high_glow"
        )

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (currentUrl.isNotEmpty()) {
                AsyncImage(
                    model = remember(currentUrl, isHighRes) {
                        ImageRequest.Builder(context)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .data(currentUrl)
                            .crossfade(false)
                            .size(if (isHighRes) 3840 else 1920, if (isHighRes) 2160 else 1080)
                            .allowHardware(true)
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = animatedOffset.x + animatedMidOffset
                            translationY = animatedOffset.y + (animatedMidOffset * 0.5f)
                            val dist = animatedOffset.getDistance()
                            val extraScale = dist / 2000f
                            val baseScale = 1.25f
                            scaleX = (baseScale + extraScale) * animatedBass
                            scaleY = (baseScale + extraScale) * animatedBass
                        }
                )
            }

            // Слой пульсирующего неонового свечения (реагирует на весь спектр Spectralizer)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = animatedGlowAlpha }
                    .drawBehind {
                        if (spectrum.size >= 64) {
                            // Оптимизация: используем 16 зон вместо 64 для фонового свечения
                            val zones = 16
                            val zoneWidth = size.width / zones
                            for (i in 0 until zones) {
                                // Берем максимум из 4 соседних полос для каждой зоны
                                var zoneMagnitude = 0f
                                for (j in 0 until 4) {
                                    zoneMagnitude = maxOf(zoneMagnitude, spectrum[i * 4 + j])
                                }

                                if (zoneMagnitude > 0.15f) {
                                    val x = i * zoneWidth + zoneWidth / 2
                                    val color = androidx.compose.ui.graphics.lerp(
                                        Color(0xFFFF00E5), // Magenta (НЧ)
                                        Color(0xFF00E5FF), // Cyan (ВЧ)
                                        i.toFloat() / zones
                                    )

                                    // Рисуем свечение зоны. Используем pre-calculated значения где возможно.
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(color.copy(alpha = 0.12f * zoneMagnitude), Color.Transparent),
                                            center = Offset(x, size.height),
                                            radius = zoneWidth * 8 * zoneMagnitude
                                        ),
                                        center = Offset(x, size.height),
                                        radius = zoneWidth * 8 * zoneMagnitude
                                    )
                                }
                            }
                        } else {
                            // Fallback на старое свечение
                            drawRect(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Cyan.copy(alpha = 0.1f * highBoost),
                                        0.7f to Color.Transparent,
                                        1.0f to Color.Magenta.copy(alpha = 0.05f * highBoost)
                                    ),
                                    center = center,
                                    radius = size.width
                                )
                            )
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -animatedOffset.x * 1.5f
                        translationY = -animatedOffset.y * 1.5f
                        scaleX = 1.3f
                        scaleY = 1.3f
                    }
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.4f to Color.Black.copy(alpha = 0.3f + (bassBoost * 0.1f)),
                                    1.0f to Color.Black.copy(alpha = 0.95f)
                                ),
                                center = center,
                                radius = size.width * 0.6f
                            )
                        )
                    }
            )
        }
    }

    private fun applySystemFixesWithRoot() {
        lifecycleScope.launch {
            DeepNightOSManager.applyCriticalFixes(this@MainActivity)
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    fun MainDashboard(
        modifier: Modifier = Modifier,
        onAppMenuChange: (AppInfo?) -> Unit,
        movingApp: AppInfo?,
        onMovingAppChange: (AppInfo?) -> Unit,
        iconSize: Int,
        wallpaperUrl: String,
        showAppNames: Boolean,
        onOpenSettings: () -> Unit,
        showSearch: Boolean,
        onShowSearchChange: (Boolean) -> Unit,
        onShowAboutDialogChange: (Boolean) -> Unit,
        showRecents: Boolean,
        onShowRecentsChange: (Boolean) -> Unit,
        showRadio: Boolean,
        onShowRadioChange: (Boolean) -> Unit,
        isAnyOverlayOpen: Boolean,
        showScreensaver: Boolean,
        isScreensaverClosing: Boolean,
        onScreensaverDismiss: () -> Unit,
        onScreensaverRequestClose: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()

        val apps = remember { AppRepository.allApps }
        var focusPosition by remember { mutableStateOf<Offset?>(null) }
        var stats by remember { mutableStateOf(SystemStats()) }
        var currentTime by remember { mutableStateOf("--:--") }
        var isGridVisible by remember { mutableStateOf(false) }

        val blurRadius by animateDpAsState(targetValue = if (showRecents) 15.dp else 0.dp)
        val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

        LaunchedEffect(Unit) {
            delay(1500)
            withContext(Dispatchers.IO) {
                AppRepository.loadApps(context, force = true)
            }
            isGridVisible = true

            // Объединенный цикл обновления времени и статистики
            launch {
                var counter = 0
                while (true) {
                    currentTime = timeFormatter.format(Date())

                    // Обновляем статы каждые 15 секунд (30 * 500ms)
                    if (counter % 30 == 0) {
                        val freshStats = withContext(Dispatchers.IO) {
                            SystemInfoRepository.fetchFullStats(context)
                        }
                        stats = freshStats
                    }

                    // Обновляем погоду каждые 30 минут
                    if (counter % 3600 == 0) {
                        withContext(Dispatchers.IO) {
                            try { SystemInfoRepository.updateWeather(context) } catch (_: Exception) {}
                        }
                    }

                    delay(500)
                    counter++
                }
            }
        }

        val parallaxEffect = remember(focusPosition) {
            focusPosition?.let { pos ->
                Offset(
                    x = ((1920f / 2 - pos.x) * 0.02f).coerceIn(-20f, 20f),
                    y = ((1080f / 2 - pos.y) * 0.02f).coerceIn(-10f, 10f)
                )
            } ?: Offset.Zero
        }

        val isAnyGlobalOverlayOpen = isAnyOverlayOpen || showSearch || showRecents

        Box(
            modifier = modifier
                .fillMaxSize()
                .focusGroup()
        ) {
            AiWallpaperBackground(currentUrl = wallpaperUrl, targetOffset = parallaxEffect)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.6f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.5f)
                        )
                    )
            )

            var backPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showRecents && blurRadius > 0.1.dp) Modifier.blur(blurRadius) else Modifier)
                    .onKeyEvent { keyEvent ->
                        if (showScreensaver) {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                onScreensaverRequestClose()
                            }
                            return@onKeyEvent true
                        }

                        val nativeEvent = keyEvent.nativeKeyEvent
                        if (nativeEvent.keyCode == KEYCODE_BACK) {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (backPressJob == null) {
                                    backPressJob = scope.launch {
                                        delay(600)
                                        onShowRecentsChange(true)
                                    }
                                }
                                return@onKeyEvent true
                            }

                            if (keyEvent.type == KeyEventType.KeyUp) {
                                backPressJob?.cancel()
                                backPressJob = null

                                if (showRecents) {
                                    onShowRecentsChange(false)
                                    return@onKeyEvent true
                                }
                                return@onKeyEvent false
                            }
                        }
                        false
                    }
            ) {
                PhoneStatusBar(
                    stats = stats,
                    time = currentTime,
                    showSearch = showSearch,
                    onUpdateStats = { stats = it },
                    onOpenSearch = { onShowSearchChange(true) },
                    onOpenRadio = { onShowRadioChange(true) },
                    onOpenSettings = onOpenSettings,
                    onOpenInfo = { onShowAboutDialogChange(true) },
                    isEnabled = !isAnyGlobalOverlayOpen
                )

                if (apps.isEmpty() || !isGridVisible) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        MainAppGrid(
                            apps = apps,
                            iconSize = iconSize,
                            showNames = showAppNames,
                            movingApp = movingApp,
                            onAppMenuChange = onAppMenuChange,
                            onMovingAppChange = onMovingAppChange,
                            onGenParticles = { offset, _ ->
                                val lastPos = focusPosition
                                if (offset != Offset.Zero) {
                                    val dx = offset.x - (lastPos?.x ?: 0f)
                                    val dy = offset.y - (lastPos?.y ?: 0f)
                                    if (dx * dx + dy * dy > 1600f) {
                                        focusPosition = offset
                                    }
                                }
                            },
                            isEnabled = !isAnyGlobalOverlayOpen
                        )
                    }
                }
            }

            if (showScreensaver && lifecycleState == Lifecycle.State.RESUMED) {
                val saverType = LauncherSettings.getScreensaverType(context)
                val prefer4K = true

                // "AERIAL" и "LOCAL" используют один и тот же движок (ExoPlayer)
                if (saverType == "AERIAL" || saverType == "LOCAL") {
                    AerialDreamScreensaver(
                        stats = stats,
                        prefer4K = prefer4K,
                        isLocalOnly = (saverType == "LOCAL"),
                        isExitingExternal = isScreensaverClosing,
                        onDismiss = onScreensaverDismiss
                    )
                } else {
                    // "SPACE" или любой другой тип — запускаем процедурные звезды
                    DeepNightScreensaver(
                        wallpaperUrl = wallpaperUrl,
                        stats = stats,
                        isExitingExternal = isScreensaverClosing,
                        onDismiss = onScreensaverDismiss
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    fun PhoneStatusBar(
        stats: SystemStats,
        time: String,
        showSearch: Boolean,
        onUpdateStats: (SystemStats) -> Unit,
        onOpenSearch: () -> Unit,
        onOpenRadio: () -> Unit,
        onOpenSettings: () -> Unit,
        onOpenInfo: () -> Unit,
        isEnabled: Boolean = true
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var isBoosting by remember { mutableStateOf(false) }
        val neonCyan = Color(0xFF00E5FF)

        val isPlaying by RadioManager.isPlaying

        val baseTextStyle = androidx.compose.ui.text.TextStyle(
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.5.sp
        )

        val neonTimeStyle = androidx.compose.ui.text.TextStyle(
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Thin,
            letterSpacing = 6.sp,
            shadow = Shadow(
                color = neonCyan.copy(alpha = 0.7f),
                blurRadius = 25f
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = time, style = neonTimeStyle)
                    Spacer(Modifier.width(32.dp))

                    Text(text = stats.weatherIcon, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stats.weatherTemp.uppercase(),
                        style = baseTextStyle.copy(fontWeight = FontWeight.Medium)
                    )

                    Box(
                        Modifier
                            .padding(horizontal = 20.dp)
                            .width(1.dp)
                            .height(16.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    if (stats.temp.isNotEmpty() && stats.temp != "N/A") {
                        Text(text = "TEMP", style = baseTextStyle.copy(color = neonCyan))
                        Spacer(Modifier.width(8.dp))
                        Text(text = stats.temp, style = baseTextStyle)
                        Spacer(Modifier.width(24.dp))
                    }

                    Text(text = "RAM", style = baseTextStyle.copy(color = neonCyan))
                    Spacer(Modifier.width(8.dp))
                    Text(text = stats.ram, style = baseTextStyle)
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        null,
                        tint = Color.White.copy(0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stats.volume, style = baseTextStyle)

                    Spacer(Modifier.width(16.dp))

                    Text(text = "OUT", style = baseTextStyle.copy(color = neonCyan))
                    Spacer(Modifier.width(6.dp))
                    Text(text = stats.audioOut.uppercase(), style = baseTextStyle)

                    Spacer(Modifier.width(20.dp))

                    Text(
                        text = stats.net.uppercase(),
                        style = baseTextStyle.copy(
                            color = neonCyan,
                            shadow = Shadow(neonCyan.copy(0.4f), blurRadius = 10f)
                        )
                    )

                    Spacer(Modifier.width(32.dp))

                    Row(
                        modifier = Modifier.focusGroup(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusIconButton(
                            icon = if (isBoosting) Icons.Rounded.Speed else Icons.Rounded.RocketLaunch,
                            isHighlight = isBoosting,
                            onClick = {
                                if (!isBoosting) {
                                    isBoosting = true
                                    scope.launch(Dispatchers.IO) {
                                        val msg = SystemInfoRepository.boostSystem(context)
                                        val fresh = SystemInfoRepository.fetchFullStats(context)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            onUpdateStats(fresh)
                                            isBoosting = false
                                        }
                                    }
                                }
                            },
                            isEnabled = isEnabled
                        )

                        StatusIconButton(
                            icon = Icons.Default.Search,
                            isHighlight = showSearch,
                            onClick = onOpenSearch,
                            isEnabled = isEnabled
                        )
                        StatusIconButton(icon = Icons.Default.Settings, onClick = onOpenSettings, isEnabled = isEnabled)
                        StatusIconButton(
                            icon = Icons.Rounded.Info,
                            onClick = onOpenInfo,
                            isEnabled = isEnabled)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    fun StatusIconButton(
        icon: ImageVector,
        isHighlight: Boolean = false,
        isEnabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val isHighRes = LocalIsHighRes.current
        val neonCyan = Color(0xFF00E5FF)
        var isFocused by remember { mutableStateOf(false) }

        val infiniteTransition = rememberInfiniteTransition(label = "boost")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isHighlight) 1.2f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )

        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = if (isHighlight) 0.8f else 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow"
        )

        Surface(
            onClick = onClick,
            enabled = isEnabled,
            modifier = Modifier
                .size(if (isHighRes) 56.dp else 36.dp)
                .onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(if (isHighRes) 12.dp else 8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isHighlight) neonCyan.copy(alpha = 0.1f) else Color.White.copy(
                    alpha = 0.05f
                ),
                focusedContainerColor = if (isHighlight) neonCyan else Color.White.copy(alpha = 0.2f),
                contentColor = if (isHighlight) neonCyan else Color.White.copy(alpha = 0.6f),
                focusedContentColor = if (isHighlight) Color.Black else Color.White,
                disabledContainerColor = Color.Transparent
            ),
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1.2f,
                scale = if (isHighlight && !isFocused) pulseScale else 1f
            ),
            glow = glow(
                focusedGlow = Glow(
                    elevationColor = neonCyan.copy(alpha = 0.5f),
                    elevation = if (isHighRes) 30.dp else 12.dp
                ),
                glow = if (isHighlight && !isFocused) Glow(
                    neonCyan.copy(alpha = pulseAlpha),
                    if (isHighRes) 20.dp else 8.dp
                ) else Glow.None
            )
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (isHighRes) 28.dp else 18.dp),
                    tint = if (isFocused) {
                        if (isHighlight) Color.Black else Color.White
                    } else {
                        if (isHighlight) neonCyan else Color.White.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }

    @SuppressLint("ConfigurationScreenWidthHeight")
    @OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun MainAppGrid(
        apps: List<AppInfo>,
        iconSize: Int,
        showNames: Boolean,
        movingApp: AppInfo?,
        onAppMenuChange: (AppInfo?) -> Unit,
        onMovingAppChange: (AppInfo?) -> Unit,
        onGenParticles: (Offset, Color) -> Unit,
        isEnabled: Boolean = true
    ) {
        val isHighRes = LocalIsHighRes.current
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current

        // Оптимизация spacing - уходим от анонимного объекта
        val (hSpacing, vSpacing, pSpacing) = remember(isHighRes) {
            if (isHighRes) Triple(28.dp, 32.dp, 56.dp)
            else Triple(16.dp, 20.dp, 32.dp)
        }

        val estimatedSpanCount = remember(configuration.screenWidthDp, iconSize) {
            ((configuration.screenWidthDp - (pSpacing.value.toInt() * 2)) /
                    (iconSize + hSpacing.value.toInt())).coerceAtLeast(1)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(estimatedSpanCount),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(pSpacing),
            horizontalArrangement = Arrangement.spacedBy(hSpacing, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(vSpacing),
            userScrollEnabled = isEnabled
        ) {
            itemsIndexed(
                items = apps,
                key = { _, app -> app.packageName }
            ) { _, app ->
                if (app.packageName == "com.deepnight.launcher.radio") {
                    com.deepnight.launcher.radio.RadioCard(
                        stationName = "Радио",
                        onClick = {
                            val intent = Intent("com.deepnight.launcher.OPEN_RADIO")
                            intent.setPackage(packageName)
                            sendBroadcast(intent)
                        },
                        modifier = Modifier.size(iconSize.dp)
                    )
                } else {
                    AppGridItem(
                        app = app,
                        iconSize = iconSize,
                        showNames = showNames,
                        isMoving = app.packageName == movingApp?.packageName,
                        spanCount = estimatedSpanCount,
                        onLongClick = { if (movingApp == null) onAppMenuChange(it) },
                        onExitMovingMode = { onMovingAppChange(null) },
                        onGenParticles = onGenParticles,
                        movingApp = movingApp,
                        isEnabled = isEnabled
                    )
                }
            }
        }
    }

    @Composable
    fun AppGridItem(
        app: AppInfo,
        iconSize: Int,
        showNames: Boolean,
        isMoving: Boolean,
        spanCount: Int,
        movingApp: AppInfo?,
        onLongClick: (AppInfo) -> Unit,
        onExitMovingMode: () -> Unit,
        onGenParticles: (Offset, Color) -> Unit,
        isEnabled: Boolean = true
    ) {
        val context = LocalContext.current
        var isFocused by remember { mutableStateOf(false) }

        val appColor = remember(app.packageName) {
            Color(app.packageName.hashCode() or 0xFF000000.toInt())
        }

        val scale by animateFloatAsState(
            targetValue = if (isFocused) 1.15f else 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "SpringFocus"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .padding(bottom = 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        if (isFocused) translationY = -5.dp.toPx()
                    }
            ) {
                AppIconItem(
                    app = app,
                    iconSize = iconSize,
                    spanCount = spanCount,
                    isMoving = isMoving,
                    onClick = {
                        if (movingApp == null) AppRepository.launchApp(context, app)
                        else { AppRepository.finalizeOrder(context); onExitMovingMode() }
                    },
                    onLongClick = { onLongClick(app) },
                    onEmitParticles = onGenParticles,
                    isEnabled = isEnabled
                )
            }

            if (showNames || isFocused) {
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = app.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                        shadow = if (isFocused) Shadow(
                            color = appColor.copy(alpha = 0.8f),
                            blurRadius = 15f
                        ) else null
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = if (isFocused) 1f else 0.6f
                            scaleX = scale * 0.9f
                            scaleY = scale * 0.9f
                        },
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    fun AppIconItem(
        app: AppInfo,
        iconSize: Int,
        isMoving: Boolean,
        spanCount: Int,
        isEnabled: Boolean = true,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        onEmitParticles: (Offset, Color) -> Unit = { _, _ -> }
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val focusRequester = remember { FocusRequester() }
        val isHighRes = LocalIsHighRes.current

        val appColor = remember(app.packageName) {
            Color(app.packageName.hashCode() or 0xFF000000.toInt())
        }

        val glowAlpha by animateFloatAsState(
            targetValue = if (isFocused) 1f else 0f,
            animationSpec = tween(500),
            label = "glowAlpha"
        )

        LaunchedEffect(isMoving, app.packageName) {
            if (isMoving) focusRequester.requestFocus()
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize.dp)
                .zIndex(if (isFocused) 10f else 1f)
        ) {
            if (glowAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .size(iconSize.dp * 6f)
                        .graphicsLayer { alpha = glowAlpha; clip = false }
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.0f to appColor.copy(alpha = 0.9f),
                                    0.2f to appColor.copy(alpha = 0.4f),
                                    1.0f to Color.Transparent,
                                    center = center, radius = size.width / 2
                                )
                            )
                        }
                )
            }

            Surface(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        if (!it.isFocused) onEmitParticles(Offset.Zero, Color.Transparent)
                    }
                    .onGloballyPositioned { coords ->
                        if (isFocused && !isMoving) {
                            val center = coords.localToWindow(
                                Offset(coords.size.width / 2f, coords.size.height / 2f)
                            )
                            if (center.x > 0 && center.y > 0) {
                                onEmitParticles(center, appColor)
                            }
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (isMoving && keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    AppRepository.moveAppStep(context, app.packageName, -1); true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    AppRepository.moveAppStep(context, app.packageName, 1); true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    AppRepository.moveAppStep(context, app.packageName, -spanCount); true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    AppRepository.moveAppStep(context, app.packageName, spanCount); true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KEYCODE_BACK -> {
                                    onClick(); true
                                }
                                else -> false
                            }
                        } else false
                    },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(if (isHighRes) 20.dp else 16.dp)),
                border = ClickableSurfaceDefaults.border(
                    border = if (isMoving) Border(
                        BorderStroke(
                            if (isHighRes) 5.dp else 3.dp,
                            Color.Cyan
                        )
                    ) else Border.None,
                    focusedBorder = Border.None
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    pressedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                glow = glow(focusedGlow = Glow.None),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(if (isHighRes) 20.dp else 16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(app.iconDrawable)
                            .size(if (isHighRes) 512 else 256)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (iconSize < (if (isHighRes) 100 else 70)) 10.dp else 14.dp)
                            .graphicsLayer {
                                alpha = if (isFocused) 1f else 0.7f
                            }
                    )
                }
            }
        }
    }
}
