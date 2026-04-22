package com.deepnight.launcher

import android.app.Application
import androidx.work.*
import com.deepnight.launcher.parser.TorrentNetworkClient
import com.deepnight.launcher.worker.WallpaperWorker
import java.util.concurrent.TimeUnit

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Глобальный обработчик крашей для перезапуска лаунчера
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("DeepNight", "CRITICAL CRASH in thread ${thread.name}: ${throwable.message}")
            throwable.printStackTrace()
            
            // Пытаемся перезапустить лаунчер через 2 секунды
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 12345, intent, 
                android.app.PendingIntent.FLAG_CANCEL_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 2000, pendingIntent)
            
            // Завершаем текущий процесс
            android.os.Process.killProcess(android.os.Process.myPid())
            java.lang.System.exit(10)
        }

        TorrentNetworkClient.init(this)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "wallpaper_generation",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        val updateRequest = PeriodicWorkRequestBuilder<com.deepnight.launcher.worker.UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "app_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )

        // Запуск DSP-движка (аналог Wavelet)
        try {
            val dspIntent = android.content.Intent(this, com.deepnight.launcher.dsp.DSPService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(dspIntent)
            } else {
                startService(dspIntent)
            }
            android.util.Log.i("DeepNight", "DSP Service started successfully")
        } catch (e: Exception) {
            android.util.Log.e("DeepNight", "Failed to start DSP Service: ${e.message}")
        }
    }
}