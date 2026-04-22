package com.deepnight.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        val bootActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        if (action in bootActions) {
            val tvPrefs = context.getSharedPreferences("tv_alarm_prefs", Context.MODE_PRIVATE)

            // 1. Восстанавливаем будильники
            val wakeActive = tvPrefs.getBoolean("wake_active", false)
            val sleepActive = tvPrefs.getBoolean("sleep_active", false)

            if (wakeActive) {
                schedule(
                    context,
                    tvPrefs.getInt("wake_h", 8),
                    tvPrefs.getInt("wake_m", 0),
                    "WAKEUP"
                )
            }
            if (sleepActive) {
                schedule(
                    context,
                    tvPrefs.getInt("sleep_h", 23),
                    tvPrefs.getInt("sleep_m", 0),
                    "SLEEP"
                )
            }

            // 2. Запуск сервиса оверлея
            val wakeStr = if (wakeActive) String.format(
                Locale.getDefault(),
                "%02d:%02d",
                tvPrefs.getInt("wake_h", 8),
                tvPrefs.getInt("wake_m", 0)
            ) else "--:--"
            val sleepStr = if (sleepActive) String.format(
                Locale.getDefault(),
                "%02d:%02d",
                tvPrefs.getInt("sleep_h", 23),
                tvPrefs.getInt("sleep_m", 0)
            ) else "--:--"

            val overlayIntent = Intent(context, OverlayService::class.java).apply {
                putExtra("WAKE_TIME", wakeStr)
                putExtra("SLEEP_TIME", sleepStr)
            }

            try {
                ContextCompat.startForegroundService(context, overlayIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. АВТОЗАПУСК ЛАУНЧЕРА
            val launcherPrefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            val isEnabled = launcherPrefs.getBoolean("autostart_on_boot", true)

// Проверяем, не запускали ли мы уже автозапуск в этой сессии (опционально, но полезно)
            if (isEnabled) {
                val autostartPkg = AppRepository.getAutostartApp(context)
                val targetPackage = autostartPkg ?: context.packageName

                       Handler(Looper.getMainLooper()).postDelayed({
                    val pm = context.packageManager
                    val launchIntent = pm.getLeanbackLaunchIntentForPackage(targetPackage)
                        ?: pm.getLaunchIntentForPackage(targetPackage)

                    launchIntent?.apply {
                       addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                       //  УДАЛИЛ FLAG_ACTIVITY_RESET_TASK_IF_NEEDED — это он ворует фокус!

                        // Если это наш собственный лаунчер, добавим проверку,
                         //чтобы не запускать его поверх уже открытых приложений
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

                        //context.startActivity(this)
                    }
                }, 3000)
            }

        }
    }

    private fun schedule(context: Context, h: Int, m: Int, type: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            setInexactAlarm(am, context, h, m, type)
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.deepnight.launcher.ALARM_ACTION"
            putExtra("ACTION_TYPE", type)
            identifier = type
        }

        val pi = PendingIntent.getBroadcast(
            context, if (type == "WAKEUP") 101 else 102, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    private fun setInexactAlarm(am: AlarmManager, context: Context, h: Int, m: Int, type: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.deepnight.launcher.ALARM_ACTION"
            identifier = type
        }
        val pi = PendingIntent.getBroadcast(context, if (type == "WAKEUP") 101 else 102, intent, PendingIntent.FLAG_IMMUTABLE)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }
}