package com.deepnight.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageReceiver(private val onUpdate: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val packageName = intent.data?.schemeSpecificPart
        
        Log.d("PackageReceiver", "Action: $action, Package: $packageName")
        
        if (action == Intent.ACTION_PACKAGE_ADDED || 
            action == Intent.ACTION_PACKAGE_REMOVED || 
            action == Intent.ACTION_PACKAGE_REPLACED) {
            
            // Запускаем обновление списка приложений в фоновом потоке
            CoroutineScope(Dispatchers.IO).launch {
                AppRepository.loadApps(context, force = true)
                launch(Dispatchers.Main) {
                    onUpdate()
                }
            }
        }
    }
}
