package com.deepnight.launcher

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Это сработает, когда приложение получит права админа
        Toast.makeText(context, "Администратор устройства активирован", Toast.LENGTH_SHORT).show()
    }
}