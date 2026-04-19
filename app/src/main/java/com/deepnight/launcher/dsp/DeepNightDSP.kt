package com.deepnight.launcher.dsp

import android.media.audiofx.DynamicsProcessing
import android.util.Log

/**
 * Продвинутый звуковой процессор DeepNight.
 * Реализован на базе DynamicsProcessing API.
 * Использует 11-полосную сетку для максимальной детализации звука.
 */
class DeepNightDSP(private val sessionId: Int = 0) {

    private var engine: DynamicsProcessing? = null
    private val channelCount = 2 // Stereo

    // 11-полосная сетка (расширенная для ТВ и Hi-Fi акустики)
    private val bandFrequencies = floatArrayOf(
        31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 12000f, 16000f
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
                true, // Pre-EQ
                bandFrequencies.size,
                true, // MBC (Multi-band Compressor)
                bandFrequencies.size,
                true, // Post-EQ
                bandFrequencies.size,
                true  // Limiter
            )

            engine = DynamicsProcessing(0, sessionId, builder.build())
            engine?.enabled = true
        }
        
        applyDefaultSettings()
    }

    private fun applyDefaultSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // Лимитер: Attack 60ms, Release 10ms, Threshold -2dB
            for (i in 0 until channelCount) {
                val limiter = DynamicsProcessing.Limiter(
                    true, true, i, 60f, 10f, 1.0f, -2.0f, 0f
                )
                engine?.setLimiterByChannelIndex(i, limiter)
            }
        }
    }

    fun setNightMode(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            for (i in 0 until channelCount) {
                val limiter = if (enabled) {
                    DynamicsProcessing.Limiter(true, true, i, 2f, 120f, 10f, -8f, 3f)
                } else {
                    DynamicsProcessing.Limiter(true, true, i, 60f, 10f, 1.0f, -2.0f, 0f)
                }
                engine?.setLimiterByChannelIndex(i, limiter)
            }
        }
    }

    fun applyPreset(presetName: String) {
        if (presetName == "Custom") return

        val gains = when (presetName) {
            "Movie" -> floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 1f, 3f, 5f, 6f, 7f)
            "Music" -> floatArrayOf(4f, 3f, 1f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)
            "Voice" -> floatArrayOf(-6f, -4f, -2f, 1f, 3f, 5f, 4f, 2f, 0f, -2f, -4f)
            else -> FloatArray(bandFrequencies.size)
        }

        setCustomGains(gains)
    }

    fun setCustomGains(gains: FloatArray) {
        if (gains.size == bandFrequencies.size) {
            gains.forEachIndexed { index, gain ->
                setPreEqBandGain(index, gain)
            }
        }
    }

    fun setLoudness(boost: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            if (boost <= 0.01f) {
                engine?.enabled = false
                return
            }

            engine?.enabled = true
            
            for (i in 0 until channelCount) {
                engine?.setInputGainbyChannel(i, 0.0f)
            }

            val lowBoost = boost * 7.5f 
            val highBoost = boost * 5.0f
            
            for (index in bandFrequencies.indices) {
                setPostEqBandGain(index, 0f)
            }

            // Тонкомпенсация (11 полос)
            setPostEqBandGain(0, lowBoost)         // 31.25Hz
            setPostEqBandGain(1, lowBoost * 0.85f)  // 62.5Hz
            setPostEqBandGain(2, lowBoost * 0.45f)  // 125Hz
            
            setPostEqBandGain(8, highBoost * 0.4f)  // 8kHz
            setPostEqBandGain(9, highBoost * 0.8f)  // 12kHz
            setPostEqBandGain(10, highBoost)        // 16kHz
            
            // Настройка MBC с уникальными частотами для каждой полосы
            for (i in 0 until channelCount) {
                for (bandIdx in bandFrequencies.indices) {
                    val isBassBand = bandIdx < 2
                    val mbcBand = DynamicsProcessing.MbcBand(
                        isBassBand, 
                        bandFrequencies[bandIdx], 
                        if (isBassBand) 15f else 50f,
                        if (isBassBand) 120f else 100f,
                        if (isBassBand) 2.5f else 1.0f,
                        -24f, 
                        2f, 
                        -90f, 1.0f, 
                        if (isBassBand) boost * 2.0f else 0f,
                        if (isBassBand) boost * 1.5f else 0f
                    )
                    engine?.setMbcBandByChannelIndex(i, bandIdx, mbcBand)
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
