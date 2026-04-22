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
    
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val link: String,
        val changelog: String,
        val checksum: String? = null
    )

    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
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

    suspend fun downloadAndInstallUpdate(context: Context, update: UpdateInfo, onProgress: ((Float) -> Unit)? = null) = withContext(Dispatchers.IO) {
        // Проверка разрешения на установку из неизвестных источников (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls() && !com.topjohnwu.superuser.Shell.getShell().isRoot) {
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

        // Используем внешнюю папку для APK, так как системный установщик часто не видит внутренний кэш
        // Приоритет отдаем getExternalFilesDir(null), так как он более доступен для FileProvider на ТВ
        val apkDir = context.getExternalFilesDir(null) ?: context.externalCacheDir ?: context.cacheDir
        val fileName = "update.apk"
        val apkFile = File(apkDir, fileName)
        
        // Удаляем старый файл если он есть
        if (apkFile.exists()) {
            Log.d(TAG, "Удаление старого APK: ${apkFile.absolutePath}")
            apkFile.delete()
        }
        
        withContext(Dispatchers.Main) {
            if (onProgress == null) {
                Toast.makeText(context, "Загрузка обновления...", Toast.LENGTH_SHORT).show()
            }
        }
        
        try {
            downloadFile(update.link, apkFile, onProgress)
            
            if (apkFile.length() < MIN_APK_SIZE) {
                throw IllegalStateException("Файл слишком мал: ${apkFile.length()} байт")
            }
            
            Log.d(TAG, "Файл загружен: ${apkFile.absolutePath}, размер: ${apkFile.length()}")
            
            update.checksum?.let { expectedHash ->
                if (!verifyChecksum(apkFile, expectedHash)) {
                    throw IllegalStateException("Неверная контрольная сумма")
                }
            }
            
            installApk(context, apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download/Install failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
            if (apkFile.exists()) apkFile.delete()
        }
    }
    
    private fun downloadFile(url: String, outputFile: File, onProgress: ((Float) -> Unit)?) {
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            
            val body = response.body ?: throw IllegalStateException("Пустое тело ответа")
            val totalBytes = body.contentLength()
            
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0 && onProgress != null) {
                            onProgress(bytesRead.toFloat() / totalBytes)
                        }
                    }
                }
            }
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

    private suspend fun installApk(context: Context, file: File) {
        Log.d(TAG, "Установка APK: ${file.absolutePath}, размер: ${file.length()}")
        
        if (!file.exists()) {
            Log.e(TAG, "Файл не существует")
            return
        }

        // Если есть Root - устанавливаем тихо и надежно
        val isRoot = try { com.topjohnwu.superuser.Shell.getShell().isRoot } catch (e: Exception) { false }
        
        if (isRoot) {
            Log.d(TAG, "Root detected, using silent install")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Установка обновления (Root)...", Toast.LENGTH_LONG).show()
            }
            val result = withContext(Dispatchers.IO) {
                val tmpPath = "/data/local/tmp/update.apk"
                // Копируем через cat, так как у su точно есть доступ к обоим путям
                com.topjohnwu.superuser.Shell.cmd(
                    "rm -f $tmpPath",
                    "cat ${file.absolutePath} > $tmpPath",
                    "chmod 666 $tmpPath",
                    "pm install -r -d -g $tmpPath",
                    "rm -f $tmpPath"
                ).exec()
            }
            
            if (result.isSuccess) {
                Log.i(TAG, "Silent install successful")
                return
            } else {
                Log.e(TAG, "Silent install failed. Exit code: ${result.code}")
                Log.e(TAG, "Error output: ${result.out.joinToString("\n")}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Root-установка не удалась (код ${result.code})", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.d(TAG, "No root access or shell error, falling back to Intent install")
        }

        launchInstallIntent(context, file)
    }

    private suspend fun launchInstallIntent(context: Context, file: File) {
        // Делаем файл доступным для чтения другими приложениями (установщиком)
        file.setReadable(true, false)
        
        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get URI for file", e)
            return
        }
        
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                   Intent.FLAG_GRANT_READ_URI_PERMISSION or
                   Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        
        // Для старых версий дублируем через ACTION_INSTALL_PACKAGE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            installIntent.action = Intent.ACTION_INSTALL_PACKAGE
        }
        
        try {
            Log.d(TAG, "Launching install intent for URI: $uri")
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install intent failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Ошибка запуска установки: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
