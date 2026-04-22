package com.deepnight.launcher


import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationHelper {
    const val CHANNEL_ID = "overlay_service_channel"
    const val DSP_CHANNEL_ID = "dsp_service_channel"

    fun createNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Канал для Overlay
        val name = "Статус ТВ Будильника"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = "Показывает состояние автоматизации питания ТВ"
        }
        notificationManager.createNotificationChannel(channel)

        // Канал для DSP (Улучшение звука)
        val dspName = "Улучшение звука (DSP)"
        val dspChannel = NotificationChannel(DSP_CHANNEL_ID, dspName, NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(dspChannel)
    }
}
