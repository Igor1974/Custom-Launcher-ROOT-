package com.deepnight.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UpdateDialog(
    update: AppUpdateManager.UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: (onProgress: (Float) -> Unit) -> Unit
) {
    val neonCyan = Color(0xFF00E5FF)
    val deepBlack = Color(0xFF0A0A0A)
    val downloadFocusRequester = remember { FocusRequester() }
    
    var progress by remember { mutableFloatStateOf(-1f) }
    val animatedProgress by animateFloatAsState(targetValue = if (progress < 0f) 0f else progress)

    BackHandler { if (progress < 0f) onDismiss() }

    LaunchedEffect(Unit) {
        downloadFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = { if (progress < 0f) onDismiss() }) {
        Surface(
            modifier = Modifier
                .width(420.dp)
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
                    text = "ОБНОВЛЕНИЕ СИСТЕМЫ",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Thin,
                    letterSpacing = 4.sp,
                    style = TextStyle(shadow = Shadow(neonCyan.copy(alpha = 0.5f), blurRadius = 15f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Доступна версия ${update.versionName}",
                    color = neonCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (progress >= 0f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .background(neonCyan)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = update.changelog,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (progress < 0f) {
                                onDownload { progress = it }
                            }
                        },
                        enabled = progress < 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .focusRequester(downloadFocusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = neonCyan,
                            contentColor = Color.Black,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black,
                            disabledContainerColor = neonCyan.copy(alpha = 0.3f)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = if (progress >= 0f) "ЗАГРУЗКА..." else "ОБНОВИТЬ СЕЙЧАС",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (progress < 0f) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.05f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = "ПОЗЖЕ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
