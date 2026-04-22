@file:Suppress("ComposePreviewMustBeTopLevelFunction")

package com.deepnight.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
fun AboutDialog(
    onDismiss: () -> Unit,
    onCheckUpdate: () -> Unit = {}
) {
    val neonCyan = Color(0xFF00E5FF)
    val deepBlack = Color(0xFF0A0A0A)

    val scrollState = rememberScrollState()
    val checkUpdateFocusRequester = remember { FocusRequester() }

    // Перехватываем кнопку "Назад" на пульте
    BackHandler { onDismiss() }

    LaunchedEffect(Unit) {
        checkUpdateFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(420.dp)
                .fillMaxHeight(0.9f)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(containerColor = deepBlack)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 20.dp, horizontal = 32.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(neonCyan.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                        .border(1.2.dp, neonCyan.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.banner),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "DEEP NIGHT",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Thin,
                    letterSpacing = 8.sp,
                    style = TextStyle(shadow = Shadow(neonCyan.copy(alpha = 0.5f), blurRadius = 20f))
                )

                Text(
                    text = "LAUNCHER",
                    color = neonCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ВЕРСИЯ ${BuildConfig.VERSION_NAME}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .background(neonCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "ULTIMATE",
                            color = neonCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Профессиональная оболочка Android TV\nнового поколения",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        Modifier
                            .padding(vertical = 14.dp)
                            .width(40.dp)
                            .height(1.dp)
                            .background(neonCyan.copy(alpha = 0.3f))
                    )

                    val featureStyle = TextStyle(
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraLight,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )

                    Text("ИНТЕЛЛЕКТУАЛЬНЫЙ ИНТЕРФЕЙС", style = featureStyle)
                    Spacer(Modifier.height(6.dp))
                    Text("ВИЗУАЛЬНЫЕ ЭФФЕКТЫ", style = featureStyle)
                    Spacer(Modifier.height(6.dp))
                    Text("МГНОВЕННАЯ ОПТИМИЗАЦИЯ ПАМЯТИ", style = featureStyle)
                    Spacer(Modifier.height(6.dp))
                    Text("МОНИТОРИНГ СИСТЕМЫ", style = featureStyle)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(4.dp).background(neonCyan, RoundedCornerShape(50)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "DEVELOPED BY ~Игорь~ (4PDA)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(4.dp).background(neonCyan, RoundedCornerShape(50)))
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { onCheckUpdate() },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(44.dp)
                        .focusRequester(checkUpdateFocusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = neonCyan.copy(alpha = 0.1f),
                        contentColor = neonCyan,
                        focusedContainerColor = neonCyan,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = "ПРОВЕРИТЬ ОБНОВЛЕНИЯ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onDismiss() },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(38.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(14.dp))
                ) {
                    Text(
                        text = "ВЕРНУТЬСЯ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p", backgroundColor = 0xFF0A0A0A, showBackground = true)
@Composable
fun AboutDialogPreview() {
    AboutDialog(onDismiss = {})
}
