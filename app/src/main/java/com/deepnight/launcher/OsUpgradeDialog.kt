package com.deepnight.launcher

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OsUpgradeDialog(
    onDismiss: () -> Unit
) {
    val neonCyan = Color(0xFF00E5FF)
    val deepBlack = Color(0xFF0A0A0A)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Deep Night Launcher готов к интеграции в систему") }
    var progress by remember { mutableFloatStateOf(0f) }

    val confirmFocusRequester = remember { FocusRequester() }
    val isIntegrated = remember { DeepNightOSManager.isAlreadyIntegrated() }

    val buttonShape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
    val buttonHeight = Modifier.height(48.dp)

    LaunchedEffect(Unit) {
        if (step == 0) confirmFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = { if (step != 1) onDismiss() }) {
        Surface(
            modifier = Modifier
                .width(500.dp)
                .wrapContentHeight()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(containerColor = deepBlack)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DEEP NIGHT OS",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Thin,
                    letterSpacing = 6.sp,
                    style = TextStyle(shadow = Shadow(neonCyan.copy(alpha = 0.5f), blurRadius = 20f))
                )

                Text(
                    text = "CORE UPGRADE v2.6",
                    color = neonCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isIntegrated && step == 0) {
                    Text(
                        text = "Системная интеграция уже активна.\nDeep Night OS работает в штатном режиме.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().then(buttonHeight).focusRequester(confirmFocusRequester),
                        shape = buttonShape,
                        colors = ButtonDefaults.colors(containerColor = neonCyan, contentColor = Color.Black),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ОТЛИЧНО", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    when (step) {
                        0 -> {
                            Text(
                                text = "Превратите ваш ТВ в Deep Night OS.\n\n" +
                                        "• Системный приоритет (OOM -1000)\n" +
                                        "• Полная визуализация звука (Audio Loopback)\n" +
                                        "• Глубокая стилизация SystemUI через RRO\n" +
                                        "• Кастомная анимация загрузки (Neon Loop)\n" +
                                        "• Отключение системного мусора\n\n" +
                                        "Требуется Magisk / Root доступ.",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Start,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f).then(buttonHeight),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.colors(containerColor = Color.White.copy(0.1f), contentColor = Color.White),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("ОТМЕНА", fontWeight = FontWeight.Bold)
                                    }
                                }
                                Button(
                                    onClick = {
                                        step = 1
                                        scope.launch {
                                            statusText = "Проверка Root доступа..."
                                            delay(800)
                                            progress = 0.2f
                                            val success = DeepNightOSManager.transformToDeepNightOS(context)
                                            if (success) {
                                                progress = 1.0f
                                                statusText = "Готово! Система перезагрузится..."
                                                delay(3000)
                                                step = 2
                                            } else {
                                                step = 3
                                                statusText = "Ошибка: Root не получен."
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).then(buttonHeight).focusRequester(confirmFocusRequester),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.colors(
                                        containerColor = neonCyan,
                                        contentColor = Color.Black,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("АКТИВИРОВАТЬ", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        1 -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = neonCyan,
                                    trackColor = Color.White.copy(0.1f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = statusText.uppercase(),
                                    color = neonCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        2 -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ИНТЕГРАЦИЯ ЗАВЕРШЕНА", color = neonCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Система перезагрузится.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { com.topjohnwu.superuser.Shell.cmd("reboot").exec() },
                                    modifier = Modifier.fillMaxWidth().then(buttonHeight),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.colors(containerColor = neonCyan, contentColor = Color.Black),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("ПЕРЕЗАГРУЗИТЬ", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        3 -> {
                            Text(statusText, color = Color.Red.copy(0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth().then(buttonHeight),
                                shape = buttonShape,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("ЗАКРЫТЬ", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}