package com.deepnight.launcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

@SuppressLint("AccessibilityPolicy")
class HomeInterceptorService : AccessibilityService() {

    private val sleepReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.deepnight.launcher.ACTION_FORCE_SLEEP") {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter("com.deepnight.launcher.ACTION_FORCE_SLEEP")
        ContextCompat.registerReceiver(
            this, sleepReceiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        val isHome = keyCode == KeyEvent.KEYCODE_HOME
        val isSettings = (keyCode == KeyEvent.KEYCODE_F1 || keyCode == 119 || event.scanCode == 85)
        val isSearch = keyCode == KeyEvent.KEYCODE_SEARCH ||
                keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                keyCode == KeyEvent.KEYCODE_ASSIST || keyCode == 174

        if (isHome || isSettings || isSearch) {
            if (isSearch) {
                if (action == KeyEvent.ACTION_DOWN) {
                    // Агрессивно перехватываем
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        this.action = "com.deepnight.launcher.VOICE_SEARCH_TRIGGER"
                    }
                    startActivity(intent)
                    return true 
                }
            }

            if (action == KeyEvent.ACTION_UP) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    when {
                        isSettings -> {
                            putExtra("OPEN_SETTINGS", true)
                            this.action = "com.deepnight.launcher.OPEN_SETTINGS"
                        }
                        isSearch -> {
                            this.action = Intent.ACTION_SEARCH
                        }
                    }
                }
                startActivity(intent)
            }
            return true
        }

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Если вылез системный ассистент - УБИВАЕМ ЕГО
            if (packageName.contains("katniss") || packageName.contains("assistant") || packageName.contains("voice.search")) {
                if (packageName != "com.deepnight.launcher") {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        this.action = "com.deepnight.launcher.VOICE_SEARCH_TRIGGER"
                    }
                    startActivity(intent)
                }
            }

            if (AppRepository.isVideoApp(packageName)) {
                sendBroadcast(Intent("com.deepnight.launcher.VIDEO_STARTED"))
            } else if (packageName == this.packageName) {
                sendBroadcast(Intent("com.deepnight.launcher.VIDEO_STOPPED"))
            }
        }
    }
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(sleepReceiver) } catch (_: Exception) {}
    }
}