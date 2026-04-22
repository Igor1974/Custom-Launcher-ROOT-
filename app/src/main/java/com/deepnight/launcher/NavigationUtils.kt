package com.deepnight.launcher


import android.content.Context
import android.content.Intent
import android.provider.Settings

object NavigationUtils {
    fun openSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }
}