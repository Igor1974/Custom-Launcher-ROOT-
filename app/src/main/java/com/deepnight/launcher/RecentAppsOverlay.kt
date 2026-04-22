package com.deepnight.launcher

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RecentAppsOverlay(onDismiss: () -> Unit) {
    val recents = AppRepository.recentApps
    val context = LocalContext.current
    val iconSize = remember { LauncherSettings.getIconSize(context) }

    val listFocusRequester = remember { FocusRequester() }
    var canDismiss by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(400)
        if (recents.isNotEmpty()) {
            listFocusRequester.requestFocus()
        }
        canDismiss = true
    }

    LaunchedEffect(recents.size) {
        if (recents.isNotEmpty()) {
            listFocusRequester.requestFocus()
        } else if (canDismiss) {
            delay(600)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.9f))
            .focusProperties {
                onEnter = {
                    listFocusRequester.requestFocus()
                    FocusRequester.Default
                }
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP && canDismiss) {
                        onDismiss()
                    }
                    return@onKeyEvent true
                }
                false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок
            Text(
                "НЕДАВНИЕ ПРИЛОЖЕНИЯ",
                color = Color.White.copy(0.6f),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // ВОЗВРАЩАЕМ ПОЯСНЕНИЕ
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color.Red.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = " Вверх — закрыть приложение",
                    color = Color.White.copy(0.4f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            if (recents.isEmpty()) {
                Text("Список пуст", color = Color.Gray, fontSize = 20.sp)
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((iconSize + 140).dp)
                        .focusRequester(listFocusRequester)
                        .focusGroup(),
                    contentPadding = PaddingValues(horizontal = 60.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(
                        items = recents,
                        key = { _, app -> app.packageName }
                    ) { _, app ->
                        RecentAppCard(
                            app = app,
                            onRemove = { AppRepository.removeFromRecents(app) },
                            onClick = {
                                AppRepository.launchApp(context, app)
                                onDismiss()
                            }
                        )
                    }

                    item {
                        ClearAllCard(onClear = {
                            AppRepository.clearAllRecents()
                        })
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RecentAppCard(
    app: AppInfo,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var isRemoving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Анимация вылета вверх
    val offsetY by animateDpAsState(
        targetValue = if (isRemoving) (-300).dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "removeAnim"
    )

    val iconSize = LauncherSettings.getIconSize(context)
    val density = LocalDensity.current
    val sizeInPx = remember(iconSize) { with(density) { iconSize.dp.roundToPx() } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .offset(y = offsetY) // Применяем смещение
            .onKeyEvent {
                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                    it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && !isRemoving) {
                    isRemoving = true
                    scope.launch {
                        delay(250) // Даем анимации проиграться
                        onRemove()
                    }
                    true
                } else false
            }
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(width = (iconSize + 100).dp, height = (iconSize + 40).dp)
                .onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF222222),
                focusedContainerColor = Color.White
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(context.packageManager.getApplicationIcon(app.packageName))
                            .size(sizeInPx)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize.dp),
                    contentScale = ContentScale.Fit
                )

                if (isFocused) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.name,
            color = if (isFocused) Color.White else Color.Gray,
            fontSize = 18.sp,
            maxLines = 1,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ClearAllCard(onClear: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClear,
            modifier = Modifier
                .size(width = 100.dp, height = 100.dp)
                .onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Red.copy(alpha = 0.1f),
                focusedContainerColor = Color.Red
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = if (isFocused) Color.White else Color.Red,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("ОЧИСТИТЬ", color = if (isFocused) Color.White else Color.Gray, fontSize = 12.sp)
    }
}
