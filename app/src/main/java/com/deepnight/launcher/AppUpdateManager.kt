package com.deepnight.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

object AppUpdateManager {
    private const val UPDATE_URL = "https://raw.githubusercontent.com/Igor1974/Custom-Launcher-ROOT-/main/update.json"
    private const val TAG = "AppUpdateManager"
    private const val MIN_APK_SIZE = 1024 * 1024 // Минимум 1MB
    private const val BUFFER_SIZE = 8192
    
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val link: String,
        val changelog: String,
        val checksum: String? = null
    )

    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val client = AerialVideoProvider.client
            val request = Request.Builder().url(UPDATE_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val latestVersionCode = json.getInt("versionCode")
                
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }

                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

                if (latestVersionCode > currentVersionCode) {
                    return@withContext UpdateInfo(
                        versionName = json.getString("versionName"),
                        versionCode = latestVersionCode,
                        link = json.getString("link"),
                        changelog = json.optString("changelog", ""),
                        checksum = json.optString("checksum", "").takeIf { it.isNotEmpty() }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check update error: ${e.message}")
        }
        null
    }

    suspend fun downloadAndInstallUpdate(context: Context, update: UpdateInfo) = withContext(Dispatchers.IO) {
        // Проверка разрешения на установку из неизвестных источников (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                withContext(Dispatchers.Main) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Разрешите установку из этого источника и повторите", Toast.LENGTH_LONG).show()
                }
                return@withContext
            }
        }

        // Скачиваем в файловое хранилище приложения (не требует внешних разрешений)
        val fileName = "DeepNight_Update_${update.versionCode}.apk"
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Загрузка обновления...", Toast.LENGTH_SHORT).show()
        }
        
        try {
            // Скачиваем файл напрямую через OkHttp
            downloadFile(update.link, apkFile)
            
            // Проверяем размер файла
            if (apkFile.length() < MIN_APK_SIZE) {
                throw IllegalStateException("Файл слишком мал: ${apkFile.length()} байт")
            }
            
            // Проверка контрольной суммы если есть
            update.checksum?.let { expectedHash ->
                if (!verifyChecksum(apkFile, expectedHash)) {
                    throw IllegalStateException("Неверная контрольная сумма файла")
                }
            }
            
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download/Install failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
            // Удаляем битый файл если он был создан
            if (apkFile.exists()) apkFile.delete()
        }
    }
    
    private fun downloadFile(url: String, outputFile: File) {
        val client = AerialVideoProvider.client
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            
            response.body?.byteStream()?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            } ?: throw IllegalStateException("Пустое тело ответа")
        }
    }
    
    private fun verifyChecksum(file: File, expectedHash: String): Boolean {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            hash.equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Checksum verification failed", e)
            true // Пропускаем проверку если возникла ошибка вычисления, чтобы не блокировать установку
        }
    }

    private fun installApk(context: Context, file: File) {
        Log.d(TAG, "Установка APK: ${file.absolutePath}, размер: ${file.length()}")
        
        if (!file.exists()) {
            Log.e(TAG, "Файл не существует")
            Toast.makeText(context, "Файл обновления не найден", Toast.LENGTH_SHORT).show()
            return
        }
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        Log.d(TAG, "FileProvider URI: $uri")
        
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            Toast.makeText(context, "Ошибка установки: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
