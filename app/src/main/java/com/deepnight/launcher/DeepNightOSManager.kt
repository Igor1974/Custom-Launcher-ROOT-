package com.deepnight.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.deepnight.launcher.parser.DnlNetworkClient
import com.deepnight.launcher.parser.DnlNetworkClient.await
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.Locale
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * Менеджер Deep Night OS Core v2.6.
 * Обеспечивает глубокую системную интеграцию и модификацию ресурсов "на лету".
 */
object DeepNightOSManager {
    private const val TAG = "DeepNightOS"
    
    private var isRootCached: Boolean? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private fun isRoot(): Boolean {
        if (isRootCached == null) {
            isRootCached = Shell.getShell().isRoot
        }
        return isRootCached ?: false
    }

    // ============ КОНФИГУРАЦИЯ ============
    private const val OS_VERSION = "2.6"
    private const val MAGISK_MODULE_ID = "deep_night_os"
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1000L

    const val APTOIDE_TV_PACKAGE = "cm.aptoidetv.pt"
    const val PLAY_STORE_PACKAGE = "com.android.vending"
    private val APTOIDE_MIRRORS = listOf(
        "https://raw.githubusercontent.com/Igor1974/Custom-Launcher-ROOT-/main/aptoide-tv.apk",
        "https://pool.aptoide.com/apps/cm.aptoide.pt/latest/cm.aptoide.pt.apk",
        "https://tv.aptoide.com/ae_tv_aptoide.apk",
        "https://ru.aptoide.com/download?package_name=cm.aptoide.pt&store_name=aptoide-web&entry_point=appstore_home_installer&utm_content=home&utm_source=aptoide&utm_campaign=direct&utm_term=e"
    )

    // Анимации (смягчено для устранения "прыжков")
    private const val ANIMATION_SCALE_FAST = "0.4"
    private const val ANIMATION_SCALE_NORMAL = "1.0"

    // --- ГРУППИРОВКА МУСОРА ДЛЯ ВЫБОРА ПОЛЬЗОВАТЕЛЕМ ---
    
    val ADS_AND_TELEMETRY = listOf(
        "com.google.android.leanbacklauncher.recommendations",
        "com.google.android.youtube.tvrecommendations",
        "com.google.android.tvrecommendations",
        "com.google.android.tungsten.setupwraith",
        "com.miui.systemads",
        "com.miui.analytics",
        "com.xiaomi.mitv.advertise",
        "com.xiaomi.devicereport",
        "com.facebook.system",
        "com.facebook.appmanager",
        "com.facebook.services",
        "tv.samba.atv",
        "com.nvidia.feedback",
        "de.j4velin.wallpaperChanger"
    )

    val GOOGLE_UNUSED_SERVICES = listOf(
        "com.google.android.videos",
        "com.google.android.play.games",
        "com.google.android.feedback",
        "com.android.printspooler",
        "com.google.android.apps.fitness",
        "com.google.android.printservice.recommendation"
    )

    val TCL_SPECIFIC = listOf(
        "com.tcl.partnercustomizer",
        "com.tcl.pdt.notification.service",
        "com.tcl.waterfall",
        "com.tcl.experimental",
        "com.tcl.tguard",
        "com.tcl.guard",
        "com.tcl.manager",
        "com.tcl.tv.manager",
        "com.tcl.suspension",
        "com.tcl.ext.system.upgrade",
        "com.tcl.voice.assistant.ui",
        "com.tcl.cyberui",
        "com.tcl.bi",
        "com.tcl.messagebox",
        "com.tcl.notereminder",
        "com.tcl.dashboard",
        "com.tcl.usercenter",
        "com.tcl.versionUpdateApp",
        "com.tcl.cloudenable",
        "com.tcl.ui_diagnosis",
        "com.tcl.smart_home"
    )

    val BLOATWARE_PACKAGES = ADS_AND_TELEMETRY + GOOGLE_UNUSED_SERVICES + TCL_SPECIFIC

    /**
     * Создает "супер-конфиг" версии 999, который отключает системные ограничения TCL.
     */
    private suspend fun applyGhostResourcePolicy(context: Context) = withContext(Dispatchers.IO) {
        val packageName = context.packageName
        val ghostConfig = """
        {
            "version": 999,
            "sub_version": 999,
            "timestamp": 2147483647,
            "enable": true,
            "memsettings": [{
                "memlevel": { "normal": 0, "urgent": 0 },
                "bgService_enable": false,
                "memCheck_enable": false,
                "adj_whitelist": ["$packageName", "com.google.android.katniss", "org.smarttube.stable"],
                "top_use_list": ["$packageName", "com.netflix.ninja", "com.google.android.youtube.tv"],
                "ui_services": ["$packageName"]
            }],
            "processStart": {
                "enable": true,
                "white_list": ["$packageName", "com.google.android.apps.tv.launcherx"],
                "black_list": ["com.tcl.tguard", "com.tcl.manager"]
            }
        }
        """.trimIndent()

        val tempFile = File(context.cacheDir, "resource_policy.conf")
        tempFile.writeText(ghostConfig)
        
        Shell.cmd(
            "cp ${tempFile.absolutePath} /data/system/resource_policy.conf",
            "chmod 644 /data/system/resource_policy.conf",
            "chown system:system /data/system/resource_policy.conf"
        ).exec()
        Log.i(TAG, "✓ Ghost Resource Policy v999 applied")
    }

    /**
     * Возвращает список команд для применения критических исправлений.
     */
    fun getCriticalFixCommands(context: Context): List<String> {
        val packageName = context.packageName
        val serviceId = "$packageName/.DeepNightAccessibilityService"
        val notificationListenerId = "$packageName/$packageName.MediaSessionListenerService"
        val voiceServiceId = "$packageName/$packageName.DeepNightVoiceService"

        val commands = mutableListOf(
            "appops set $packageName SYSTEM_ALERT_WINDOW allow",
            "appops set $packageName ACTIVATE_VPN allow",
            "appops set $packageName RUN_IN_BACKGROUND allow",
            "appops set $packageName APP_AUTO_START allow",
            "appops set $packageName MANAGE_EXTERNAL_STORAGE allow",
            "appops set $packageName REQUEST_INSTALL_PACKAGES allow",
            "appops set $packageName GET_USAGE_STATS allow",
            "appops set $packageName AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore",
            "settings put secure enabled_accessibility_services $serviceId",
            "settings put secure accessibility_enabled 1",
            "cmd notification allow_listener $notificationListenerId",
            "settings put secure enabled_notification_listeners $notificationListenerId",
            "pm enable com.google.android.katniss", 
            "settings put secure assistant $voiceServiceId",
            "settings put secure voice_interaction_service $voiceServiceId",
            "cmd package install-existing --user 0 $packageName", 
            "settings put secure user_setup_complete 1",
            "cmd vpn grant-consent $packageName",
            "am set-standby-bucket $packageName active",
            "am set-standby-bucket com.google.android.katniss active",
            "pm grant $packageName android.permission.RECORD_AUDIO",
            "settings put system listen_alone_mode 0"
        )
        
        // Заставка
        val screensaver = "${context.packageName}/.DeepNightScreensaverService"
        commands.add("settings put secure screensaver_enabled 1")
        commands.add("settings put secure screensaver_components $screensaver")
        
        // Подавление уведомлений T-Guard и громкости
        commands.add("settings put system show_volume_panel 0")
        commands.add("settings put global show_volume_panel 0")
        commands.add("setprop persist.sys.tcl.volume_panel 0")
        
        val targets = listOf("com.tcl.tguard", "com.tcl.guard", "com.tcl.manager", "com.tcl.tv.manager", "com.tcl.suspension", "com.tcl.pdt.notification.service")
        targets.forEach { pkg ->
            commands.add("cmd notification set_block $pkg 1")
            commands.add("appops set $pkg SYSTEM_ALERT_WINDOW ignore")
            commands.add("am force-stop $pkg")
        }

        return commands
    }

    /**
     * Экстремальное отключение системного мусора и блокировка nvrhung.
     */
    suspend fun applyExtremeDebloat(context: Context) = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext

        Log.i(TAG, "!!! STARTING GHOST PROTOCOL !!!")
        
        // 1. Применяем сверх-конфиг (обман RMS)
        applyGhostResourcePolicy(context)

        // 2. Аппаратная оптимизация через нативное ядро
        NativeBridge.optimizeSystemHardwareNative()

        // 3. Формируем единый список команд для пакетной обработки (быстрее и меньше нагрузки на CPU)
        val purgeCommands = mutableListOf<String>()
        
        // Добавляем критические фиксы
        purgeCommands.addAll(getCriticalFixCommands(context))

        BLOATWARE_PACKAGES.forEach { pkg ->
            purgeCommands.add("pm disable-user --user 0 $pkg")
            purgeCommands.add("am force-stop $pkg")
        }

        // 4. Убиваем nvrhung (HeraEye) и Watchdog
        purgeCommands.addAll(listOf(
            "setprop persist.sys.tcl.abs_state 0",
            "setprop sys.screen.backlight 1",
            "setprop persist.sys.backlight 1",
            "setprop ro.tcl.tguard.enable false",
            "setprop persist.tcl.tguard.enable false",
            "setprop persist.sys.tcl.tguard.enable 0",
            "setprop persist.nvr.ui_probe false,false,false,0",
            "setprop persist.nvr.wtg false",
            "setprop persist.nvr.cpu false",
            "setprop persist.nvr.mem false",
            "setprop debug.nvr.enable 0",
            "setprop persist.sys.nvrhung.enable false",
            "setprop persist.tcl.nvrhung.enable false",
            "settings put global nvrhung_enable 0"
        ))
        
        // 5. Отключаем вендорский контроль ресурсов
        purgeCommands.addAll(listOf(
            "setprop debug.rms.bgworkcontrol 0",
            "setprop persist.sys.rms.enable false",
            "settings put global proc_write_list \"${context.packageName}\""
        ))

        // 6. Очистка системных дампов
        purgeCommands.add("rm -rf /data/system/nvrhung/*")
        purgeCommands.add("rm -rf /data/anr/*")

        // Выполняем всё одним блоком
        Shell.cmd(*purgeCommands.toTypedArray()).submit { result ->
            if (result.isSuccess) {
                Log.i(TAG, "✓ Ghost Protocol successfully deployed in background")
            }
        }
    }

    /**
     * Управляет состоянием пакетов (заморозка/разморозка) через Root.
     */
    suspend fun setPackagesEnabled(packages: List<String>, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext false
        val cmdPrefix = if (enabled) "pm enable" else "pm disable-user --user 0"
        val commands = packages.map { "$cmdPrefix $it" }
        Shell.cmd(*commands.toTypedArray()).exec().isSuccess
    }

    /**
     * Применяет настройки анимации.
     */
    fun applyAnimationTweaks(fast: Boolean) {
        val scale = if (fast) ANIMATION_SCALE_FAST else ANIMATION_SCALE_NORMAL
        Shell.cmd(
            "settings put global window_animation_scale $scale",
            "settings put global transition_animation_scale $scale",
            "settings put global animator_duration_scale $scale"
        ).submit()
    }

    /**
     * Применяет твики производительности (HW Accel и др).
     */
    fun applyPerformanceTweaks(enabled: Boolean) {
        if (enabled) {
            Shell.cmd(
                "setprop debug.performance.tuning 1",
                "setprop video.accelerate.hw 1",
                "setprop persist.sys.ui.hw true"
            ).submit()
        } else {
            Shell.cmd(
                "setprop debug.performance.tuning 0",
                "setprop video.accelerate.hw 0"
            ).submit()
        }
    }

    /**
     * Управляет заморозкой кэшированных приложений (Android 11+).
     */
    fun applyFreezerTweak(enabled: Boolean) {
        if (enabled) {
            Shell.cmd(
                "settings put global cached_apps_freezer enabled",
                "device_config put activity_manager_native_boot use_freezer true",
                "device_config put activity_manager_native_boot freeze_debounce_timeout 1000"
            ).submit()
        } else {
            Shell.cmd(
                "settings put global cached_apps_freezer disabled",
                "device_config put activity_manager_native_boot use_freezer false"
            ).submit()
        }
    }

    /**
     * Оптимизирует планировщик задач (JobScheduler).
     */
    fun applySchedulerTweaks(enabled: Boolean) {
        if (enabled) {
            Shell.cmd(
                "settings put global job_scheduler_constants \"fg_job_count=2,bg_normal_job_count=1,bg_moderate_job_count=1\"",
                "settings put global job_scheduler_quota_controller_constants \"max_job_count_per_rate_limiting_window=5,rate_limiting_window_ms=60000,max_job_count_active=50,max_session_count_active=50\"",
                "settings put global job_scheduler_time_controller_constants \"min_idle_reschedule_ms=30000,min_ready_non_active_reschedule_ms=120000,max_non_active_job_batch_delay_ms=600000\""
            ).submit()
        } else {
            // Сброс к значениям по умолчанию
            Shell.cmd(
                "settings delete global job_scheduler_constants",
                "settings delete global job_scheduler_quota_controller_constants",
                "settings delete global job_scheduler_time_controller_constants"
            ).submit()
        }
    }

    /**
     * Возвращает список всех компонентов, зарегистрированных на автозапуск.
     */
    suspend fun getBootReceivers(): List<String> = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext emptyList()
        val result = Shell.cmd("pm query-receivers --components --query-flags 512 -a android.intent.action.BOOT_COMPLETED").exec()
        if (result.isSuccess) result.out else emptyList()
    }

    /**
     * Включает или выключает конкретный компонент (ресивер/сервис).
     */
    suspend fun setComponentState(component: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext false
        val state = if (enabled) "enable" else "disable"
        val result = Shell.cmd("pm $state '$component'").exec()
        result.isSuccess
    }

    /**
     * Глубокая очистка кэшей и TRIM.
     */
    suspend fun runMaintenance(): Boolean = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext false
        Shell.cmd("am trim-memory all 80").exec()
        Shell.cmd("pm trim-caches 2G").exec()
        Shell.cmd("sync").exec()
        val trimResult = Shell.cmd("fstrim -v /data").exec()
        if (!trimResult.isSuccess) {
            Shell.cmd("vdc volume fstrim").exec()
        }
        true
    }

    /**
     * Выполняет команды с логированием и retry-логикой.
     */
    private suspend fun executeCommandWithRetry(vararg commands: String, silent: Boolean = false): Boolean {
        var attempts = 0
        while (attempts < MAX_RETRY_ATTEMPTS) {
            val result = Shell.cmd(*commands).exec()
            if (result.isSuccess) {
                if (!silent) Log.d(TAG, "✓ Success: ${commands.firstOrNull()?.take(30)}...")
                return true
            }
            attempts++
            if (attempts < MAX_RETRY_ATTEMPTS) {
                delay(RETRY_DELAY_MS.milliseconds)
            }
        }
        return false
    }

    /**
     * Применяет анимационные настройки системы
     */
    private fun applyAnimationSettings() {
        Shell.cmd(
            "settings put global window_animation_scale $ANIMATION_SCALE_NORMAL",
            "settings put global transition_animation_scale $ANIMATION_SCALE_NORMAL",
            "settings put global animator_duration_scale $ANIMATION_SCALE_NORMAL"
        ).submit()
    }

    /**
     * Проверяет, установлена ли уже Deep Night OS.
     */
    fun isAlreadyIntegrated(context: Context): Boolean {
        val isSystemApp = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                          (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        if (isSystemApp) return true

        if (Shell.getCachedShell()?.isRoot == true) {
            val result = Shell.cmd("ls -d /data/adb/modules/$MAGISK_MODULE_ID").exec()
            if (result.isSuccess) return true
        }

        return try {
            NativeBridge.isFileExistsNative("/system/priv-app/DeepNightLauncher/base.apk")
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Проверяет, установлена ли Aptoide TV.
     */
    fun isAptoideInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(APTOIDE_TV_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Управляет состоянием Play Store (заморозка/разморозка).
     */
    suspend fun setPlayStoreEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext false
        val state = if (enabled) "enable" else "disable-user --user 0"
        val result = Shell.cmd("pm $state $PLAY_STORE_PACKAGE").exec()
        result.isSuccess
    }

    fun installAptoideTV(context: Context) {
        scope.launch(Dispatchers.IO) {
            val fileName = "AptoideTV_Latest.apk"
            val destination = File(context.cacheDir, fileName)
            if (destination.exists()) destination.delete()

            SystemInfoRepository.postNotification("Deep Night: Загрузка Aptoide TV...")
            
            var downloaded = false
            for (url in APTOIDE_MIRRORS) {
                try {
                    if (destination.exists()) destination.delete()
                    Log.i(TAG, "Trying to download Aptoide TV from: $url")
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                        
                    DnlNetworkClient.directClient.newCall(request).await().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Mirror $url failed with HTTP ${response.code}")
                            return@use
                        }
                        
                        val body = response.body
                        destination.outputStream().use { output ->
                            body.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }

                    val size = destination.length()
                    Log.i(TAG, "Aptoide TV download attempt from $url complete. Size: $size bytes.")

                    if (size >= 5 * 1024 * 1024) {
                        downloaded = true
                        break
                    } else {
                        Log.w(TAG, "Downloaded file from $url is too small ($size), likely an HTML page")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download Aptoide TV from $url", e)
                }
            }

            if (!downloaded || destination.length() < 5 * 1024 * 1024) {
                SystemInfoRepository.postNotification("Ошибка: Не удалось скачать Aptoide TV со всех зеркал")
                if (destination.exists()) destination.delete()
                return@launch
            }

            try {
                withContext(Dispatchers.Main) {
                    SystemInfoRepository.postNotification("Установка Aptoide TV...")
                    val success = installApkSilent(destination)
                    if (!success) {
                        Log.w(TAG, "Silent install failed, checking for Intent permission")
                        
                        if (!context.packageManager.canRequestPackageInstalls()) {
                             SystemInfoRepository.postNotification("Разрешите установку для лаунчера")
                             val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                 data = "package:${context.packageName}".toUri()
                                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                             }
                             context.startActivity(intent)
                        } else {
                             Log.i(TAG, "Using standard installer via Intent")
                             AppUpdateManager.launchInstallIntentSync(context, destination)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install Aptoide TV", e)
                SystemInfoRepository.postNotification("Ошибка установки: ${e.localizedMessage}")
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private suspend fun applySystemColorsSync() = withContext(Dispatchers.IO) {
        val sdkInt = Build.VERSION.SDK_INT
        val res = android.content.res.Resources.getSystem()
        
        val accentId = res.getIdentifier("accent_device_default_dark", "color", "android")
        val baseColor = if (accentId != 0) {
            try { res.getColor(accentId, null) } catch (_: Exception) { 0xFF00E5FF.toInt() }
        } else 0xFF00E5FF.toInt()

        val nebulaColor = nebularizeColor(baseColor)
        val darkBackground = "0xFF050505" 
        
        val commands = mutableListOf(
            "cmd overlay fabricate --target android --name NebulaAccent android:color/accent_device_default_dark $nebulaColor",
            "cmd overlay enable com.android.shell:NebulaAccent",
            "cmd overlay fabricate --target android --name NebulaBackground android:color/background_device_default_dark $darkBackground",
            "cmd overlay enable com.android.shell:NebulaBackground"
        )

        if (sdkInt >= 31) {
            commands.addAll(listOf(
                "cmd overlay fabricate --target android --name NebulaSys1 --resource android:color/system_accent1_500 --type 0x1c --value $nebulaColor",
                "cmd overlay enable com.android.shell:NebulaSys1"
            ))
        }

        executeCommandWithRetry(*commands.toTypedArray())
    }

    /**
     * Применяет чистый стиль Google TV (отключение вендорских панелей громкости, настройки шрифтов и анимаций).
     */
    suspend fun applyPureSystemStyle(context: Context) = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext
        val commands = listOf(
            "settings put system show_volume_panel 0",
            "settings put global show_volume_panel 0",
            "setprop persist.sys.tcl.volume_panel 0",
            "settings put secure user_setup_complete 1",
            "settings put global transition_animation_scale 1.0",
            "settings put global window_animation_scale 1.0",
            "settings put global animator_duration_scale 1.0"
        )
        Shell.cmd(*commands.toTypedArray()).exec()
        Log.i(TAG, "✓ Pure System Style (AOSP/Google TV look) applied successfully")
    }

// ============ УПРАВЛЕНИЕ СТОКОВЫМИ ЛАУНЧЕРАМИ ============

    private suspend fun getInstalledLaunchers(): List<String> = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext emptyList()
        val result = Shell.cmd("pm list packages | grep -E 'launcher|home'").exec()
        if (!result.isSuccess) return@withContext emptyList()
        result.out.map { line -> line.removePrefix("package:").trim() }.filter { it != "com.deepnight.launcher" }
    }

    suspend fun disableStockLaunchers(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext false
        val launchers = listOf("com.tcl.home", "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher", "com.google.android.tungsten.setupwraith", "com.tcl.tv.tclhome_passive", "com.miui.tv.launcher", "com.sony.dtv.launcher", "com.ugoos.launcher")
        val commands = mutableListOf<String>()
        launchers.forEach { pkg ->
            commands.add("pm disable-user --user 0 $pkg")
            commands.add("am force-stop $pkg")
            commands.add("appops set $pkg RUN_IN_BACKGROUND ignore")
        }
        enforceLauncherPrecedence(context)
        Shell.cmd(*commands.toTypedArray()).exec().isSuccess
    }

    suspend fun restoreStockLaunchers(): Boolean = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext false
        val launchers = getInstalledLaunchers()
        if (launchers.isEmpty()) return@withContext false
        val commands = launchers.map { "pm enable $it" }
        Shell.cmd(*commands.toTypedArray()).exec().isSuccess
    }

    suspend fun resetSystemToDefault() = withContext(Dispatchers.IO) {
        restoreStockLaunchers()
        val commands = listOf("cmd overlay disable com.android.shell:NebulaAccent", "cmd overlay disable com.android.shell:NebulaAccentDark", "cmd package set-home-activity com.android.tv.launcher/.Launcher", "settings put secure accessibility_enabled 0")
        executeCommandWithRetry(*commands.toTypedArray())
    }

    suspend fun safeUnfreezeForUpdate() = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext
        val launchers = getInstalledLaunchers()
        val commands = launchers.map { "pm enable $it" }
        Shell.cmd(*commands.toTypedArray()).exec()
    }

    suspend fun installApkSilent(apkFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext false
        val tmpPath = "/data/local/tmp/update.apk"
        // Используем .exec() вместо .submit() для получения реального результата
        val cpResult = Shell.cmd("cp \"${apkFile.absolutePath}\" $tmpPath", "chmod 644 $tmpPath").exec()
        if (!cpResult.isSuccess) return@withContext false
        
        val installResult = Shell.cmd("pm install -r -d -t --user 0 $tmpPath").exec()
        Shell.cmd("rm $tmpPath").exec()
        
        installResult.isSuccess
    }

    private fun nebularizeColor(color: Int): String {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[1] = 1.0f; hsv[2] = 1.0f
        return String.format(Locale.US, "0x%08X", android.graphics.Color.HSVToColor(hsv))
    }

    private suspend fun generateBootAnimation(context: Context): Boolean = withContext(Dispatchers.IO) {
        val moduleDir = "/data/adb/modules/$MAGISK_MODULE_ID"
        val workDir = File(context.cacheDir, "bootanim_gen")
        try {
            workDir.mkdirs()
            File(workDir, "desc.txt").writeText("1920 1080 24\np 1 0 part0\np 0 0 part0\n")
            val part0Dir = File(workDir, "part0").apply { mkdirs() }
            val width = 1920; val height = 1080
            val bitmap = createBitmap(width, height)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 80f; textAlign = android.graphics.Paint.Align.CENTER; letterSpacing = 0.5f }
            for (i in 0 until 48) {
                canvas.drawColor(android.graphics.Color.BLACK)
                paint.alpha = (((sin(i * Math.PI / 24) * 0.3 + 0.7) * 255).toInt())
                canvas.drawText("DEEP NIGHT", (width / 2).toFloat(), (height / 2).toFloat(), paint)
                val f = File(part0Dir, String.format(Locale.US, "frame_%02d.png", i))
                f.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            val zipFile = File(context.cacheDir, "bootanimation.zip")
            if (Shell.cmd("cd ${workDir.absolutePath} && zip -0r ${zipFile.absolutePath} desc.txt part0").exec().isSuccess) {
                Shell.cmd("cp ${zipFile.absolutePath} $moduleDir/system/media/bootanimation.zip", "chmod 644 $moduleDir/system/media/bootanimation.zip").exec()
                return@withContext true
            }
        } catch (_: Exception) { } finally { workDir.deleteRecursively() }
        return@withContext false
    }

    suspend fun transformToDeepNightOS(context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "▶ Starting Deep Night OS transformation v$OS_VERSION")
        if (!isRoot()) return@withContext false
        return@withContext try {
            applyAnimationSettings()
            applySystemColorsSync() // Визуальная оптимизация
            applyPureSystemStyle(context)
            
            val allCommands = mutableListOf<String>()
            val packageName = context.packageName
            
            allCommands.addAll(getCriticalFixCommands(context))

            allCommands.addAll(listOf(
                "settings put global low_power_trigger_level 0",
                "settings put global stay_on_while_plugged_in 3",
                "settings put global private_dns_mode off",
                "setprop debug.performance.tuning 1",
                "setprop video.accelerate.hw 1",
                "device_config put activity_manager_native_boot use_freezer false",
                "setprop persist.tcl.feature.appStartStats.enable false",
                "dumpsys deviceidle whitelist +$packageName",
                "dumpsys deviceidle whitelist +com.google.android.katniss"
            ))

            allCommands.addAll(listOf(
                "setprop debug.rms.bgworkcontrol 0",
                "setprop persist.sys.nvrhung.enable false",
                "setprop persist.tcl.nvrhung.enable false",
                "setprop persist.nvr.ui_probe false,false,false,0",
                "setprop persist.nvr.wtg false",
                "settings put global nvrhung_enable 0",
                "settings put global proc_write_list \"$packageName\""
            ))

            BLOATWARE_PACKAGES.forEach { pkg ->
                allCommands.add("pm disable-user --user 0 $pkg")
                allCommands.add("am force-stop $pkg")
            }

            NativeBridge.optimizeSystemHardwareNative()
            applyGhostResourcePolicy(context)

            val moduleCreated = setupMagiskModule(context)
            if (moduleCreated) {
                generateBootAnimation(context)
                deployOverlays(context)
            } else {
                integrateDirectlyToSystem(context)
            }

            allCommands.add("cmd package set-home-activity --user 0 $packageName/.MainActivity")
            allCommands.add("fstrim -v /data")
            allCommands.add("sync") 

            Log.i(TAG, "Step 3: Executing super-packet (${allCommands.size} commands)...")
            Shell.cmd(*allCommands.toTypedArray()).exec()

            Log.i(TAG, "✓ Transformation successful. Rebooting in 3s...")
            delay(3000.milliseconds)
            reboot()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Transformation failed", e)
            false
        }
    }

    fun reboot() {
        Shell.cmd("svc power reboot || reboot").submit()
    }

    suspend fun enforceLauncherPrecedence(context: Context) = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext
        if (!File("/data/system/resource_policy.conf").exists() && Build.BRAND.lowercase() == "tcl") {
            applyExtremeDebloat(context)
        }
        val myPkg = context.packageName
        val component = "$myPkg/.MainActivity"
        val commands = mutableListOf(
            "cmd package set-home-activity --user 0 $component",
            "am set-standby-bucket $myPkg active",
            "settings put secure --user 0 user_setup_complete 1",
            "setprop persist.sys.home_package $myPkg",
            "setprop sys.tcl.focuswindow $myPkg"
        )
        Shell.cmd(*commands.toTypedArray()).submit()
    }

    suspend fun trySetDefaultLauncher(context: Context): Boolean = withContext(Dispatchers.IO) {
        val componentName = "${context.packageName}/.MainActivity"
        if (isRoot()) {
            val result = Shell.cmd("cmd package set-home-activity --user 0 $componentName").exec()
            if (result.isSuccess) return@withContext true
        }
        false
    }

    suspend fun reconnectAudio() = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext
        Shell.cmd(
            "settings put system master_mute 0",
            "am broadcast -a android.media.MASTER_MUTE_CHANGED_ACTION --ez state false"
        ).exec()
        Shell.cmd("killall -9 audioserver", "killall -9 mediaserver").exec()
        delay(3000.milliseconds)
        Shell.cmd("cmd audio-server restart", "am broadcast -a com.tcl.intent.action.ABS_EXIT --user 0").exec()
    }

    private suspend fun deployOverlays(context: Context) = withContext(Dispatchers.IO) {
        val overlayAssets = context.assets.list("overlays") ?: return@withContext
        val overlayDir = "/data/adb/modules/$MAGISK_MODULE_ID/system/overlay"
        executeCommandWithRetry("mkdir -p $overlayDir")
        overlayAssets.forEach { name ->
            if (name.endsWith(".apk")) {
                val assetPath = "overlays/$name"
                val destPath = "$overlayDir/$name"
                if (copyAssetToFile(context, assetPath, destPath)) {
                    executeCommandWithRetry("chmod 644 $destPath")
                }
            }
        }
    }

    private fun copyAssetToFile(context: Context, assetPath: String, destPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use { input ->
                val tempFile = File(context.cacheDir, "temp_asset")
                tempFile.outputStream().use { output -> input.copyTo(output) }
                val result = Shell.cmd("cp \"${tempFile.absolutePath}\" \"$destPath\"").exec()
                tempFile.delete()
                result.isSuccess
            }
        } catch (_: Exception) { false }
    }

    private fun writeTextToRootFile(context: Context, content: String, destPath: String): Boolean {
        return try {
            val tempFile = File(context.cacheDir, "temp_config")
            tempFile.writeText(content)
            val result = Shell.cmd("cp \"${tempFile.absolutePath}\" \"$destPath\"").exec()
            tempFile.delete()
            result.isSuccess
        } catch (_: Exception) { false }
    }

    private fun setupMagiskModule(context: Context): Boolean {
        val moduleDir = "/data/adb/modules/$MAGISK_MODULE_ID"
        val apkPath = context.packageCodePath
        val packageName = context.packageName
        
        Log.i(TAG, "Building advanced Magisk module at $moduleDir")
        
        Shell.cmd(
            "rm -rf $moduleDir", 
            "mkdir -p $moduleDir/system/priv-app/DeepNightLauncher",
            "mkdir -p $moduleDir/system/media",
            "mkdir -p $moduleDir/system/etc/permissions",
            "mkdir -p $moduleDir/system/etc/sysconfig"
        ).exec()
        
        // 1. Копируем лаунчер в системный раздел
        if (!Shell.cmd("cp \"$apkPath\" \"$moduleDir/system/priv-app/DeepNightLauncher/base.apk\"").exec().isSuccess) return false
        
        // 2. Системные разрешения и белый список (Whitelisting)
        val permissionsXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <permissions>
                <privapp-permissions package="$packageName">
                    <permission name="android.permission.INSTALL_PACKAGES"/>
                    <permission name="android.permission.DELETE_PACKAGES"/>
                    <permission name="android.permission.REBOOT"/>
                    <permission name="android.permission.PACKAGE_USAGE_STATS"/>
                    <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
                    <permission name="android.permission.START_ACTIVITIES_FROM_BACKGROUND"/>
                    <permission name="android.permission.INTERACT_ACROSS_USERS"/>
                </privapp-permissions>
            </permissions>
        """.trimIndent()
        writeTextToRootFile(context, permissionsXml, "$moduleDir/system/etc/permissions/privapp-permissions-$packageName.xml")

        val sysConfigXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <config>
                <allow-in-power-save package="$packageName" />
                <allow-in-data-usage-save package="$packageName" />
                <allow-unthrottled-location package="$packageName" />
            </config>
        """.trimIndent()
        writeTextToRootFile(context, sysConfigXml, "$moduleDir/system/etc/sysconfig/deepnight-whitelist.xml")

        // 3. system.prop - Магия взлома TCL на уровне ядра (до загрузки UI)
        val props = """
            # Deep Night OS: TCL Hardware Hacks
            persist.sys.tcl.abs_state=0
            persist.rtk.screenoff=0
            sys.screen.backlight=1
            persist.sys.backlight=1
            persist.sys.tcl.black_screen_state=0
            
            # ОТКЛЮЧЕНИЕ ГВАРДОВ (для тишины в system_server)
            ro.tcl.tguard.enable=false
            ro.tcl.guard.enable=false
            persist.tcl.tguard.enable=false
            persist.sys.tcl.tguard.enable=0
            persist.sys.tcl.guard.enable=0
            persist.sys.tcl.rms.enable=0
            
            # Отключение убийцы процессов TCL
            ro.tcl.resource.manager.enable=false
            persist.sys.rms.enable=false
            debug.rms.bgworkcontrol=0
            
            # Отключение слежки и логов (HeraEye / nvrhung)
            persist.sys.nvrhung.enable=false
            persist.tcl.nvrhung.enable=false
            persist.nvr.ui_probe=false,false,false,0
            persist.nvr.wtg=false
            persist.nvr.cpu=false
            persist.nvr.mem=false
            debug.nvr.enable=0
            
            # Сеть
            ro.ril.wake_lock_timeout=300
            
            # Системный приоритет
            persist.sys.home_package=$packageName
            sys.tcl.focuswindow=$packageName
        """.trimIndent()
        writeTextToRootFile(context, props, "$moduleDir/system.prop")

        // 4. module.prop
        val moduleProp = "id=$MAGISK_MODULE_ID\nname=Deep Night OS Core\nversion=$OS_VERSION\nauthor=DeepNight\ndescription=Deep Night OS: Total TCL System Override. Fixes Black Screen, removes bloat, and sets launcher priority.\n"
        writeTextToRootFile(context, moduleProp, "$moduleDir/module.prop")

        // 5. service.sh - Скрипт, который "добивает" остатки TCL после загрузки
        val debloatCmds = BLOATWARE_PACKAGES.joinToString("\n") { "pm disable-user --user 0 $it" }
        val serviceScript = """
            #!/system/bin/sh
            # Ждем полной загрузки
            while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 5; done
            
            # Внедрение "Призрачной политики" v999
            cat <<CONF > /data/system/resource_policy.conf
            {
                "version": 999,
                "sub_version": 999,
                "timestamp": 2147483647,
                "enable": true,
                "memsettings": [{
                    "memlevel": { "normal": 0, "urgent": 0 },
                    "bgService_enable": false,
                    "memCheck_enable": false,
                    "adj_whitelist": ["$packageName", "com.google.android.katniss", "org.smarttube.stable", "ru.yourok.torrserve", "com.lampa.app", "top.rootu.lamps"],
                    "top_use_list": ["$packageName", "com.netflix.ninja", "com.google.android.youtube.tv"],
                    "ui_services": ["$packageName", "com.android.systemui"]
                }],
                "processStart": {
                    "enable": true,
                    "white_list": ["*"],
                    "black_list": ["com.tcl.tguard", "com.tcl.manager"]
                }
            }
            CONF
            chmod 644 /data/system/resource_policy.conf
            chown system:system /data/system/resource_policy.conf
            
            # Устанавливаем лаунчер как главный
            cmd package set-home-activity --user 0 $packageName/.MainActivity
            am set-standby-bucket $packageName active
            
            # Применяем настройки черного экрана (Double Check)
            settings put system black_screen_state 0
            settings put system tcl_abs_state 0
            settings put system screen_brightness 255
            settings put system listen_alone_mode 0
            
            # Сигнал на выход из режима ABS
            am broadcast -a com.tcl.intent.action.ABS_EXIT --user 0
            am broadcast -a com.tcl.blackscreen.EXIT --user 0
            am broadcast -a com.tcl.intent.action.SCREEN_ON --user 0
            
            # Глобальный Debloat и принудительная остановка
            $debloatCmds
            am force-stop com.tcl.tguard
            am force-stop com.tcl.manager
            
            # Оптимизация памяти
            echo 999 > /proc/sys/vm/pipe-max-size
            
            # Разрешения для лаунчера
            pm grant $packageName android.permission.RECORD_AUDIO
        """.trimIndent()
        writeTextToRootFile(context, serviceScript, "$moduleDir/service.sh")

        // Устанавливаем права доступа
        Shell.cmd(
            "chown -R root:root $moduleDir", 
            "chmod -R 755 $moduleDir", 
            "chmod 644 $moduleDir/module.prop",
            "chmod 644 $moduleDir/system.prop",
            "chmod 644 $moduleDir/system/priv-app/DeepNightLauncher/base.apk", 
            "chmod 755 $moduleDir/service.sh", 
            "chcon -R u:object_r:system_file:s0 $moduleDir/system", 
            "chcon u:object_r:magisk_file:s0 $moduleDir/module.prop", 
            "chcon u:object_r:magisk_file:s0 $moduleDir/service.sh"
        ).exec()
        
        return true
    }

    suspend fun integrateDirectlyToSystem(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext false
        val apkPath = context.packageCodePath
        val destDir = "/system/priv-app/DeepNightLauncher"
        val destApk = "$destDir/DeepNightLauncher.apk"
        Shell.cmd("mount -o remount,rw /system", "mkdir -p $destDir", "cp \"$apkPath\" $destApk", "chmod 755 $destDir", "chmod 644 $destApk", "chown root:root $destApk", "sync").exec().isSuccess
    }

    private suspend fun patchResourcePolicy(context: Context) = withContext(Dispatchers.IO) {
        val vendorPath = "/vendor/etc/resource_policy.conf"
        val userPath = "/data/system/resource_policy.conf"
        val packageName = context.packageName
        try {
            val result = Shell.cmd("cat $vendorPath").exec()
            if (!result.isSuccess || result.out.isEmpty()) return@withContext
            var content = result.out.joinToString("\n")
            if (content.contains(packageName)) return@withContext 
            val sections = listOf("\"adj_whitelist\":[" to "\"", "\"top_use_list\":[" to "\"", "\"white_list\":[" to "\"", "\"appWhiteList\":[" to "\"", "\"service_restart_whitelist_important\":[" to "\"", "\"ui_services\":[" to "\"", "\"bgServiceList\":[" to "\"", "\"specialBgServiceList\":[" to "\"")
            sections.forEach { (key, quote) -> if (content.contains(key)) { val emptyKey = key.replace("[", "[]"); content = if (content.contains(emptyKey)) { content.replace(emptyKey, "$key$quote$packageName$quote]") } else { content.replace(key, "$key$quote$packageName$quote,") } } }
            if (content != result.out.joinToString("\n")) { writeTextToRootFile(context, content, userPath); Shell.cmd("chmod 644 $userPath", "chown system:system $userPath").exec() }
        } catch (_: Exception) {}
    }

    suspend fun postUpdateCleanup(context: Context) = withContext(Dispatchers.IO) {
        if (!isRoot()) return@withContext
        if (isAlreadyIntegrated(context)) {
            disableStockLaunchers(context)
            applyExtremeDebloat(context)
            if (Build.BRAND.lowercase() == "tcl") patchResourcePolicy(context)
        }
        enforceLauncherPrecedence(context)
    }
}
