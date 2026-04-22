package com.deepnight.launcher

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File

object TorrServeManager {
    private const val RELEASE_URL = "https://releases.yourok.ru/tor/apk_release.json"
    val PACKAGES = listOf("ru.yourok.torrserve", "ru.yourok.torrserve.matrix")

    /**
     * Проверяет наличие установленного TorrServe.
     */
    fun isInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Возвращает информацию о последнем релизе.
     */
    suspend fun getLatestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        try {
            val client = AerialVideoProvider.client
            val request = Request.Builder().url(RELEASE_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext getDefaultRelease()
                val body = response.body?.string() ?: return@withContext getDefaultRelease()
                val json = JSONArray(body)
                if (json.length() > 0) {
                    val first = json.getJSONObject(0)
                    var link = first.getString("link")
                    
                    if (link.contains("github.com") && !link.endsWith(".apk")) {
                        link = "https://github.com/YouROK/TorrServe/releases/download/MatriX.141.Client/TorrServe_MatriX.141.Client-release.apk"
                    }
                    
                    return@withContext ReleaseInfo(
                        version = first.getString("version"),
                        link = link
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TorrServeManager", "Update check failed: ${e.message}")
        }
        getDefaultRelease()
    }

    private fun getDefaultRelease(): ReleaseInfo {
        return ReleaseInfo("MatriX.141", "https://github.com/YouROK/TorrServe/releases/download/MatriX.141.Client/TorrServe_MatriX.141.Client-release.apk")
    }

    data class ReleaseInfo(val version: String, val link: String)

    /**
     * Скачивание и установка через DownloadManager.
     */
    fun downloadAndInstall(context: Context, url: String) {
        val fileName = "TorrServe_Install.apk"
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(publicDir, fileName)
        
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("TorrServe")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == downloadId) {
                    installApk(context, file)
                    context.unregisterReceiver(this)
                }
            }
        }

        // Ключевое исправление: использование ContextCompat для API 34+
        ContextCompat.registerReceiver(
            context, 
            onComplete, 
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), 
            ContextCompat.RECEIVER_EXPORTED
        )
        
        Toast.makeText(context, "Загрузка TorrServe запущена", Toast.LENGTH_SHORT).show()
    }

    private fun installApk(context: Context, file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("TorrServeManager", "Installation error: ${e.message}")
        }
    }
}
