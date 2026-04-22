package com.deepnight.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.topjohnwu.superuser.Shell

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("ACTION_TYPE") ?: return
        Log.d("AlarmReceiver", "Alarm received: $type")
        
        val pendingResult = goAsync()

        // Берем WakeLock, чтобы процессор не уснул во время выполнения команд
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DeepNight:AlarmWakeLock"
        )
        wakeLock.acquire(10000) // Держим максимум 10 секунд

        Thread {
            try {
                val hasRoot = try { Shell.getShell().isRoot } catch(_: Exception) { false }

                if (type == "WAKEUP") {
                    handleWakeup(context, hasRoot)
                }
                else if (type == "SLEEP") {
                    handleSleep(context, hasRoot)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error in background thread: ${e.message}")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }.start()
    }

    private fun handleWakeup(context: Context, hasRoot: Boolean) {
        if (hasRoot) {
            Shell.cmd("input keyevent KEYCODE_WAKEUP").exec()
            Shell.cmd("wm dismiss-keyguard").exec()
            Shell.cmd("media volume --set 15").exec()
        }

        val pkg = context.getSharedPreferences("tv_alarm_prefs", Context.MODE_PRIVATE)
            .getString("selected_pkg", null)

        if (!pkg.isNullOrEmpty() && pkg != "не выбрано") {
            Thread.sleep(2000)
            
            if (hasRoot) {
                Shell.cmd("monkey -p $pkg 1").exec()
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                launchIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    context.startActivity(it)
                }
            }
        }
    }

    private fun handleSleep(context: Context, hasRoot: Boolean) {
        if (hasRoot) {
            Shell.cmd("input keyevent KEYCODE_SLEEP").exec()
        } else {
            // Отправляем команду нашему перехватчику (Accessibility Service)
            val sleepIntent = Intent("com.deepnight.launcher.ACTION_FORCE_SLEEP").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(sleepIntent)
        }
    }
}
