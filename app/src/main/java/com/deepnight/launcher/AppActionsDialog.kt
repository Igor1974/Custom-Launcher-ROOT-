package com.deepnight.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppActionsDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onMoveManual: () -> Unit,
    onNamesVisibilityChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firstFocusRequester = remember { FocusRequester() }
    var canClick by remember { mutableStateOf(false) }

    val isCurrentAutostart = AppRepository.isAutostartApp(context, app.packageName)

    // ДОБАВЛЕНО: Состояние скрытия названий
    var isNamesHidden by remember { mutableStateOf(AppRepository.areNamesHidden(context)) }

    // Обработчик "Назад" для Compose
    androidx.activity.compose.BackHandler {
        if (canClick) onDismiss()
    }

    LaunchedEffect(Unit) {
        delay(400)
        canClick = true
        firstFocusRequester.requestFocus()
    }

    // 1. ВНЕШНИЙ КОНТЕЙНЕР (Затемнение фона)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    if (canClick) onDismiss()
                    true
                } else false
            }
            .clickable(enabled = canClick, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // 2. САМО МЕНЮ
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            ),
            modifier = Modifier
                .width(320.dp)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                        if (canClick) onDismiss()
                        true
                    } else false
                }
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(20.dp))
                .clickable(enabled = true, onClick = {}),
            glow = Glow(Color.Black.copy(0.5f), 20.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {

                // --- ЗАГОЛОВОК ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = app.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = Color.White
                    )
                    Text(
                        text = if (isCurrentAutostart) "Назначено на автостарт" else "Действия",
                        fontSize = 12.sp,
                        color = if (isCurrentAutostart) Color.Cyan else Color.Gray
                    )
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(1.dp)
                        .background(Color.White.copy(0.05f))
                )

                // --- СПИСОК КОМАНД ---
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {

                    // НОВЫЙ ПУНКТ: Скрытие/Показ названий
                    SimpleMenuItem(
                        text = if (isNamesHidden) "Показать названия приложений" else "Скрыть названия приложений",
                        focusRequester = firstFocusRequester
                    ) {
                        if (canClick) {
                            val nextState = !isNamesHidden
                            AppRepository.setNamesHidden(context, nextState)
                            isNamesHidden = nextState

                            // ВЫЗЫВАЕМ КОЛБЭК, чтобы MainActivity сразу обновила состояние
                            onNamesVisibilityChanged(nextState)
                        }
                    }

                    SimpleMenuItem(
                        text = "В начало списка"
                        // focusRequester убран отсюда и перенесен выше на первый пункт
                    ) {
                        if (canClick) {
                            scope.launch {
                                AppRepository.moveAppToTop(context, app)
                                onDismiss()
                            }
                        }
                    }

                    SimpleMenuItem("Переместить") {
                        if (canClick) onMoveManual()
                    }

                    if (isCurrentAutostart) {
                        SimpleMenuItem(text = "Убрать из автозапуска") {
                            if (canClick) {
                                AppRepository.setAutostartApp(context, null)
                                onDismiss()
                            }
                        }
                    } else {
                        SimpleMenuItem(text = "В автостарт при вкл. ТВ") {
                            if (canClick) {
                                AppRepository.setAutostartApp(context, app.packageName)
                                onDismiss()
                            }
                        }
                    }

                    SimpleMenuItem("Скрыть приложение") {
                        if (canClick) {
                            scope.launch {
                                AppRepository.hideApp(context, app.packageName)
                                onDismiss()
                            }
                        }
                    }

                    if (AppRepository.hasHiddenApps(context)) {
                        SimpleMenuItem("Показать все скрытые приложения") {
                            if (canClick) {
                                scope.launch {
                                    AppRepository.resetHiddenApps(context)
                                    onDismiss()
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.05f)))
                    Spacer(modifier = Modifier.height(4.dp))

                    SimpleMenuItem("Удалить приложение", isRed = true) {
                        if (canClick) {
                            scope.launch {
                                AppRepository.uninstallApp(context, app.packageName)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

// SimpleMenuItem остается без изменений

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SimpleMenuItem(
    text: String,
    focusRequester: FocusRequester? = null,
    isRed: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 2.dp)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = if (isRed) Color.Red.copy(0.15f) else Color.White.copy(0.1f),
            contentColor = if (isRed) Color(0xFFFFFFFF) else Color.White.copy(0.8f),
            focusedContentColor = if (isRed) Color.Red else Color.Cyan
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp))
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}