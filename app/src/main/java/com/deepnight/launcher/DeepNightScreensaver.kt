package com.deepnight.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.currentStateAsState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

// --- ДАННЫЕ ---
data class Spark(val angle: Float, val speed: Float, val size: Float, val color: Color)
data class PerspectiveStar(
    val cos: Float,
    val sin: Float,
    val initialDist: Float,
    val speed: Float
)
data class FlashingStar(val offset: Offset, val maxSize: Float, val duration: Int)

@OptIn(UnstableApi::class)
@Composable
fun AerialDreamScreensaver(
    modifier: Modifier = Modifier,
    stats: SystemStats?,
    fftData: FloatArray = FloatArray(0),
    prefer4K: Boolean = true,
    isLocalOnly: Boolean = false,
    isExitingExternal: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoList by remember { mutableStateOf<List<AerialVideoProvider.AerialVideo>>(emptyList()) }
    var currentVideoTitle by remember { mutableStateOf("") }
    var isDismissing by remember { mutableStateOf(false) }
    val realTime = remember { mutableStateOf("00:00") }
    val focusRequester = remember { FocusRequester() }
    
    // ... остальной код (см. LaunchedEffect ниже)

    val exitProgress by animateFloatAsState(
        targetValue = if (isDismissing) 1f else 0f,
        animationSpec = tween(2000, easing = EaseInQuart),
        label = "ExitPhysics"
    )

    LaunchedEffect(isExitingExternal) {
        if (isExitingExternal) {
            isDismissing = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "NeonPulse")
    val textPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "TextPulse"
    )
    val flickerAlpha by infiniteTransition.animateFloat(
        0.92f,
        1f,
        infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "Flicker"
    )

    // Настраиваем RenderersFactory для максимальной производительности
    val renderersFactory = remember {
        androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true) // Страховка для переключения на другие аппаратные пути
    }

    // Используем общий OkHttpClient для поддержки User-Agent и обхода SSL
    val dataSourceFactory = remember {
        OkHttpDataSource.Factory(AerialVideoProvider.client)
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true

            // ПОЛНОСТЬЮ отключаем аудио, чтобы не тратить ресурсы декодера
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)
                .build()

            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val failingUri = currentMediaItem?.localConfiguration?.uri?.toString()
                    android.util.Log.e("Screensaver", "Playback error at: $failingUri", error)
                    
                    scope.launch {
                        // Удаляем битую ссылку из списка
                        if (failingUri != null) {
                            videoList = videoList.filter { it.url != failingUri }
                        }
                        
                        delay(2000)
                        if (videoList.isNotEmpty() && !isDismissing) {
                            val nextVideo = videoList.random()
                            val nextUri = if (nextVideo.id == "local") {
                                android.net.Uri.fromFile(File(nextVideo.url))
                            } else {
                                android.net.Uri.parse(nextVideo.url)
                            }
                            setMediaItem(MediaItem.fromUri(nextUri))
                            prepare()
                        }
                    }
                }
                
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        val currentUrl = currentMediaItem?.localConfiguration?.uri?.toString()
                        currentVideoTitle = videoList.find { it.url == currentUrl }?.name ?: ""
                    } else if (state == Player.STATE_IDLE && !playWhenReady) {
                        // Если плеер остановился по ошибке, пробуем пнуть его
                        prepare()
                        play()
                    }
                }
            })
        }
    }

    LaunchedEffect(Unit) {
        // Загрузка видео теперь происходит только при фактическом старте заставки
        delay(500) // Даем UI отрисоваться перед тяжелой работой
        
        val localFile = File(context.getExternalFilesDir(null), "Aerial/screensaver_video.mp4")
        
        val videos = if (isLocalOnly && localFile.exists()) {
            listOf(AerialVideoProvider.AerialVideo("local", localFile.absolutePath, "Custom Video"))
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val result = AerialVideoProvider.fetchVideos(true)
                    android.util.Log.d("Screensaver", "Fetched ${result.size} videos")
                    result
                } catch (e: Exception) {
                    android.util.Log.e("Screensaver", "Failed to fetch videos", e)
                    emptyList()
                }
            }
        }
        
        if (videos.isNotEmpty() && !isDismissing) {
            videoList = videos
        }

        while (true) {
            realTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }

    // Отдельный эффект для инициализации плеера после загрузки списка
    LaunchedEffect(videoList) {
        if (videoList.isNotEmpty() && !isDismissing) {
            delay(200) // Еще небольшая пауза для плавности
            val firstVideo = videoList.random()
            
            val mediaItem = if (firstVideo.id == "local") {
                MediaItem.fromUri(android.net.Uri.fromFile(File(firstVideo.url)))
            } else {
                MediaItem.fromUri(firstVideo.url)
            }
            
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            android.util.Log.d("Screensaver", "Player prepared with: ${firstVideo.name}")
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Очистка плеера
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onKeyEvent { 
                if (it.type == KeyEventType.KeyDown) {
                    isDismissing = true
                }
                true 
            }
            .focusable()
            .clickable { isDismissing = true }
    ) {
        if (videoList.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    val view = android.view.LayoutInflater.from(ctx).inflate(R.layout.media3_player_texture, null) as PlayerView
                    view.player = exoPlayer
                    view
                },
                modifier = Modifier.fillMaxSize()
            )
            // Градиентная подложка для читаемости на светлых видео
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ЗАГРУЗКА ВИДЕО...", color = Color.White.copy(alpha = 0.5f))
            }
        }

        // --- ВИЗУАЛИЗАТОР ЗВУКА ---
        if (fftData.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.6f * (1f - exitProgress) }
            ) {
                val width = size.width
                val height = size.height
                val barWidth = width / fftData.size.toFloat()
                
                for (i in fftData.indices) {
                    val magnitude = fftData[i]
                    val barHeight = (magnitude / 1.5f) * (height * 0.4f)
                    
                    val color = androidx.compose.ui.graphics.lerp(
                        Color(0xFF00FFFF),
                        Color(0xFFFF00E5),
                        i.toFloat() / fftData.size
                    )

                    drawRect(
                        color = color.copy(alpha = 0.5f),
                        topLeft = Offset(i * barWidth, height - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth - 2f, barHeight)
                    )
                }
            }
        }

        // Центральный блок (Часы, Погода, Название, Автор)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .graphicsLayer { 
                    alpha = 1f - exitProgress
                    translationY = 300f * exitProgress
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Часы
            Text(
                text = realTime.value,
                fontSize = 110.sp,
                fontWeight = FontWeight.ExtraLight,
                color = Color(0xFF00E5FF),
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.9f),
                        blurRadius = 60f
                    )
                ),
                modifier = Modifier.graphicsLayer { alpha = flickerAlpha }
            )

            // Погода
            if (stats != null) {
                Column(
                    modifier = Modifier.graphicsLayer { alpha = flickerAlpha },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stats.weatherIcon,
                            fontSize = 40.sp,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    blurRadius = 20f
                                )
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stats.weatherTemp,
                            style = TextStyle(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraLight,
                                color = Color(0xFF00E5FF),
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.9f),
                                    blurRadius = 30f
                                )
                            )
                        )
                    }

                    Text(
                        text = stats.weatherDescription.uppercase(),
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 4.sp,
                            fontSize = 10.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                blurRadius = 15f
                            )
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // Название видео (Город)
            if (currentVideoTitle.isNotEmpty()) {
                Text(
                    text = currentVideoTitle.uppercase(),
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = Color(0xFF00E5FF),
                        letterSpacing = 15.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            blurRadius = 40f
                        )
                    ),
                    modifier = Modifier.graphicsLayer { alpha = textPulse }
                )
                Spacer(Modifier.height(16.dp))
            }

            // Название (DEEP NIGHT)
            Text(
                text = "DEEP NIGHT",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraLight,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 15.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.9f),
                        blurRadius = 40f
                    )
                )
            )

            Spacer(Modifier.height(8.dp))

            // Автор
            Text(
                text = "DEVELOPED BY: ~Игорь~ (4PDA)",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                    letterSpacing = 4.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        blurRadius = 15f
                    )
                )
            )
        }
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(1800)
            onDismiss()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DeepNightScreensaver(
    modifier: Modifier = Modifier,
    appName: String = "DEEP NIGHT",
    wallpaperUrl: String,
    stats: SystemStats?, // Статистика из MainActivity
    fftData: FloatArray = FloatArray(0),
    isExitingExternal: Boolean = false,
    onDismiss: () -> Unit
) {
    // --- [НАСТРОЙКИ] ---
    val starCount = 200
    val starSpeedBase = 15000
    val flashIntervalMin = 2000L
    val flashIntervalMax = 6000L
    val flashDuration = 2000
    val flashMaxSize = 5f
    val meteorIntervalMin = 8000L
    val meteorIntervalMax = 15000L
    val meteorSpeed = 400
    val meteorTailLength = 0.2f
    val matrixSpeed = 600
    val matrixStep = 300
    val clockFixDelay = 0.90f
    val clockVerticalShake = 25f
    val skyBreathSpeed = 5000

    // --- СОСТОЯНИЯ ---
    val characters = remember { appName.map { it.toString() } }
    var isAssembled by remember { mutableStateOf(false) }
    var hasExploded by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var showClock by remember { mutableStateOf(false) }
    val realTime = remember { mutableStateOf("00:00") }
    val focusRequester = remember { FocusRequester() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(isExitingExternal) {
        if (isExitingExternal) {
            isDismissing = true // Запускаем анимацию падения букв
        }
    }

    // --- АНИМАЦИИ ---
    val exitProgress by animateFloatAsState(
        targetValue = if (isDismissing) 1f else 0f,
        animationSpec = tween(2000, easing = EaseInQuart),
        label = "ExitPhysics"
    )
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(1800) // Даем буквам время разогнаться и упасть
            onDismiss()  // И только теперь закрываем окончательно
        }
    }

    val starTransition = rememberInfiniteTransition(label = "DeepSpace")
    val starFlow by starTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(starSpeedBase, easing = LinearEasing)),
        label = "StarFlow"
    )

    val skyBreathing by starTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(skyBreathSpeed, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "SkyBreathing"
    )

    val clockEntrance by animateFloatAsState(
        targetValue = if (showClock) 1f else 0f,
        animationSpec = tween(1500, easing = LinearOutSlowInEasing),
        label = "ClockEntrance"
    )

    // Анимация для блока погоды и инфо
    val infoEntrance by animateFloatAsState(
        targetValue = if (showClock) 1f else 0f,
        animationSpec = tween(2000, delayMillis = 500),
        label = "InfoEntrance"
    )

    val flickerAlpha by starTransition.animateFloat(
        0.8f,
        1f,
        infiniteRepeatable(tween(250, easing = LinearEasing), RepeatMode.Reverse),
        label = "Flicker"
    )
    val textPulse by starTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "TextPulse"
    )

    // --- РАСЧЕТ БАСА ДЛЯ ВИЗУАЛИЗАЦИИ ---
    val bassBoost = remember(fftData) {
        if (fftData.size > 8) {
            val bassSum = fftData.slice(0..7).sum()
            (bassSum / 8f) * 1.2f // Коэффициент усиления баса
        } else 0f
    }

    // --- ЛОГИКА ---
    LaunchedEffect(Unit) {
        delay(2000); isAssembled = true
        delay(13000); hasExploded = true
        delay(1500); showClock = true
        while (true) {
            realTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }

    var flashingStar by remember { mutableStateOf<FlashingStar?>(null) }
    val flashProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(flashIntervalMin, flashIntervalMax))
            flashingStar = FlashingStar(
                Offset(Random.nextFloat(), Random.nextFloat()),
                flashMaxSize,
                flashDuration
            )
            flashProgress.snapTo(0f)
            flashProgress.animateTo(1f, animationSpec = tween(flashDuration, easing = LinearEasing))
            flashingStar = null
        }
    }

    var meteorPoints by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    val meteorAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(meteorIntervalMin, meteorIntervalMax))
            val startY = Random.nextFloat() * 0.6f
            meteorPoints = Offset(-0.2f, startY) to Offset(1.2f, startY + Random.nextFloat() * 0.3f)
            meteorAnim.snapTo(0f)
            meteorAnim.animateTo(1f, animationSpec = tween(meteorSpeed, easing = LinearEasing))
            meteorPoints = null
        }
    }

    val stars = remember {
        List(starCount) {
            val angleRad = Math.toRadians(Random.nextFloat() * 360.0).toFloat()
            PerspectiveStar(
                cos = cos(angleRad),
                sin = sin(angleRad),
                initialDist = Random.nextFloat(),
                speed = 0.01f + Random.nextFloat() * 0.03f
            )
        }
    }
    val sparks = remember {
        List(120) {
            Spark(
                Random.nextFloat() * 360f,
                Random.nextFloat() * 10f + 2f,
                Random.nextFloat() * 2.5f + 1f,
                if (Random.nextBoolean()) Color(0xFF00E5FF) else Color.White
            )
        }
    }
    val explosionProgress = remember { Animatable(0f) }
    LaunchedEffect(hasExploded) {
        if (hasExploded) explosionProgress.animateTo(
            1f,
            animationSpec = tween(1500)
        )
    }

    // --- ИНТЕРФЕЙС ---
    if (lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .onKeyEvent { if (it.type == KeyEventType.KeyDown) isDismissing = true; true }
                .focusable()
        ) {
            // Невидимый слой для клика (выход)
            Box(Modifier.fillMaxSize().clickable { isDismissing = true })

            AsyncImage(
                model = wallpaperUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 0.2f * (1f - exitProgress) }.blur(30.dp)
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerPt = Offset(size.width / 2, size.height / 2)
                val maxDist = if (size.width > size.height) size.width else size.height

                stars.forEach { star ->
                    val currentDistMult = (star.initialDist + starFlow) % 1f

                    val x = centerPt.x + star.cos * (currentDistMult * maxDist)
                    val y = centerPt.y + star.sin * (currentDistMult * maxDist)

                    drawCircle(
                        Color.White.copy(alpha = currentDistMult * (1f - exitProgress) * skyBreathing),
                        1.2.dp.toPx() * currentDistMult,
                        Offset(x, y)
                    )
                }
                flashingStar?.let { star ->
                    val p = flashProgress.value
                    val alpha = if (p < 0.5f) p * 2f else (1f - p) * 2f
                    val currentSize = star.maxSize.dp.toPx() * alpha
                    drawCircle(
                        Color.White.copy(alpha = alpha * (1f - exitProgress)),
                        currentSize,
                        Offset(star.offset.x * size.width, star.offset.y * size.height)
                    )
                    drawCircle(
                        Color(0xFF00E5FF).copy(alpha = alpha * 0.4f * (1f - exitProgress)),
                        currentSize * 2.5f,
                        Offset(star.offset.x * size.width, star.offset.y * size.height)
                    )
                }
                meteorPoints?.let { points ->
                    val p = meteorAnim.value
                    val curX = points.first.x + (points.second.x - points.first.x) * p
                    val curY = points.first.y + (points.second.y - points.first.y) * p
                    val tailP = (p - meteorTailLength).coerceAtLeast(0f)
                    val tailX = points.first.x + (points.second.x - points.first.x) * tailP
                    val tailY = points.first.y + (points.second.y - points.first.y) * tailP
                    drawLine(
                        Color.White.copy(alpha = (1f - p) * (1f - exitProgress)),
                        Offset(tailX * size.width, tailY * size.height),
                        Offset(curX * size.width, curY * size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                if (hasExploded && explosionProgress.value < 1f) {
                    val p = explosionProgress.value
                    sparks.forEach { spark ->
                        val angleRad = Math.toRadians(spark.angle.toDouble()).toFloat()
                        val x = centerPt.x + (cos(angleRad) * spark.speed * p * 300f)
                        val y = centerPt.y + (sin(angleRad) * spark.speed * p * 300f)

                        drawCircle(
                            spark.color.copy(alpha = (1f - p) * (1f - exitProgress)),
                            spark.size.dp.toPx(),
                            Offset(x, y)
                        )
                    }
                }
            }

            // --- 1. ЧАСЫ, ПОГОДА, ЛОГОТИП, АВТОР (ЕДИНЫЙ БЛОК) ---
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .graphicsLayer {
                        translationY = 300f * exitProgress
                        alpha = 1f - exitProgress
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ЧАСЫ
                if (showClock) {
                    Row {
                        val matrixChars = "0123456789$#%&@*?₿"
                        realTime.value.forEachIndexed { i, char ->
                            val scroll by starTransition.animateFloat(
                                0f,
                                10f,
                                infiniteRepeatable(
                                    tween(
                                        matrixSpeed + i * matrixStep,
                                        easing = LinearEasing
                                    )
                                ),
                                label = "MatrixScroll"
                            )
                            val isFixed = clockEntrance > clockFixDelay
                            val displayChar =
                                if (isFixed) char.toString() else matrixChars[scroll.toInt() % matrixChars.length].toString()

                            Text(
                                text = displayChar,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 110.sp,
                                    fontWeight = FontWeight.ExtraLight,
                                    color = if (isFixed) Color(0xFF00E5FF) else Color(0xFF00FF41).copy(
                                        alpha = flickerAlpha
                                    ),
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.9f),
                                        blurRadius = 60f * clockEntrance
                                    )
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp).graphicsLayer {
                                    if (!isFixed) {
                                        translationY =
                                            (scroll % 1f) * clockVerticalShake - (clockVerticalShake / 2)
                                        alpha = 0.4f + (scroll % 1f) * 0.6f
                                    }
                                }
                            )
                        }
                    }
                }

                // ПОГОДА
                if (showClock && stats != null) {
                    Column(
                        modifier = Modifier
                            .graphicsLayer { alpha = infoEntrance },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stats.weatherIcon,
                                fontSize = 40.sp,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        blurRadius = 20f
                                    )
                                ),
                                modifier = Modifier.graphicsLayer { alpha = flickerAlpha }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stats.weatherTemp,
                                style = TextStyle(
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.ExtraLight,
                                    color = Color(0xFF00E5FF),
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.9f),
                                        blurRadius = 30f
                                    )
                                )
                            )
                        }

                        Text(
                            text = stats.weatherDescription.uppercase(),
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.6f),
                                letterSpacing = 4.sp,
                                fontSize = 10.sp,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    blurRadius = 15f
                                )
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))

                // ЛОГОТИП (DEEP NIGHT)
                Row {
                    characters.forEachIndexed { index, char ->
                        val assembleProgress by animateFloatAsState(
                            targetValue = if (isAssembled) 1f else 0f,
                            animationSpec = tween(
                                10000,
                                delayMillis = index * 200,
                                easing = LinearOutSlowInEasing
                            ),
                            label = "Assemble"
                        )
                        val randX = remember { Random.nextInt(-600, 600).toFloat() }
                        val randY = remember { Random.nextInt(-800, 800).toFloat() }
                        val randRotX = remember { Random.nextInt(-90, 90).toFloat() }
                        val randRotY = remember { Random.nextInt(-90, 90).toFloat() }
                        val randRotZ = remember { Random.nextInt(-45, 45).toFloat() }
                        val randFallRotation = remember { Random.nextInt(-120, 120).toFloat() }

                        Text(
                            text = char,
                            style = TextStyle(
                                fontWeight = FontWeight.ExtraLight,
                                color = Color(0xFF00E5FF),
                                fontSize = 32.sp,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.9f),
                                    blurRadius = (40f + 20f * textPulse)
                                )
                            ),
                            modifier = Modifier.graphicsLayer {
                                val arrivalY = randY * (1f - assembleProgress)
                                val arrivalX = randX * (1f - assembleProgress)

                                rotationX = randRotX * (1f - assembleProgress)
                                rotationY = randRotY * (1f - assembleProgress)
                                rotationZ = randRotZ * (1f - assembleProgress) + (randFallRotation * exitProgress)

                                cameraDistance = 16f * density

                                val fallScale = 1f - (exitProgress * 0.8f)
                                val assemblyScale = 0.3f + (0.7f * assembleProgress)
                                val beatScale = 1f + (bassBoost * 0.2f) // Пульсация от баса

                                scaleX = (0.3f + 0.7f * assemblyScale) * fallScale * beatScale
                                scaleY = (0.3f + 0.7f * assemblyScale) * fallScale * beatScale

                                translationX = arrivalX + (randX * 0.2f * exitProgress)
                                translationY = arrivalY

                                alpha = (0.1f + 0.9f * assembleProgress) * textPulse
                            }.padding(horizontal = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // АВТОР
                val authorAlpha by animateFloatAsState(
                    if (hasExploded && !isDismissing) 1f else 0f,
                    tween(2000),
                    label = "AuthorAlpha"
                )
                Text(
                    text = "DEVELOPED BY: ~Игорь~ (4PDA)",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                        letterSpacing = 4.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            blurRadius = 15f
                        )
                    ),
                    modifier = Modifier.graphicsLayer { alpha = authorAlpha }
                )
            }
        }
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(1800)
            onDismiss()
        }
    }
}
