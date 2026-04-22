package com.deepnight.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.random.Random

// ОСТАВЛЯЕМ ТОЛЬКО ОДНО ОБЪЯВЛЕНИЕ ЗДЕСЬ
class Particle(
    var position: Offset,
    var velocity: Offset,
    var alpha: Float = 1f,
    val color: Color,
    val size: Float,
    val fadingSpeed: Float = Random.nextFloat() * 0.02f + 0.012f
) {
    // Храним последние 4 позиции для отрисовки хвоста
    val trail = ArrayDeque<Offset>(4)
}

@Composable
fun ParticlesBackground(
    modifier: Modifier = Modifier,
    isHighRes: Boolean = false,
    emitterTrigger: Offset? = null,
    emitterColor: Color = Color.Cyan
) {
    val particles = remember { mutableStateListOf<Particle>() }
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing))
    )

    LaunchedEffect(emitterTrigger) {
        if (emitterTrigger != null && emitterTrigger.x > 10f && emitterTrigger.y > 10f) {
            val count = if (isHighRes) 12 else 7 // Чуть меньше, так как хвосты едят ресурсы
            repeat(count) {
                particles.add(
                    Particle(
                        position = emitterTrigger,
                        velocity = Offset(
                            (Random.nextFloat() - 0.5f) * 20f, // Быстрый разлет для эффекта скорости
                            (Random.nextFloat() - 0.5f) * 14f
                        ),
                        color = emitterColor,
                        size = Random.nextFloat() * 6f + 2f
                    )
                )
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        phase

        if (particles.size > 120) {
            repeat(15) { if (particles.isNotEmpty()) particles.removeAt(0) }
        }

        particles.forEach { p ->
            // --- 1. ОТРИСОВКА ШЛЕЙФА (TRAIL) ---
            // Рисуем хвост ПЕРЕД основной частицей
            p.trail.forEachIndexed { index, trailPos ->
                // index 0 - самая старая точка, index 3 - самая свежая
                // Делаем хвост тоньше и прозрачнее к началу
                val trailFactor = (index + 1).toFloat() / (p.trail.size + 1)
                drawCircle(
                    color = p.color.copy(alpha = p.alpha * 0.2f * trailFactor),
                    radius = p.size * trailFactor,
                    center = trailPos
                )
            }

            // --- 2. ОСНОВНАЯ ЧАСТИЦА ---
            // Аура (Свечение)
            drawCircle(
                color = p.color.copy(alpha = p.alpha * 0.3f),
                radius = p.size * 3.5f,
                center = p.position,
                blendMode = BlendMode.Screen
            )
            // Ядро (Искра)
            // Используем функцию lerp(старт, финиш, прогресс)
            drawCircle(
                color = lerp(p.color.copy(alpha = p.alpha), Color.White, 0.5f),
                radius = p.size * 0.7f,
                center = p.position,
                blendMode = BlendMode.Screen
            )

            // --- 3. ОБНОВЛЕНИЕ ФИЗИКИ И ПАМЯТИ ---
            // Добавляем текущую позицию в хвост
            if (p.trail.size >= 4) {
                p.trail.removeFirst()
            }
            p.trail.addLast(p.position)

            p.position += p.velocity
            p.velocity += Offset(0f, -0.08f) // Всплытие (гравитация вверх)
            p.alpha -= p.fadingSpeed
        }

        particles.removeAll { it.alpha <= 0f }
    }
}