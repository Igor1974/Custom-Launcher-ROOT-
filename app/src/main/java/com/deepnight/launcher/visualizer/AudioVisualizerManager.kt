package com.deepnight.launcher.visualizer

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.pow

/**
 * Менеджер для получения данных о спектре звука в реальном времени.
 * Оптимизирован для ТВ: обработка FFT в фоновом потоке, предвычисленные индексы.
 */
class AudioVisualizerManager(val sessionId: Int = 0) {

    private var visualizer: Visualizer? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _spectrum = MutableStateFlow(FloatArray(64))
    val spectrum = _spectrum.asStateFlow()

    private val _bassLevel = MutableStateFlow(0f)
    val bassLevel = _bassLevel.asStateFlow()

    private val _midLevel = MutableStateFlow(0f)
    val midLevel = _midLevel.asStateFlow()
    
    private val _highLevel = MutableStateFlow(0f)
    val highLevel = _highLevel.asStateFlow()

    private val bands = 64
    private var lastSpectrum = FloatArray(bands)
    private val processingBuffer = FloatArray(bands)
    private var logIndices = IntArray(bands + 1)

    init {
        precomputeLogIndices()
        try {
            visualizer = Visualizer(sessionId).apply {
                enabled = false // Гарантируем, что выключен перед настройкой
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        // Ограничиваем частоту обработки для экономии ресурсов ТВ
                        scope.launch {
                            processFftOptimized(fft)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            Log.e("AudioVisualizer", "Error: ${e.message}")
        }
    }

    private fun precomputeLogIndices() {
        val n = Visualizer.getCaptureSizeRange()[1] / 2
        // Используем более точное распределение, чтобы не терять первые бины FFT
        for (i in 0..bands) {
            val ratio = i.toDouble() / bands
            // Плавный переход от линейного к логарифмическому, чтобы низкие частоты (бас) были детальнее
            logIndices[i] = (n.toDouble().pow(ratio)).toInt().coerceIn(i, n)
        }
    }

    private fun processFftOptimized(fft: ByteArray?) {
        if (fft == null || fft.isEmpty()) return

        var bassSum = 0f
        var midSum = 0f
        var highSum = 0f

        for (i in 0 until bands) {
            val start = logIndices[i]
            val end = logIndices[i + 1]
            
            var magnitudeSum = 0f
            var count = 0
            for (j in start until end) {
                val r = abs(fft[2 * j].toInt())
                val img = abs(fft[2 * j + 1].toInt())
                magnitudeSum += (r + img).toFloat()
                count++
            }
            
            val magnitude = if (count > 0) magnitudeSum / count else 0f
            val normalized = (magnitude / 60f).coerceIn(0f, 1.5f)
            
            // Сглаживание: быстрая атака, медленное затухание
            if (normalized > lastSpectrum[i]) {
                processingBuffer[i] = normalized
            } else {
                processingBuffer[i] = lastSpectrum[i] * 0.82f + normalized * 0.18f
            }

            if (i < bands / 4) {
                bassSum += processingBuffer[i]
            } else if (i < bands * 3 / 4) {
                midSum += processingBuffer[i]
            } else {
                highSum += processingBuffer[i]
            }
        }

        // Копируем данные в стабильный массив для StateFlow (минимизируем аллокации)
        val outArray = FloatArray(bands)
        System.arraycopy(processingBuffer, 0, outArray, 0, bands)
        System.arraycopy(processingBuffer, 0, lastSpectrum, 0, bands)

        _spectrum.value = outArray
        _bassLevel.value = (bassSum / (bands / 4)).coerceIn(0f, 1f)
        _midLevel.value = (midSum / (bands / 2)).coerceIn(0f, 1f)
        _highLevel.value = (highSum / (bands / 4)).coerceIn(0f, 1f)
    }

    fun setEnabled(enabled: Boolean) {
        try {
            if (visualizer?.enabled != enabled) {
                visualizer?.enabled = enabled
                Log.d("AudioVisualizer", "Visualizer enabled: $enabled")
                if (!enabled) {
                    // Если выключается во время работы, полезно знать кто это сделал
                    // Log.d("AudioVisualizer", "Stacktrace:", Throwable())
                }
            }
        } catch (e: Exception) {
            Log.e("AudioVisualizer", "Error setting enabled $enabled: ${e.message}")
        }
    }

    fun release() {
        scope.cancel()
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
    }
}
