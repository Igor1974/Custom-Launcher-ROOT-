package com.deepnight.launcher.radio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.deepnight.launcher.ui.LauncherViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RadioScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPlaying by RadioManager.isPlaying
    val currentStation by RadioManager.currentStation
    val focusRequester = remember { FocusRequester() }
    val launcherViewModel: LauncherViewModel = viewModel()
    val wallpaperUrl by launcherViewModel.wallpaperUrl.collectAsState()

    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorOccurred by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val initialFocusRequester = remember { FocusRequester() }
    var focusAlreadyRequested by remember { mutableStateOf(false) }

    // Удаляем BackHandler и заменяем его на onPreviewKeyEvent в Box ниже
    // BackHandler { onClose() }

    fun loadStations() {
        isLoading = true
        errorOccurred = false
        searchJob?.cancel()
        searchJob = scope.launch {
            try {
                val apiStations = RadioManager.fetchBestStations(context)
                
                // Очистка от дублей и технических индексов [1], [2] и т.д.
                stations = apiStations
                    .map { station -> 
                        // Убираем индексы [X] и лишние пробелы из названия
                        station.copy(name = station.name.replace(Regex("\\[\\d+]"), "").trim())
                    }
                    .distinctBy { it.name.lowercase() } // Оставляем только уникальные названия, сохраняя порядок API (лучшие сверху)
                
                errorOccurred = stations.isEmpty()
            } catch (_: Exception) {
                errorOccurred = true
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadStations()
    }

    LaunchedEffect(isLoading, stations, currentStation) {
        if (!isLoading && stations.isNotEmpty() && !focusAlreadyRequested) {
            try {
                // Если станция уже играет, фокус на неё, иначе на первую
                focusRequester.requestFocus()
                focusAlreadyRequested = true
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Сплошной темный фон вместо блюра
            .onPreviewKeyEvent { 
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    onClose()
                    true
                } else {
                    false
                }
            }
    ) {
        // Обои на фоне БЕЗ блюра (используем прозрачность и темный фон)
        if (wallpaperUrl.isNotEmpty()) {
            AsyncImage(
                model = wallpaperUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.15f } // Просто делаем их едва заметными
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
            // Фиксированный Header для предотвращения "прыжков" UI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp), // Фиксированная высота
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DEEPNIGHT RADIO", 
                    style = MaterialTheme.typography.labelLarge, 
                    color = Color(0xFF00E5FF),
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(end = 32.dp)
                )

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.animation.Crossfade(
                        targetState = isPlaying && currentStation != null,
                        label = "header_info"
                    ) { playing ->
                        val station = currentStation
                        if (playing && station != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF00E5FF), RoundedCornerShape(50))
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    station.name,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    "${station.codec.uppercase()} • ${station.bitrate}kbps",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        } else {
                            Text(
                                "Выберите станцию для запуска",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 2.dp)
                }
            } else if (errorOccurred) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ошибка загрузки. Нажмите Back для выхода.", color = Color.White.copy(alpha = 0.4f))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(stations, key = { it.uuid }) { station ->
                        val isCurrent = currentStation?.uuid == station.uuid
                        val isCurrentlyPlaying = isPlaying && isCurrent

                        StationItem(
                            station = station,
                            isCurrent = isCurrent,
                            isPlaying = isCurrentlyPlaying,
                            modifier = if (isCurrent) Modifier.focusRequester(focusRequester) else if (stations.indexOf(station) == 0) Modifier.focusRequester(focusRequester) else Modifier,
                            onClick = {
                                if (isCurrentlyPlaying) {
                                    RadioManager.stop(context)
                                } else {
                                    RadioManager.playStation(context, station)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StationItem(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val neonCyan = Color(0xFF00E5FF)
    
    // Облегчаем анимацию: убираем плавность scale при прокрутке, делаем мгновенно или очень быстро
    val scale = if (isFocused) 1.04f else 1f

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(84.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { 
                scaleX = scale
                scaleY = scale
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isCurrent) Color(0xFF222222) else Color(0xFF161616),
            focusedContainerColor = neonCyan,
            contentColor = if (isCurrent) neonCyan else Color.White,
            focusedContentColor = Color.Black
        ),
        // Glow — главная причина лагов на ТВ, убираем его или делаем минимальным
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = neonCyan.copy(alpha = 0.3f), elevation = 4.dp)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Маленький акцентный визуализатор справа снизу (только если не в фокусе или активен)
            if (isCurrent && isPlaying) {
                AudioPulse(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 6.dp, end = 10.dp)
                        .width(36.dp)
                        .height(10.dp),
                    color = (if (isFocused) Color.Black else neonCyan).copy(alpha = 0.7f),
                    count = 8,
                    barWidth = 3.dp,
                    gap = 2.dp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = station.name.replace("Radio Caprice - ", ""),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 16.sp
                    ),
                    color = if (isFocused) Color.Black else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!isFocused) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = (station.tags?.split(",")?.firstOrNull() ?: station.codec).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}


