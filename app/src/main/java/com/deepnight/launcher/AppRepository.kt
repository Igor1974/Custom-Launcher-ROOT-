package com.deepnight.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AppRepository {
    private val _installedApps = mutableStateListOf<AppInfo>()
    val allApps: SnapshotStateList<AppInfo> = _installedApps

    private val _recentApps = mutableStateListOf<AppInfo>()
    val recentApps: SnapshotStateList<AppInfo> = _recentApps

    private var isLoaded = false
    private const val PREFS_NAME = "launcher_prefs"
    private const val KEY_PRIORITY = "apps_priority"
    private const val KEY_HIDDEN = "hidden_apps"

    private const val KEY_HIDE_NAMES = "hide_app_names"

    // Ключ для хранения пакета автозапуска
    private const val KEY_AUTOSTART_PACKAGE = "autostart_package"

    // --- ЛОГИКА АВТОЗАПУСКА ВЫБРАННОГО ПРИЛОЖЕНИЯ ---

    /**
     * Сохраняет пакет для автозапуска.
     * Если передать null — автозапуск сбросится на лаунчер по умолчанию.
     */
    fun setAutostartApp(context: Context, packageName: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (packageName == null) {
            prefs.edit().remove(KEY_AUTOSTART_PACKAGE).apply()
        } else {
            prefs.edit().putString(KEY_AUTOSTART_PACKAGE, packageName).apply()
        }
    }

    fun areNamesHidden(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_NAMES, false)
    }

    fun setNamesHidden(context: Context, hide: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HIDE_NAMES, hide).apply()
    }

    /**
     * Возвращает имя пакета приложения, выбранного для автозапуска.
     */
    fun getAutostartApp(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AUTOSTART_PACKAGE, null)
    }

    /**
     * Проверяет, является ли конкретное приложение выбранным для автозапуска.
     */
    fun isAutostartApp(context: Context, packageName: String): Boolean {
        return getAutostartApp(context) == packageName
    }

    // --- ОСНОВНАЯ ЛОГИКА РАБОТЫ С ПРИЛОЖЕНИЯМИ ---

    val videoApps = setOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "ru.yourok.num",
        "ru.yourok.torrserve",
        "com.hulu.plus",
        "com.apple.atve.android.appletv",
        "org.videolan.vlc",
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "com.liskovsoft.videotube.tv",
        "com.teamsmart.videomanager.tv",
        "com.instantbits.cast.receiver",
        "com.pockettv.android",
        "com.graymatrix.did",
        "com.sony.dtv.smartmediaapp",
        "com.tcl.videoplayer",
        "com.ionitech.videoplayer",
        "com.plexapp.android",
        "com.codex.vimu",
        "com.jio.jiocinema",
        "com.hotstar.tv",
        "com.zee5.zeetv",
        "com.turner.tnt.android.tv",
        "com.turner.tbs.android.tv",
        "com.hbo.hbonow",
        "com.hbo.broadband",
        "com.google.android.videos",
        "com.vudu.android.tivo"
    )

    fun isVideoApp(packageName: String?): Boolean {
        if (packageName == null) return false
        return videoApps.contains(packageName) || 
               packageName.contains("video", ignoreCase = true) || 
               packageName.contains("player", ignoreCase = true) || 
               packageName.contains("cinema", ignoreCase = true)
    }

    fun launchApp(context: Context, app: AppInfo) {
        if (app.packageName == "com.deepnight.launcher.radio") {
            // Здесь мы должны вызвать открытие экрана радио.
            // Но AppRepository не знает про MainActivity.
            // Можно отправить Broadcast, который MainActivity поймает.
            context.sendBroadcast(Intent("com.deepnight.launcher.OPEN_RADIO"))
            return
        }

        _recentApps.removeAll { it.packageName == app.packageName }
        _recentApps.add(0, app)
        if (_recentApps.size > 12) _recentApps.removeAt(_recentApps.size - 1)

        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(app.packageName)

        intent?.let {
            if (videoApps.contains(app.packageName)) {
                context.sendBroadcast(Intent("com.deepnight.launcher.VIDEO_STARTED"))
            }
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    suspend fun loadApps(context: Context, force: Boolean = false) {
        if (isLoaded && !force) return

        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val allResolveInfos = mutableSetOf<android.content.pm.ResolveInfo>()

                allResolveInfos.addAll(pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
                ))

                allResolveInfos.addAll(pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
                ))

                val priorityList = getPriority(context)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val hiddenSet = prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
                val myPkg = context.packageName

                val apps = allResolveInfos
                    .distinctBy { it.activityInfo.packageName }
                    .filter { info ->
                        val pkgName = info.activityInfo.packageName
                        val isBasicFilterPass = pkgName !in hiddenSet && pkgName != myPkg
                        val label = info.loadLabel(pm).toString()
                        val hasLabel = label.isNotBlank()

                        isBasicFilterPass && hasLabel && !isSystemComponent(pkgName)
                    }
                    .map { info ->
                        AppInfo(
                            name = info.loadLabel(pm).toString(),
                            packageName = info.activityInfo.packageName,
                            icon = info.loadIcon(pm)
                        )
                    }.toMutableList()

                // Добавляем виртуальное Радио
                val radioApp = AppInfo(
                    name = "Радио",
                    packageName = "com.deepnight.launcher.radio",
                    icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, android.R.drawable.ic_lock_silent_mode_off)!!
                )
                apps.add(0, radioApp)

                val sortedApps = apps.sortedWith(compareBy<AppInfo> { app ->
                    val index = priorityList.indexOf(app.packageName)
                    if (index != -1) index else Int.MAX_VALUE
                }.thenBy { it.name.lowercase() })

                withContext(Dispatchers.Main) {
                    _installedApps.clear()
                    _installedApps.addAll(sortedApps)
                    isLoaded = true
                }
            } catch (e: Exception) {
                Log.e("LAUNCHER_ERROR", "Error: ${e.message}")
            }
        }
    }

    private fun isSystemComponent(pkgName: String): Boolean {
        return pkgName == "android" ||
                pkgName.startsWith("com.android.providers") ||
                pkgName.startsWith("com.android.systemui") ||
                pkgName.startsWith("com.android.inputmethod")
    }

    suspend fun moveAppToTop(context: Context, app: AppInfo) {
        withContext(Dispatchers.Main) {
            val index = _installedApps.indexOfFirst { it.packageName == app.packageName }
            if (index != -1) {
                val item = _installedApps.removeAt(index)
                _installedApps.add(0, item)
                finalizeOrder(context)
            }
        }
    }

    fun moveAppStep(context: Context, packageName: String, direction: Int) {
        val index = _installedApps.indexOfFirst { it.packageName == packageName }
        if (index == -1) return

        val newIndex = index + direction
        if (newIndex in 0 until _installedApps.size) {
            val app = _installedApps.removeAt(index)
            _installedApps.add(newIndex, app)
            finalizeOrder(context)
        }
    }

    fun hideApp(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hiddenSet = prefs.getStringSet(KEY_HIDDEN, mutableSetOf()) ?: mutableSetOf()
        val newSet = hiddenSet.toMutableSet()
        newSet.add(packageName)
        prefs.edit().putStringSet(KEY_HIDDEN, newSet).apply()

        _installedApps.removeAll { it.packageName == packageName }
        _recentApps.removeAll { it.packageName == packageName }
    }

    fun finalizeOrder(context: Context) {
        val currentOrder = _installedApps.map { it.packageName }
        savePriority(context, currentOrder)
    }

    fun resetHiddenApps(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HIDDEN).apply()
        MainScope().launch {
            loadApps(context, force = true)
        }
    }

    fun hasHiddenApps(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hiddenSet = prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        return hiddenSet.isNotEmpty()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun uninstallApp(context: Context, packageName: String) {
        if (packageName == context.packageName) return
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val result = Shell.cmd("pm uninstall $packageName").exec()
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        _installedApps.removeAll { it.packageName == packageName }
                        _recentApps.removeAll { it.packageName == packageName }
                        Toast.makeText(context, "Удалено успешно", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e("UNINSTALL_ERROR", "Uninstall failed: ${e.message}")
            }
        }
    }

    private fun savePriority(context: Context, packageList: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PRIORITY, packageList.joinToString(",")).apply()
    }

    private fun getPriority(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PRIORITY, "") ?: ""
        return if (saved.isEmpty()) emptyList() else saved.split(",")
    }

    fun removeFromRecents(app: AppInfo) {
        _recentApps.removeAll { it.packageName == app.packageName }
    }

    fun clearAllRecents() {
        _recentApps.clear()
    }

    /**
     * Глобальный поиск через внешние приложения.
     */
   }