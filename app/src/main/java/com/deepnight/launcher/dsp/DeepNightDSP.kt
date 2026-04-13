package com.deepnight.launcher.dsp

import android.media.audiofx.DynamicsProcessing
import android.util.Log

/**
 * Продвинутый звуковой процессор DeepNight.
 * Реализован на базе DynamicsProcessing API (аналог Wavelet).
 * Поддерживает 2 канала (Stereo) для ТВ и внешних систем.
 */
class DeepNightDSP(private val sessionId: Int = 0) {

    private var engine: DynamicsProcessing? = null
    private val channelCount = 2 // Stereo

    // Сетка частот Wavelet (11 полос)
    private val bandFrequencies = floatArrayOf(
        62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f, 24000f, 32000f
    )

    init {
        try {
            setupEngine()
        } catch (e: Exception) {
            Log.e("DeepNightDSP", "Ошибка инициализации DSP: ${e.message}")
        }
    }

    private fun setupEngine() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val builder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                channelCount, 
                true, // Pre-EQ включен
                bandFrequencies.size, // 11 полос
                true, // MBC включен
                bandFrequencies.size,
                true, // Post-EQ включен
                bandFrequencies.size,
                true // Limiter включен
            )

            engine = DynamicsProcessing(0, sessionId, builder.build())
            engine?.enabled = true
        }
        
        applyDefaultSettings()
    }

    private fun applyDefaultSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // Настройка лимитера согласно Wavelet: Attack 60ms, Release 10ms, Threshold -2dB
            for (i in 0 until channelCount) {
                val limiter = DynamicsProcessing.Limiter(
                    true, // включен
                    true, // в цепи
                    i, // канал
                    60f, // attack ms (Wavelet standard)
                    10f, // release ms (Wavelet standard)
                    1.0f, // ratio
                    -2.0f, // threshold dB (Wavelet standard)
                    0f // post gain
                )
                engine?.setLimiterByChannelIndex(i, limiter)
            }
        }
    }

    /**
     * Включение/выключение режима "Ночной просмотр" (Компрессия)
     * Сжимает динамический диапазон: тихие звуки становятся слышнее, громкие — тише.
     */
    fun setNightMode(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            for (i in 0 until channelCount) {
                if (enabled) {
                    // Агрессивный лимитер и компрессия для ночи (быстрая атака, долгий релиз)
                    val nightLimiter = DynamicsProcessing.Limiter(
                        true, true, i, 
                        2f,     // attack
                        120f,   // release
                        10f,    // ratio
                        -8f,    // threshold
                        3f      // post gain (компенсация тишины)
                    )
                    engine?.setLimiterByChannelIndex(i, nightLimiter)
                } else {
                    // Возврат к эталону Wavelet
                    val stdLimiter = DynamicsProcessing.Limiter(
                        true, true, i, 
                        60f,    // attack
                        10f,    // release
                        1.0f,   // ratio
                        -2.0f,  // threshold
                        0f      // post gain
                    )
                    engine?.setLimiterByChannelIndex(i, stdLimiter)
                }
            }
        }
    }

    /**
     * Применение пресета эквалайзера.
     * Теперь настроено под 11-полосную сетку Wavelet.
     */
    fun applyPreset(presetName: String) {
        if (presetName == "Custom") return

        val gains = when (presetName) {
            // Movie: V-образная кривая, глубокий бас и кристальные верха
            "Movie" -> floatArrayOf(6f, 4f, 2f, 0f, -1f, 0f, 1f, 3f, 5f, 6f, 7f)
            // Music: Сбалансированный звук, акцент на деталях
            "Music" -> floatArrayOf(4f, 2f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)
            // Voice: Режим четкости речи, срез низов, подъем середины
            "Voice" -> floatArrayOf(-6f, -4f, -2f, 2f, 4f, 5f, 3f, 1f, -1f, -3f, -5f)
            else -> FloatArray(bandFrequencies.size)
        }

        setCustomGains(gains)
    }

    /**
     * Применение пользовательских настроек эквалайзера.
     */
    fun setCustomGains(gains: FloatArray) {
        if (gains.size == bandFrequencies.size) {
            gains.forEachIndexed { index, gain ->
                setPreEqBandGain(index, gain)
            }
        }
    }

    /**
     * Эффект "Тонкомпенсации" (Equal Loudness) и расширения баса.
     * Усиливает низкие и высокие частоты, делая звук "сочнее" на любой громкости.
     * @param boost уровень усиления от 0.0 (выкл) до 1.0 (макс)
     */
    fun setLoudness(boost: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // СТРОГОЕ ОТКЛЮЧЕНИЕ: если boost 0, выключаем весь движок, чтобы не было лишнего баса
            if (boost <= 0.01f) {
                engine?.enabled = false
                return
            }

            engine?.enabled = true
            
            // Убираем Input Gain полностью, чтобы избежать клиппинга и срабатывания системного лимитера
            for (i in 0 until channelCount) {
                engine?.setInputGainbyChannel(i, 0.0f)
            }

            // Линейное усиление без Sqrt для предсказуемости
            val lowBoost = boost * 7.0f // Максимум +7дБ (Wavelet Style)
            val highBoost = boost * 4.0f // Максимум +4дБ
            
            // Очистка Post-EQ перед применением Loudness
            for (index in bandFrequencies.indices) {
                setPostEqBandGain(index, 0f)
            }

            // Тонкомпенсация по новой сетке (11 полос)
            setPostEqBandGain(0, lowBoost)        // 62.5Hz
            setPostEqBandGain(1, lowBoost * 0.7f) // 125Hz
            setPostEqBandGain(2, lowBoost * 0.3f) // 250Hz
            
            setPostEqBandGain(8, highBoost * 0.4f) // 16kHz
            setPostEqBandGain(9, highBoost * 0.8f) // 24kHz
            setPostEqBandGain(10, highBoost)       // 32kHz
            
            for (i in 0 until channelCount) {
                // Настройка MBC для глубокого баса (первая полоса)
                val mbcBand = DynamicsProcessing.MbcBand(
                    true, 
                    bandFrequencies[0], // Самый низ
                    2f,  // knee
                    60f, // release
                    2.2f, // ratio (еще мягче)
                    -18f, // threshold
                    -10f, 
                    0f, 
                    1.0f + (boost * 0.8f), 
                    boost * 1.5f, 
                    boost * 2f
                )
                engine?.setMbcBandByChannelIndex(i, 0, mbcBand)
                
                for (bandIdx in 1 until bandFrequencies.size) {
                    val neutralMbc = DynamicsProcessing.MbcBand(
                        false, bandFrequencies[bandIdx], 5f, 50f, 1.0f, -24f, -10f, 0f, 1.0f, 0f, 0f
                    )
                    engine?.setMbcBandByChannelIndex(i, bandIdx, neutralMbc)
                }
            }
        }
    }

    private fun setPreEqBandGain(bandIndex: Int, gain: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val band = DynamicsProcessing.EqBand(true, bandFrequencies[bandIndex], gain)
            for (i in 0 until channelCount) {
                engine?.setPreEqBandByChannelIndex(i, bandIndex, band)
            }
        }
    }

    private fun setPostEqBandGain(bandIndex: Int, gain: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val band = DynamicsProcessing.EqBand(true, bandFrequencies[bandIndex], gain)
            for (i in 0 until channelCount) {
                engine?.setPostEqBandByChannelIndex(i, bandIndex, band)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        engine?.enabled = enabled
    }

    fun release() {
        engine?.enabled = false
        engine?.release()
        engine = null
    }
}
