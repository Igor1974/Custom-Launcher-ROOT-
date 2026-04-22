package com.deepnight.launcher.radio

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AudioPulse(
    modifier: Modifier = Modifier,
    color: Color = Color.Cyan,
    count: Int = 12,
    barWidth: Dp = 4.dp,
    gap: Dp = 2.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "audio_pulse")
        
        repeat(count) { index ->
            // Разная длительность для создания эффекта хаотичности спектра
            val duration = 300 + (index % 5 * 100)
            val heightFraction by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(heightFraction)
                    .background(color, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            )
        }
    }
}
