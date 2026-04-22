package com.deepnight.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.center
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TorrServeInstallDialog(
    onDismiss: () -> Unit,
    downloadUrl: String
) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            androidx.compose.material3.Text(
                "TorrServe не установлен", 
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                color = Color.White
            ) 
        },
        text = { 
            androidx.compose.material3.Text(
                "Для просмотра торрентов необходимо установить приложение TorrServe. Предлагаем скачать актуальную версию с официального сервера.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            ) 
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    TorrServeManager.downloadAndInstall(context, downloadUrl)
                    Toast.makeText(context, "Начинаем загрузку TorrServe...", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier.padding(8.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                )
            ) {
                androidx.compose.material3.Text("Скачать и установить", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            androidx.compose.material3.OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.padding(8.dp),
                border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF00E5FF)
                )
            ) {
                androidx.compose.material3.Text("Отмена", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            }
        },
        containerColor = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ScanningAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "xOffset"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF00E5FF).copy(alpha = 0.4f),
                    Color.White,
                    Color(0xFF00E5FF).copy(alpha = 0.4f),
                    Color.Transparent
                ),
                startX = (xOffset * width) - (width * 0.2f),
                endX = (xOffset * width) + (width * 0.2f)
            ),
            size = size
        )
    }
}

@Composable
fun ListeningAnimation(level: Float, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "listen")
    
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val animatedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "voice_level"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = size.center
        val baseRadius = size.minDimension / 2.2f
        
        // Внешние пульсирующие круги (зависят от громкости)
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = (0.3f * animatedLevel).coerceIn(0.05f, 0.4f)),
            radius = baseRadius * (basePulse + animatedLevel * 0.5f),
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Внутренний энергетический круг (Красный при пиках)
        val energyColor = androidx.compose.ui.graphics.lerp(
            Color(0xFF00E5FF),
            Color(0xFFFF0055),
            animatedLevel
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(energyColor.copy(alpha = 0.6f), Color.Transparent),
                center = center,
                radius = baseRadius * (0.4f + animatedLevel * 0.6f)
            ),
            radius = baseRadius * (0.4f + animatedLevel * 0.6f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchOverlay(
    viewModel: SearchViewModel = viewModel(),
    initialQuery: String? = null,
    onInteraction: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val textFieldFocus = remember { FocusRequester() }
    val voiceButtonFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    val searchButtonFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Если передан начальный запрос (от ассистента), подставляем его и запускаем поиск
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && viewModel.query.isEmpty()) {
            val clean = viewModel.getCleanQuery(initialQuery)
            viewModel.query = clean
            viewModel.performSearch(clean) {
                scope.launch {
                    delay(400)
                    try { firstItemFocus.requestFocus() } catch(_:Exception) {}
                }
            }
        }
    }

    val customImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache_v2"))
                    .maxSizeBytes(1024 * 1024 * 250)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(1024 * 1024 * 40)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Сброс состояния при новом поиске
    LaunchedEffect(viewModel.isSearching, viewModel.globalResults.size) {
        // Если поиск завершен и результатов нет - возвращаем фокус в поле ввода
        if (!viewModel.isSearching && viewModel.globalResults.isEmpty() && !viewModel.isListening) {
            try {
                textFieldFocus.requestFocus()
            } catch(_:Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            delay(2000)
            val currentTime = System.currentTimeMillis()
            // Focus Guard: если прошло > 30 сек или фокус потерян (не в сетке и не на основных кнопках)
            if (currentTime - lastInteractionTime > 30000 && !viewModel.isListening && !viewModel.isSearching) {
                try {
                    voiceButtonFocus.requestFocus()
                    lastInteractionTime = currentTime
                    onInteraction()
                    Log.d("SearchOverlay", "Focus guard: returned to Voice Button")
                } catch (e: Exception) {
                    Log.e("SearchOverlay", "Focus guard error: ${e.message}")
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onInteraction()
            viewModel.startVoiceSearch {
                scope.launch {
                    delay(400)
                    try { firstItemFocus.requestFocus() } catch(_:Exception) {}
                }
            }
        }
    }

    LaunchedEffect(viewModel.shouldStartVoiceImmediately) {
        if (viewModel.shouldStartVoiceImmediately) {
            viewModel.shouldStartVoiceImmediately = false
            voiceButtonFocus.requestFocus()
            
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                viewModel.startVoiceSearch {
                    scope.launch {
                        delay(400)
                        try { firstItemFocus.requestFocus() } catch(_:Exception) {}
                    }
                }
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(300)
        voiceButtonFocus.requestFocus()
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1A1A), Color.Black),
                    radius = 1500f
                )
            )
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp)
                .focusGroup()
                .focusProperties { 
                    onExit = { FocusRequester.Cancel } 
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(0.85f).height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { 
                        lastInteractionTime = System.currentTimeMillis()
                        onInteraction()
                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startVoiceSearch {
                                scope.launch {
                                    delay(400)
                                    try { firstItemFocus.requestFocus() } catch(_:Exception) {}
                                }
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .focusRequester(voiceButtonFocus)
                        .onFocusChanged { 
                            if (it.isFocused) {
                                lastInteractionTime = System.currentTimeMillis()
                                onInteraction()
                            }
                        }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown) {
                                lastInteractionTime = System.currentTimeMillis()
                                onInteraction()
                                when(it.key) {
                                    Key.DirectionRight -> { textFieldFocus.requestFocus(); true }
                                    Key.DirectionDown -> {
                                        if (viewModel.globalResults.isNotEmpty()) {
                                            firstItemFocus.requestFocus()
                                        }
                                        true
                                    }
                                    Key.DirectionLeft, Key.DirectionUp -> true
                                    else -> false
                                }
                            } else false
                        },
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(30.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(0.05f),
                        focusedContainerColor = if (viewModel.isListening) Color(0xFFFF0055).copy(0.4f) else Color(0xFF00E5FF).copy(0.4f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, if (viewModel.isListening) Color(0xFFFF0055) else Color(0xFF00E5FF)))
                    )
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (viewModel.isListening) {
                            ListeningAnimation(viewModel.voiceLevel)
                        }
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Голосовой поиск",
                            tint = if (viewModel.isListening) Color(0xFFFF0055) else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { 
                        viewModel.query = it 
                        lastInteractionTime = System.currentTimeMillis()
                        onInteraction()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(textFieldFocus)
                        .onFocusChanged { 
                            if (it.isFocused) {
                                lastInteractionTime = System.currentTimeMillis()
                                onInteraction()
                            }
                        }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown) {
                                lastInteractionTime = System.currentTimeMillis()
                                onInteraction()
                                when (it.key) {
                                    Key.DirectionDown -> {
                                        if (viewModel.globalResults.isNotEmpty()) {
                                            firstItemFocus.requestFocus()
                                        }
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        searchButtonFocus.requestFocus()
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        voiceButtonFocus.requestFocus()
                                        true
                                    }
                                    Key.DirectionUp -> true
                                    Key.Enter, Key.NumPadEnter -> {
                                        viewModel.performSearch(viewModel.query) {
                                            scope.launch {
                                                delay(400)
                                                try { firstItemFocus.requestFocus() } catch(_:Exception) {}
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        },
                    placeholder = { Text("Введите название фильма...", color = Color.Gray) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(0.08f),
                        unfocusedContainerColor = Color.White.copy(0.03f),
                        focusedIndicatorColor = Color(0xFF00E5FF),
                        unfocusedIndicatorColor = Color.Gray.copy(0.3f),
                        cursorColor = Color(0xFF00E5FF)
                    )
                )
                
                Spacer(Modifier.width(16.dp))
                
                Surface(
                    onClick = { 
                        lastInteractionTime = System.currentTimeMillis()
                        onInteraction()
                        viewModel.performSearch(viewModel.query) {
                            scope.launch {
                                delay(400)
                                try { firstItemFocus.requestFocus() } catch(_:Exception) {}
                            }
                        }
                    },
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight()
                        .focusRequester(searchButtonFocus)
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastInteractionTime = System.currentTimeMillis()
                                onInteraction()
                            }
                        }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown) {
                                lastInteractionTime = System.currentTimeMillis()
                                onInteraction()
                                when(it.key) {
                                    Key.DirectionLeft -> { textFieldFocus.requestFocus(); true }
                                    Key.DirectionDown -> {
                                        if (viewModel.globalResults.isNotEmpty()) {
                                            firstItemFocus.requestFocus()
                                        }
                                        true
                                    }
                                    Key.DirectionRight, Key.DirectionUp -> true
                                    else -> false
                                }
                            } else false
                        },
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(0.05f),
                        focusedContainerColor = Color(0xFF00E5FF).copy(0.4f)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (viewModel.isSearching) {
                            ScanningAnimation()
                            Text("ПОИСК...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Text("НАЙТИ", color = Color.White)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp)
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 60.dp)
                ) {
                    itemsIndexed(
                        items = viewModel.globalResults,
                        key = { index, result -> "${result.intentData}_${result.title}_$index" }
                    ) { index, result ->
                        val total = viewModel.globalResults.size
                        SearchResultCard(
                            result = result, 
                            imageLoader = customImageLoader,
                            modifier = (if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                                .onFocusChanged { 
                                    if (it.isFocused) {
                                        lastInteractionTime = System.currentTimeMillis()
                                        onInteraction()
                                    }
                                }
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown) {
                                        lastInteractionTime = System.currentTimeMillis()
                                        onInteraction()
                                        when (it.key) {
                                            Key.DirectionUp -> {
                                                if (index < 6) { 
                                                    textFieldFocus.requestFocus()
                                                    true
                                                } else false
                                            }
                                            Key.DirectionLeft -> {
                                                if (index % 6 == 0) {
                                                    voiceButtonFocus.requestFocus()
                                                    true
                                                } else false
                                            }
                                            Key.DirectionRight -> false
                                            Key.DirectionDown -> {
                                                val lastRowStart = if (total % 6 == 0) (total - 6).coerceAtLeast(0) else total - (total % 6)
                                                index >= lastRowStart || index == total - 1
                                            }
                                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                                viewModel.launchResult(result)
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }, 
                            onClick = { viewModel.launchResult(result) }
                        )
                    }
                }
            }
        }

        // Overlays at the end of root Box to be on top
        if (viewModel.showTorrServeInstallDialog) {
            TorrServeInstallDialog(
                onDismiss = { viewModel.showTorrServeInstallDialog = false },
                downloadUrl = viewModel.torrServeDownloadLink
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchResultCard(result: SearchResult, imageLoader: ImageLoader, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color(0xFF00E5FF))),
            border = Border(BorderStroke(1.dp, Color.White.copy(0.1f)))
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(0.08f),
            focusedContainerColor = Color.White.copy(0.2f)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val shouldShowPoster = !result.posterUrl.isNullOrEmpty()

                if (shouldShowPoster) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(result.posterUrl)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                } else if (result.icon != null) {
                    val bitmap = (result.icon as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).align(Alignment.Center),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF333333), Color(0xFF1A1A1A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = result.sourceApp.take(1).uppercase(),
                            color = Color.White.copy(0.5f),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (!result.quality.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        colors = SurfaceDefaults.colors(containerColor = Color(0xFF00E5FF).copy(0.85f)),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = result.quality,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (!result.year.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp),
                        colors = SurfaceDefaults.colors(containerColor = Color(0xFFE91E63).copy(0.85f)),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = result.year,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 0.dp),
                    colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(0.7f)),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = result.sourceApp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                if (!result.size.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 0.dp),
                        colors = SurfaceDefaults.colors(containerColor = Color(0xFF00C853).copy(0.8f)),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = result.size,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = result.title ?: "",
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = if (isFocused) Int.MAX_VALUE else 0,
                            animationMode = MarqueeAnimationMode.Immediately,
                            initialDelayMillis = 2000, // Увеличиваем задержку для стабильности Layout
                            spacing = androidx.compose.foundation.MarqueeSpacing.fractionOfContainer(0.2f)
                        )
                )
            }
        }
    }
}
