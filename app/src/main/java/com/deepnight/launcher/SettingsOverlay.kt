package com.deepnight.launcher

import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceDefaults.colors
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text

private val SettingsNightBlack = Color(0xFF0A0A0A)
private val SettingsNeonCyan = Color(0xFF00E5FF)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsOverlay(
    onDismiss: () -> Unit,
    onInteraction: () -> Unit = {},
    onSizeChanged: (Int) -> Unit,
    onRefreshWallpaper: () -> Unit,
    onPickFileWallpaper: () -> Unit,
    onPickVideoScreensaver: () -> Unit,
    onOpenAlarms: () -> Unit,
    onCheckUpdate: () -> Unit,
    onPreviewScreensaver: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val mainMenuFirstItem = remember { FocusRequester() }
    val wallpaperMenuFirstItem = remember { FocusRequester() }
    val screensaverMenuFirstItem = remember { FocusRequester() }
    val audioMenuFirstItem = remember { FocusRequester() }
    val presetsMenuFirstItem = remember { FocusRequester() }
    
    val wallpaperButtonReq = remember { FocusRequester() }
    val screensaverButtonReq = remember { FocusRequester() }
    val audioButtonReq = remember { FocusRequester() }
    val presetsButtonReq = remember { FocusRequester() }
    val equalizerButtonReq = remember { FocusRequester() }
    val equalizerFirstBandReq = remember { FocusRequester() }

    val isPreview = LocalInspectionMode.current

    var activeMenu by remember { mutableStateOf(MenuState.MAIN) }
    var previousMenu by remember { mutableStateOf<MenuState?>(null) }
    
    var isAudioEnabled by remember {
        mutableStateOf(if (isPreview) false else LauncherSettings.isAudioEffectEnabled(context))
    }

    var isScreensaverEnabled by remember {
        mutableStateOf(if (isPreview) true else LauncherSettings.isScreensaverEnabled(context))
    }

    var isNightMode by remember {
        mutableStateOf(if (isPreview) false else LauncherSettings.isAudioNightMode(context))
    }

    var isTestSoundPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    // Сбрасываем таймер заставки пока играет тестовый звук
    LaunchedEffect(isTestSoundPlaying) {
        if (isTestSoundPlaying) {
            while (isTestSoundPlaying) {
                onInteraction() // Сбрасываем таймер каждые 5 секунд
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    var currentPreset by remember {
        mutableStateOf(if (isPreview) "Movie" else LauncherSettings.getAudioPreset(context))
    }

    var currentIconSize by remember {
        mutableIntStateOf(if (isPreview) 80 else LauncherSettings.getIconSize(context))
    }

    var currentLoudness by remember {
        mutableFloatStateOf(if (isPreview) 0.0f else LauncherSettings.getAudioLoudness(context))
    }

    var currentGains by remember {
        mutableStateOf(if (isPreview) floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) else LauncherSettings.getAudioCustomGains(context))
    }

    var screensaverTimeout by remember {
        mutableLongStateOf(if (isPreview) 300_000L else LauncherSettings.getScreensaverTimeout(context))
    }

    var prefer4K by remember {
        mutableStateOf(if (isPreview) true else (LauncherSettings.isScreensaverPrefer4K(context) ?: true))
    }

    BackHandler {
        val oldMenu = activeMenu
        when (activeMenu) {
            MenuState.MAIN -> onDismiss()
            MenuState.EQUALIZER -> activeMenu = MenuState.PRESETS
            MenuState.PRESETS -> activeMenu = MenuState.AUDIO
            else -> activeMenu = MenuState.MAIN
        }
        if (oldMenu != activeMenu) {
            previousMenu = oldMenu
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .onPreviewKeyEvent {
                onInteraction() // Сбрасываем таймер заставки при любом нажатии
                false
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.TopEnd
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 28.dp, end = 28.dp, bottom = 28.dp)
                .width(360.dp)
                .focusRequester(focusRequester)
                .focusProperties { 
                    onExit = { FocusRequester.Cancel } 
                }
                .focusGroup(),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(containerColor = SettingsNightBlack.copy(alpha = 0.95f)),
            border = Border(BorderStroke(1.dp, Color.White.copy(0.08f)))
        ) {
            Column(modifier = Modifier.padding(vertical = 24.dp)) {

                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    val subtitle = when (activeMenu) {
                        MenuState.WALLPAPER -> "ВЫБОР ИСТОЧНИКА"
                        MenuState.SCREENSAVER -> "ПАРАМЕТРЫ ЗАСТАВКИ"
                        MenuState.AUDIO -> "УЛУЧШЕНИЕ ЗВУКА"
                        MenuState.PRESETS -> "ВЫБОР ПРЕСЕТА"
                        MenuState.EQUALIZER -> "ЭКВАЛАЙЗЕР (РУЧНОЙ)"
                        MenuState.MAIN -> "КОНФИГУРАЦИЯ"
                    }
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = SettingsNeonCyan.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "DEEP NIGHT",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Thin,
                        letterSpacing = 8.sp,
                        style = TextStyle(shadow = Shadow(SettingsNeonCyan.copy(0.5f), blurRadius = 20f))
                    )
                }

                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.05f)))
                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(Modifier.height(4.dp))
                    when (activeMenu) {
                        MenuState.WALLPAPER -> {
                            CompactSettingButton(
                                title = "ОБНОВИТЬ AI ФОН",
                                icon = Icons.Default.Refresh,
                                modifier = Modifier.focusRequester(wallpaperMenuFirstItem),
                                onClick = {
                                    onRefreshWallpaper()
                                    activeMenu = MenuState.MAIN
                                }
                            )

                            CompactSettingButton(
                                title = "ВЫБРАТЬ ИЗ ФАЙЛА",
                                icon = Icons.Default.Build,
                                onClick = {
                                    onPickFileWallpaper()
                                }
                            )

                            Spacer(Modifier.height(8.dp))

                            CompactSettingButton(
                                title = "НАЗАД",
                                icon = Icons.Default.Settings,
                                onClick = { activeMenu = MenuState.MAIN }
                            )
                        }

                        MenuState.SCREENSAVER -> {
                            CompactSettingButton(
                                title = "MATRIX (DEEP NIGHT)",
                                icon = Icons.Default.Monitor,
                                modifier = Modifier.focusRequester(screensaverMenuFirstItem),
                                isAccent = LauncherSettings.getScreensaverType(context) == "DEEP_NIGHT",
                                onClick = {
                                    LauncherSettings.setScreensaverEnabled(context, true)
                                    LauncherSettings.setScreensaverType(context, "DEEP_NIGHT")
                                    isScreensaverEnabled = true
                                    activeMenu = MenuState.MAIN
                                }
                            )

                            CompactSettingButton(
                                title = "AERIAL (ВИДЕО)",
                                icon = Icons.Default.Refresh,
                                isAccent = LauncherSettings.getScreensaverType(context) == "AERIAL",
                                onClick = {
                                    LauncherSettings.setScreensaverEnabled(context, true)
                                    LauncherSettings.setScreensaverType(context, "AERIAL")
                                    isScreensaverEnabled = true
                                    activeMenu = MenuState.MAIN
                                }
                            )

                            CompactSettingButton(
                                title = "ВЫБРАТЬ ВИДЕО ФАЙЛ",
                                icon = Icons.Default.Build,
                                onClick = {
                                    onPickVideoScreensaver()
                                    activeMenu = MenuState.MAIN
                                }
                            )

                            CompactSettingButton(
                                title = "ПРЕДПРОСМОТР",
                                icon = Icons.Default.Monitor,
                                onClick = {
                                    onPreviewScreensaver()
                                    onDismiss()
                                }
                            )

                            CompactSettingButton(
                                title = "ТАЙМ-АУТ: ${screensaverTimeout / 60_000} МИН",
                                icon = Icons.Default.Refresh,
                                onClick = {
                                    val nextTimeout = when (screensaverTimeout) {
                                        60_000L -> 180_000L
                                        180_000L -> 300_000L
                                        300_000L -> 600_000L
                                        600_000L -> 1_200_000L
                                        1_200_000L -> 1_800_000L
                                        else -> 60_000L
                                    }
                                    screensaverTimeout = nextTimeout
                                    LauncherSettings.setScreensaverTimeout(context, nextTimeout)
                                }
                            )

                            CompactSettingButton(
                                title = if (prefer4K) "КАЧЕСТВО: 4K (ULTRA HD)" else "КАЧЕСТВО: 1080P (FULL HD)",
                                icon = Icons.Default.Monitor,
                                isAccent = prefer4K,
                                onClick = {
                                    prefer4K = !prefer4K
                                    LauncherSettings.setScreensaverPrefer4K(context, prefer4K)
                                }
                            )

                            Spacer(Modifier.height(8.dp))

                            CompactSettingButton(
                                title = "ОТКЛЮЧИТЬ",
                                icon = Icons.Default.Settings,
                                isAccent = !isScreensaverEnabled,
                                onClick = {
                                    LauncherSettings.setScreensaverEnabled(context, false)
                                    isScreensaverEnabled = false
                                    activeMenu = MenuState.MAIN
                                }
                            )

                            CompactSettingButton(
                                title = "НАЗАД",
                                icon = Icons.Default.Settings,
                                onClick = { activeMenu = MenuState.MAIN }
                            )
                        }

                        MenuState.AUDIO -> {
                            CompactSettingButton(
                                title = if (isAudioEnabled) "DSP: ВКЛЮЧЕН" else "DSP: ВЫКЛЮЧЕН",
                                icon = Icons.Default.GraphicEq,
                                modifier = Modifier.focusRequester(audioMenuFirstItem),
                                isAccent = isAudioEnabled,
                                onClick = {
                                    onInteraction() // Сбрасываем таймер заставки
                                    isAudioEnabled = !isAudioEnabled
                                    LauncherSettings.setAudioEffectEnabled(context, isAudioEnabled)
                                    val intent = android.content.Intent("com.deepnight.launcher.dsp.UPDATE_SETTINGS")
                                    intent.putExtra("enabled", isAudioEnabled)
                                    context.sendBroadcast(intent)
                                }
                            )

                            CompactSettingButton(
                                title = if (isTestSoundPlaying) "ОСТАНОВИТЬ ТЕСТ" else "ЗАПУСТИТЬ ТЕСТ ЗВУКА",
                                icon = Icons.Default.MusicNote,
                                isAccent = isTestSoundPlaying,
                                onClick = {
                                    onInteraction() // Сбрасываем таймер заставки
                                    if (isTestSoundPlaying) {
                                        try {
                                            mediaPlayer.stop()
                                            mediaPlayer.reset()
                                        } catch (_: Exception) {}
                                        isTestSoundPlaying = false
                                    } else {
                                        try {
                                            mediaPlayer.reset()
                                            // Используем более динамичный трек для теста эквалайзера и басов
                                            mediaPlayer.setDataSource("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3")
                                            mediaPlayer.prepareAsync()
                                            mediaPlayer.setOnPreparedListener { 
                                                it.start()
                                                it.isLooping = true
                                                
                                                // Принудительно уведомляем DSP о новой сессии
                                                val intent = android.content.Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                                                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, it.audioSessionId)
                                                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                                context.sendBroadcast(intent)
                                            }
                                            mediaPlayer.setOnErrorListener { _, _, _ ->
                                                isTestSoundPlaying = false
                                                Toast.makeText(context, "Ошибка сети или файла", Toast.LENGTH_SHORT).show()
                                                true
                                            }
                                            isTestSoundPlaying = true
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Ошибка запуска", Toast.LENGTH_SHORT).show()
                                            isTestSoundPlaying = false
                                        }
                                    }
                                }
                            )

                            CompactSettingButton(
                                title = "НОЧНОЙ РЕЖИМ: ${if (isNightMode) "ВКЛ" else "ВЫКЛ"}",
                                icon = Icons.Default.Nightlight,
                                isAccent = isNightMode,
                                onClick = {
                                    onInteraction() // Сбрасываем таймер заставки
                                    isNightMode = !isNightMode
                                    if (!isPreview) {
                                        LauncherSettings.setAudioNightMode(context, isNightMode)
                                        val intent = android.content.Intent("com.deepnight.launcher.dsp.UPDATE_SETTINGS")
                                        intent.putExtra("night_mode", isNightMode)
                                        context.sendBroadcast(intent)
                                    }
                                }
                            )

                            CompactSettingButton(
                                title = "ВЫБОР ПРЕСЕТА: $currentPreset",
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                modifier = Modifier.focusRequester(presetsButtonReq),
                                onClick = { 
                                    onInteraction() // Сбрасываем таймер заставки
                                    previousMenu = activeMenu
                                    activeMenu = MenuState.PRESETS 
                                }
                            )

                            CompactSettingButton(
                                title = "LOUDNESS: ${(currentLoudness * 100).toInt()}%",
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                onClick = {
                                    onInteraction() // Сбрасываем таймер заставки
                                    val nextLoudness = if (currentLoudness >= 1.0f) 0.0f else currentLoudness + 0.2f
                                    currentLoudness = nextLoudness
                                    if (!isPreview) {
                                        LauncherSettings.setAudioLoudness(context, nextLoudness)
                                        val intent = android.content.Intent("com.deepnight.launcher.dsp.UPDATE_SETTINGS")
                                        intent.putExtra("loudness", nextLoudness)
                                        context.sendBroadcast(intent)
                                    }
                                }
                            )

                            CompactSettingButton(
                                title = "НАЗАД",
                                icon = Icons.Default.Settings,
                                onClick = { 
                                    onInteraction() // Сбрасываем таймер заставки
                                    activeMenu = MenuState.MAIN 
                                }
                            )
                        }

                        MenuState.PRESETS -> {
                            val presets = listOf("Movie", "Music", "Voice")
                            presets.forEachIndexed { index, preset ->
                                CompactSettingButton(
                                    title = preset.uppercase(),
                                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                                    modifier = if (index == 0) Modifier.focusRequester(presetsMenuFirstItem) else Modifier,
                                    isAccent = currentPreset == preset,
                                    onClick = {
                                        currentPreset = preset
                                        if (!isPreview) {
                                            LauncherSettings.setAudioPreset(context, preset)
                                            val intent = android.content.Intent("com.deepnight.launcher.dsp.UPDATE_SETTINGS")
                                            intent.putExtra("preset", preset)
                                            context.sendBroadcast(intent)
                                        }
                                        activeMenu = MenuState.AUDIO
                                    }
                                )
                            }
                            
                            CompactSettingButton(
                                title = "РУЧНАЯ НАСТРОЙКА (EQ)",
                                icon = Icons.Default.GraphicEq,
                                modifier = Modifier.focusRequester(equalizerButtonReq),
                                isAccent = currentPreset == "Custom",
                                onClick = {
                                    currentPreset = "Custom"
                                    if (!isPreview) {
                                        LauncherSettings.setAudioPreset(context, "Custom")
                                        val intent = android.content.Intent("com.deepnight.launcher.dsp.UPDATE_SETTINGS")
                                        intent.putExtra("preset", "Custom")
                                        intent.putExtra("custom_gains", currentGains)
                                        context.sendBroadcast(intent)
                                    }
                                    previousMenu = activeMenu
                                    activeMenu = MenuState.EQUALIZER
                                }
                            )
                            
                            Spacer(Modifier.height(8.dp))

                            CompactSettingButton(
                                title = "НАЗАД",
                                icon = Icons.Default.Settings,
                                onClick = { activeMenu = MenuState.AUDIO }
                            )
                        }

                        MenuState.EQUALIZER -> {
                            EqualizerSettings(
                                gains = currentGains,
                                firstBandRequester = equalizerFirstBandReq,
                                onGainsChanged = { newGains ->
                                    currentGains = newGains
                                    LauncherSettings.setAudioCustomGains(context, newGains)
                                    val intent = android.content.Intent("com.deepnight.launcher.dsp.UPDATE_SETTINGS")
                                    intent.putExtra("custom_gains", newGains)
                                    context.sendBroadcast(intent)
                                },
                                onBack = { 
                                    onInteraction() // Сбрасываем таймер заставки
                                    previousMenu = activeMenu
                                    activeMenu = MenuState.PRESETS 
                                },
                                onInteraction = onInteraction
                            )
                        }

                        MenuState.MAIN -> {
                            CompactSettingButton(
                                title = "ИЗМЕНИТЬ ФОНОВЫЙ РИСУНОК",
                                icon = Icons.Default.Refresh,
                                modifier = Modifier.focusRequester(wallpaperButtonReq).then(
                                    if (activeMenu == MenuState.MAIN && previousMenu == null) 
                                        Modifier.focusRequester(mainMenuFirstItem) 
                                    else Modifier
                                ),
                                onClick = { 
                                    previousMenu = activeMenu
                                    activeMenu = MenuState.WALLPAPER 
                                }
                            )

                            CompactSettingButton(
                                title = if (isScreensaverEnabled) {
                                    val type = LauncherSettings.getScreensaverType(context)
                                    if (type == "AERIAL") "ЗАСТАВКА (AERIAL)" else "ЗАСТАВКА (MATRIX)"
                                } else "ЗАСТАВКА (ВЫКЛ)",
                                icon = Icons.Default.Monitor,
                                modifier = Modifier.focusRequester(screensaverButtonReq),
                                isAccent = isScreensaverEnabled,
                                onClick = { 
                                    previousMenu = activeMenu
                                    activeMenu = MenuState.SCREENSAVER 
                                }
                            )

                            CompactSettingButton(
                                title = "УЛУЧШЕНИЕ ЗВУКА (DSP)",
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                modifier = Modifier.focusRequester(audioButtonReq),
                                isAccent = isAudioEnabled,
                                onClick = { 
                                    previousMenu = activeMenu
                                    activeMenu = MenuState.AUDIO 
                                }
                            )

                            CompactSettingButton(
                                title = "Размер иконок: ${currentIconSize}DP",
                                icon = Icons.Default.Build,
                                onClick = {
                                    val nextSize = if (currentIconSize >= 110) 40 else currentIconSize + 5
                                    currentIconSize = nextSize
                                    if (!isPreview) {
                                        LauncherSettings.saveIconSize(context, nextSize)
                                        onSizeChanged(nextSize)
                                    }
                                }
                            )

                            CompactSettingButton(
                                title = "DEEP NIGHT (ALARM)",
                                icon = Icons.Default.Bedtime,
                                onClick = onOpenAlarms
                            )

                            CompactSettingButton(
                                title = "ПРОВЕРИТЬ ОБНОВЛЕНИЯ",
                                icon = Icons.Default.Refresh,
                                onClick = onCheckUpdate
                            )

                            CompactSettingButton(
                                title = "СИСТЕМА ANDROID",
                                icon = Icons.Default.Settings,
                                onClick = {
                                    if (!isPreview) NavigationUtils.openSettings(context)
                                    onDismiss()
                                }
                            )

                            Spacer(Modifier.height(8.dp))

                            CompactSettingButton(
                                title = "НАЗАД",
                                icon = Icons.Default.Settings,
                                onClick = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(activeMenu) {
        // 1. Срочно перехватываем фокус на контейнер
        try { focusRequester.requestFocus() } catch (_: Exception) {}

        // 2. Интенсивный цикл поиска целевого элемента при входе в меню
        val delays = listOf(16L, 32L, 64L, 100L, 200L)
        delays.forEach { d ->
            kotlinx.coroutines.delay(d)
            try {
                when (activeMenu) {
                    MenuState.MAIN -> {
                        when (previousMenu) {
                            MenuState.WALLPAPER -> wallpaperButtonReq.requestFocus()
                            MenuState.SCREENSAVER -> screensaverButtonReq.requestFocus()
                            MenuState.AUDIO -> audioButtonReq.requestFocus()
                            else -> mainMenuFirstItem.requestFocus()
                        }
                    }
                    MenuState.WALLPAPER -> wallpaperMenuFirstItem.requestFocus()
                    MenuState.SCREENSAVER -> screensaverMenuFirstItem.requestFocus()
                    MenuState.AUDIO -> {
                        if (previousMenu == MenuState.PRESETS) {
                            presetsButtonReq.requestFocus()
                        } else {
                            audioMenuFirstItem.requestFocus()
                        }
                    }
                    MenuState.PRESETS -> {
                        if (previousMenu == MenuState.EQUALIZER) {
                            equalizerButtonReq.requestFocus()
                        } else {
                            presetsMenuFirstItem.requestFocus()
                        }
                    }
                    MenuState.EQUALIZER -> equalizerFirstBandReq.requestFocus()
                }
            } catch (_: Exception) {}
        }
    }

    // НОВЫЙ ЭФФЕКТ: Предотвращение потери фокуса при бездействии
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000) // Каждые 30 секунд проверяем
            try {
                // Если меню открыто, принудительно возвращаем фокус на текущее меню
                // Это предотвращает "улетание" фокуса на главный экран лаунчера
                when (activeMenu) {
                    MenuState.MAIN -> mainMenuFirstItem.requestFocus()
                    MenuState.AUDIO -> audioMenuFirstItem.requestFocus()
                    MenuState.PRESETS -> presetsMenuFirstItem.requestFocus()
                    MenuState.EQUALIZER -> equalizerFirstBandReq.requestFocus()
                    else -> focusRequester.requestFocus()
                }
            } catch (_: Exception) {
                // Игнорируем ошибки, если элемент временно недоступен
            }
        }
    }
}

enum class MenuState {
    MAIN, WALLPAPER, SCREENSAVER, AUDIO, PRESETS, EQUALIZER
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EqualizerSettings(
    gains: FloatArray,
    firstBandRequester: FocusRequester,
    onGainsChanged: (FloatArray) -> Unit,
    onBack: () -> Unit,
    onInteraction: () -> Unit = {}
) {
    val frequencies = listOf("62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k", "24k", "32k")
    var selectedBand by remember { mutableIntStateOf(0) }
    val focusRequesters = remember { List(frequencies.size) { if (it == 0) firstBandRequester else FocusRequester() } }
    val doneFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            frequencies.forEachIndexed { index, freq ->
                val gain = if (index < gains.size) gains[index] else 0f
                val isSelected = selectedBand == index
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(32.dp)
                        .onFocusChanged { if (it.isFocused) selectedBand = index }
                        .focusRequester(focusRequesters[index])
                        .focusProperties {
                            onExit = { FocusRequester.Default }
                        }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown) {
                                when (it.key) {
                                    Key.DirectionUp -> {
                                        onInteraction() // Сбрасываем таймер заставки
                                        if (gain < 12f) {
                                            val newGains = gains.copyOf()
                                            if (newGains.size < frequencies.size) {
                                                // Expand array if necessary to hit 11 bands
                                                val expanded = FloatArray(frequencies.size) { i -> 
                                                    if (i < newGains.size) newGains[i] else 0f 
                                                }
                                                expanded[index] = gain + 1f
                                                onGainsChanged(expanded)
                                            } else {
                                                newGains[index] += 1f
                                                onGainsChanged(newGains)
                                            }
                                        }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        onInteraction() // Сбрасываем таймер заставки
                                        if (gain > -12f) {
                                            val newGains = gains.copyOf()
                                            if (newGains.size < frequencies.size) {
                                                val expanded = FloatArray(frequencies.size) { i -> 
                                                    if (i < newGains.size) newGains[i] else 0f 
                                                }
                                                expanded[index] = gain - 1f
                                                onGainsChanged(expanded)
                                            } else {
                                                newGains[index] -= 1f
                                                onGainsChanged(newGains)
                                            }
                                        }
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter -> {
                                        doneFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .clickable { }
                ) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(120.dp)
                            .background(Color.White.copy(0.05f), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val heightFactor = (gain + 12f) / 24f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFactor.coerceIn(0.05f, 1f))
                                .background(if (isSelected) SettingsNeonCyan else Color.White.copy(0.2f), RoundedCornerShape(3.dp))
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = "${gain.toInt()}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) SettingsNeonCyan else Color.White
                    )
                    
                    Text(
                        text = freq,
                        fontSize = 9.sp,
                        color = if (isSelected) SettingsNeonCyan.copy(0.6f) else Color.White.copy(0.4f)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        CompactSettingButton(
            title = "ГОТОВО",
            icon = Icons.Default.Settings,
            modifier = Modifier.focusRequester(doneFocusRequester),
            onClick = onBack
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CompactSettingButton(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { 
                if (it.type == KeyEventType.KeyDown) {
                    when(it.key) {
                        Key.DirectionLeft, Key.DirectionRight -> true
                        else -> false
                    }
                } else false
            },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = colors(
            containerColor = if (isAccent) SettingsNeonCyan.copy(0.1f) else Color.Black.copy(0.4f),
            focusedContainerColor = if (isAccent) SettingsNeonCyan else Color.White.copy(0.15f),
            contentColor = if (isAccent) SettingsNeonCyan.copy(0.6f) else Color.White.copy(0.5f),
            focusedContentColor = if (isAccent) Color.Black else Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = SettingsNeonCyan.copy(alpha = 0.4f),
                elevation = if (isFocused) 20.dp else 0.dp
            )
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                BorderStroke(
                    2.dp,
                    if (isAccent) Color.White else SettingsNeonCyan
                )
            ),
            border = Border(BorderStroke(1.dp, Color.White.copy(0.05f)))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isFocused) {
                    if (isAccent) Color.Black else Color.White
                } else {
                    if (isAccent) SettingsNeonCyan.copy(0.7f) else Color.White.copy(0.5f)
                }
            )

            Spacer(Modifier.width(16.dp))

            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 1.5.sp,
                color = if (isFocused) {
                    if (isAccent) Color.Black else Color.White
                } else {
                    Color.White.copy(alpha = 0.6f)
                }
            )
        }
    }
}
