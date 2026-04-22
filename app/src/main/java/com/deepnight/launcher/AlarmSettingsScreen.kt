package com.deepnight.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

// --- Цветовая схема ---
val NeonCyan = Color(0xFF00E5FF)
val DeepBackground = Color(0xFF050505)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AlarmSettingsScreen(onClose: () -> Unit) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tv_alarm_prefs", Context.MODE_PRIVATE) }

    var wakeH by remember { mutableIntStateOf(prefs.getInt("wake_h", 7)) }
    var wakeM by remember { mutableIntStateOf(prefs.getInt("wake_m", 0)) }
    var isWakeEnabled by remember { mutableStateOf(prefs.getBoolean("wake_active", false)) }

    var sleepH by remember { mutableIntStateOf(prefs.getInt("sleep_h", 23)) }
    var sleepM by remember { mutableIntStateOf(prefs.getInt("sleep_m", 0)) }
    var isSleepEnabled by remember { mutableStateOf(prefs.getBoolean("sleep_active", false)) }

    var showWakeDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    val wakeCardFocusRequester = remember { FocusRequester() }
    val sleepCardFocusRequester = remember { FocusRequester() }
    val appPickerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        wakeCardFocusRequester.requestFocus()
    }

    var selectedAppPackage by remember {
        mutableStateOf(prefs.getString("selected_pkg", "не выбрано"))
    }

    val wakeStr = String.format(Locale.getDefault(), "%02d:%02d", wakeH, wakeM)
    val sleepStr = String.format(Locale.getDefault(), "%02d:%02d", sleepH, sleepM)

    LaunchedEffect(isWakeEnabled, isSleepEnabled, wakeH, wakeM, sleepH, sleepM) {
        saveAlarmSettings(context, "WAKEUP", wakeH, wakeM, isWakeEnabled)
        saveAlarmSettings(context, "SLEEP", sleepH, sleepM, isSleepEnabled)
        updateOverlay(context, if (isWakeEnabled) wakeStr else "--:--", if (isSleepEnabled) sleepStr else "--:--")
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepBackground)) {
        Button(
            onClick = onClose,
            modifier = Modifier
                .padding(32.dp)
                .width(160.dp)
                .height(48.dp)
                .align(Alignment.TopStart),
            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.colors(
                containerColor = Color.White.copy(0.05f),
                focusedContainerColor = NeonCyan,
                contentColor = NeonCyan,
                focusedContentColor = Color.Black
            )
        ) {
            Text(
                "НАЗАД",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "DEEP NIGHT",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = 12.sp,
                style = TextStyle(shadow = Shadow(NeonCyan.copy(0.5f), blurRadius = 25f))
            )
            Spacer(Modifier.height(60.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                ModernTimeCard(
                    "ПРОБУЖДЕНИЕ",
                    wakeStr,
                    isWakeEnabled,
                    modifier = Modifier.focusRequester(wakeCardFocusRequester)
                ) { showWakeDialog = true }
                ModernTimeCard(
                    "ВРЕМЯ СНА",
                    sleepStr,
                    isSleepEnabled,
                    modifier = Modifier.focusRequester(sleepCardFocusRequester)
                ) { showSleepDialog = true }
            }

            Spacer(Modifier.height(40.dp))

            Surface(
                onClick = { showAppPicker = true },
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(80.dp)
                    .focusRequester(appPickerFocusRequester),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(0.03f),
                    focusedContainerColor = Color.White.copy(0.12f)
                ),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, NeonCyan)))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = if (selectedAppPackage != "не выбрано") NeonCyan else Color.Gray
                    )
                    Spacer(Modifier.width(20.dp))
                    Text(selectedAppPackage ?: "ВЫБРАТЬ ПРИЛОЖЕНИЕ", color = Color.White, fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(40.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ModernStatusToggle("БУДИЛЬНИК", isWakeEnabled) { isWakeEnabled = it }
                ModernStatusToggle("РЕЖИМ СНА", isSleepEnabled) { isSleepEnabled = it }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = {
                showAppPicker = false
                appPickerFocusRequester.requestFocus()
            },
            onSelect = { pkg ->
                selectedAppPackage = pkg
                prefs.edit().putString("selected_pkg", pkg).apply()
                showAppPicker = false
                appPickerFocusRequester.requestFocus()
            }
        )
    }

    if (showWakeDialog) {
        DeepNightTimePickerDialog(wakeH, wakeM, onDismiss = {
            showWakeDialog = false
            wakeCardFocusRequester.requestFocus()
        }) { h, m ->
            wakeH = h; wakeM = m; showWakeDialog = false
            wakeCardFocusRequester.requestFocus()
        }
    }
    if (showSleepDialog) {
        DeepNightTimePickerDialog(sleepH, sleepM, onDismiss = {
            showSleepDialog = false
            sleepCardFocusRequester.requestFocus()
        }) { h, m ->
            sleepH = h; sleepM = m; showSleepDialog = false
            sleepCardFocusRequester.requestFocus()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = context.packageManager

    val apps = remember {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .map {
                val icon = it.loadIcon(pm)
                val bmp = createBitmap(icon.intrinsicWidth.coerceAtLeast(1), icon.intrinsicHeight.coerceAtLeast(1))
                val canvas = Canvas(bmp)
                icon.setBounds(0, 0, canvas.width, canvas.height)
                icon.draw(canvas)
                Triple(it.loadLabel(pm).toString(), it.activityInfo.packageName, bmp.asImageBitmap())
            }.sortedBy { it.first }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxHeight(0.8f).fillMaxWidth(0.85f),
            shape = RoundedCornerShape(32.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF0A0A0A)),
            border = Border(BorderStroke(1.dp, Color.White.copy(0.1f)))
        ) {
            Column(modifier = Modifier.padding(32.dp)) {
                Text(
                    "ВЫБЕРИТЕ ПРИЛОЖЕНИЕ",
                    color = NeonCyan,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    letterSpacing = 4.sp
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    itemsIndexed(apps) { index, (label, pkg, icon) ->
                        var startAnim by remember { mutableStateOf(false) }
                        val alpha by animateFloatAsState(if (startAnim) 1f else 0f, tween(500))
                        LaunchedEffect(Unit) { delay(index * 20L); startAnim = true }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }
                        ) {
                            Surface(
                                onClick = { onSelect(pkg) },
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(0.05f),
                                    focusedContainerColor = Color.White.copy(0.15f)
                                ),
                                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, NeonCyan))),
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Image(
                                        painter = BitmapPainter(icon),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(0.65f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                label,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ModernTimeCard(
    label: String,
    time: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(280.dp, 160.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(0.03f),
            focusedContainerColor = Color.White.copy(0.1f)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(NeonCyan.copy(0.2f), 30.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, NeonCyan)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(
                time,
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraLight,
                color = if (active) Color.White else Color.White.copy(0.2f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ModernStatusToggle(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick = { onToggle(!enabled) },
        modifier = modifier.width(280.dp).height(72.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (enabled) NeonCyan.copy(0.1f) else Color.White.copy(0.05f),
            focusedContainerColor = if (enabled) NeonCyan else Color.White.copy(0.2f),
            contentColor = Color.White,
            focusedContentColor = if (enabled) Color.Black else Color.White
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 18.sp)
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DeepNightTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirm: (Int, Int) -> Unit
) {
    var h by remember { mutableIntStateOf(initialHour) }
    var m by remember { mutableIntStateOf(initialMinute) }
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF111111)),
            border = Border(BorderStroke(2.dp, NeonCyan))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "НАСТРОЙКА ВРЕМЕНИ",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeValueSpinner(h, 24, onValueChange = { h = it }, focusRequester = focusRequester)
                    Text(
                        ":",
                        fontSize = 44.sp,
                        color = NeonCyan,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    TimeValueSpinner(m, 60, onValueChange = { m = it })
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onConfirm(h, m) },
                    modifier = Modifier.width(200.dp).height(52.dp),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = NeonCyan.copy(0.1f),
                        focusedContainerColor = NeonCyan,
                        contentColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("СОХРАНИТЬ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
fun TimeValueSpinner(
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        val buttonModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

        Button(
            onClick = { onValueChange((value + 1) % max) },
            modifier = buttonModifier.size(44.dp),
            shape = ButtonDefaults.shape(CircleShape),
            colors = ButtonDefaults.colors(
                containerColor = Color.White.copy(0.08f),
                focusedContainerColor = NeonCyan,
                contentColor = Color.White,
                focusedContentColor = Color.Black
            )
        ) {
            Text("▲", fontSize = 14.sp)
        }

        Text(
            text = String.format(Locale.getDefault(), "%02d", value),
            fontSize = 48.sp,
            fontWeight = FontWeight.Thin,
            color = Color.White,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Button(
            onClick = { onValueChange(if (value - 1 < 0) max - 1 else value - 1) },
            modifier = Modifier.size(44.dp),
            shape = ButtonDefaults.shape(CircleShape),
            colors = ButtonDefaults.colors(
                containerColor = Color.White.copy(0.08f),
                focusedContainerColor = NeonCyan,
                contentColor = Color.White,
                focusedContentColor = Color.Black
            )
        ) {
            Text("▼", fontSize = 14.sp)
        }
    }
}

fun saveAlarmSettings(context: Context, type: String, h: Int, m: Int, active: Boolean) {
    val prefs = context.getSharedPreferences("tv_alarm_prefs", Context.MODE_PRIVATE)
    val prefix = if (type == "WAKEUP") "wake" else "sleep"

    prefs.edit()
        .putInt("${prefix}_h", h)
        .putInt("${prefix}_m", m)
        .putBoolean("${prefix}_active", active)
        .apply()

    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        action = "com.deepnight.launcher.ALARM_ACTION"
        putExtra("ACTION_TYPE", type)
        identifier = type
    }

    val requestCode = if (type == "WAKEUP") 101 else 102
    val pi = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    am.cancel(pi)

    if (active) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun updateOverlay(context: Context, wake: String, sleep: String) {
    val intent = Intent(context, OverlayService::class.java).apply {
        putExtra("WAKE_TIME", wake)
        putExtra("SLEEP_TIME", sleep)
    }
    try { context.startService(intent) } catch (_: Exception) { }
}
