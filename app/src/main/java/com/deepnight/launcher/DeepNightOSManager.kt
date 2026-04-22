package com.deepnight.launcher

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Менеджер Deep Night OS Core v2.6.
 * Обеспечивает глубокую системную интеграцию и модификацию ресурсов "на лету".
 */
object DeepNightOSManager {
    private const val TAG = "DeepNightOS"

    private val BLOATWARE_PACKAGES = listOf(
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher"
    )

    /**
     * Проверяет, установлена ли уже Deep Night OS.
     */
    fun isAlreadyIntegrated(): Boolean {
        return File("/system/priv-app/DeepNightLauncher/DeepNightLauncher.apk").exists() ||
               File("/data/adb/modules/deep_night_os").exists()
    }

    /**
     * Применяет критические исправления для работы в режиме "Home".
     */
    suspend fun applyCriticalFixes(context: Context) = withContext(Dispatchers.IO) {
        if (Shell.isAppGrantedRoot() != true) return@withContext
        
        val packageName = context.packageName
        val serviceId = "$packageName/.HomeInterceptorService"
        val componentName = "$packageName/.MainActivity"
        
        Shell.cmd(
            "appops set $packageName SYSTEM_ALERT_WINDOW allow",
            "pm grant $packageName android.permission.POST_NOTIFICATIONS",
            "pm grant $packageName android.permission.RECORD_AUDIO",
            "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
            "settings put global transition_animation_scale 0.5",
            "settings put global window_animation_scale 0.5",
            "settings put global animator_duration_scale 0.5",
            "settings put secure enabled_accessibility_services $serviceId",
            "settings put secure accessibility_enabled 1",
            "settings put secure assistant $componentName",
            "settings put secure voice_interaction_service $componentName",
            "cmd package set-home-activity --user 0 $componentName",
            "settings put secure last_setup_shown 1",
            "settings put secure user_setup_complete 1",
            "am set-standby-bucket $packageName active"
        ).exec()
    }

    /**
     * Сливает ключевые системные файлы и анализирует их.
     */
    suspend fun dumpAndAnalyzeSystem(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, String>()
        if (Shell.isAppGrantedRoot() != true) return@withContext results
        
        val dumpDir = File(context.getExternalFilesDir(null), "DeepNight_Dump")
        if (!dumpDir.exists()) dumpDir.mkdirs()
        
        val buildProp = File("/system/build.prop")
        if (buildProp.exists()) {
            val dest = File(dumpDir, "build.prop")
            Shell.cmd("cp ${buildProp.absolutePath} ${dest.absolutePath}").exec()
            
            // Анализ build.prop
            dest.readLines().forEach { line ->
                when {
                    line.startsWith("ro.product.brand=") -> results["brand"] = line.substringAfter("=")
                    line.startsWith("ro.product.model=") -> results["model"] = line.substringAfter("=")
                    line.startsWith("ro.build.version.release=") -> results["android_ver"] = line.substringAfter("=")
                    line.startsWith("ro.build.display.id=") -> results["firmware"] = line.substringAfter("=")
                }
            }
        }

        val filesToDump = listOf("/system/framework/framework-res.apk")
        filesToDump.forEach { path ->
            val file = File(path)
            if (file.exists()) {
                Shell.cmd("cp $path ${dumpDir.absolutePath}/${file.name}").exec()
            }
        }

        val findSystemUi = Shell.cmd("find /system -name SystemUI.apk").exec().out.firstOrNull()
        if (findSystemUi != null) {
            Shell.cmd("cp $findSystemUi ${dumpDir.absolutePath}/SystemUI.apk").exec()
        }

        results
    }

    /**
     * Применяет специфичные для вендора твики (прозрачность, скрытие статус-бара).
     */
    suspend fun applyVendorTweaks(brand: String?) = withContext(Dispatchers.IO) {
        when (brand?.lowercase()) {
            "tcl" -> {
                // Твики для TCL
                Shell.cmd("settings put global policy_control immersive.full=*").exec()
                Shell.cmd("settings put system tcl_screen_saver_timeout 0").exec() // Отключаем стоковый скринсейвер TCL
                // Убеждаемся, что системные компоненты поиска доступны для РАБОТЫ (библиотеки),
                // но отключаем их Activity-обработчики
                Shell.cmd("pm enable com.tcl.assistant").exec()
                Shell.cmd("pm disable com.tcl.assistant/.MainActivity").exec()
                Shell.cmd("pm enable com.google.android.katniss").exec()
                Shell.cmd("pm disable com.google.android.katniss/.search.SearchActivity").exec()
                Shell.cmd("pm disable com.google.android.katniss/.search.VoiceSearchActivity").exec()
            }
            "sony" -> {
                Shell.cmd("settings put global policy_control immersive.status=*").exec()
                Shell.cmd("pm enable com.google.android.katniss").exec()
                Shell.cmd("pm disable com.google.android.katniss/.search.SearchActivity").exec()
            }
            else -> {
                Shell.cmd("pm enable com.google.android.katniss").exec()
                Shell.cmd("pm disable com.google.android.katniss/.search.SearchActivity").exec()
            }
        }
    }

    suspend fun enableVoiceEngine(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (Shell.isAppGrantedRoot() != true) return@withContext false
        
        // Список пакетов в порядке приоритета
        val packages = listOf(
            "com.tcl.assistant",
            "com.google.android.tv.assistant",
            "com.google.android.katniss",
            "com.google.android.googlequicksearchbox"
        )

        for (pkg in packages) {
            val check = Shell.cmd("pm list packages $pkg").exec()
            if (check.isSuccess && check.out.isNotEmpty()) {
                // Включаем пакет
                Shell.cmd("pm enable $pkg").exec()
                
                // Ищем внутри пакета сервис
                val candidates = when(pkg) {
                    "com.tcl.assistant" -> listOf("$pkg/.VoiceService", "$pkg/com.tcl.assistant.VoiceService")
                    "com.google.android.tv.assistant" -> listOf(
                        "$pkg/com.google.android.apps.assistant.service.AssistantRecognitionService",
                        "$pkg/com.google.android.apps.assistant.service.VoiceInteractionService"
                    )
                    "com.google.android.katniss" -> listOf(
                        "$pkg/com.google.android.katniss.VoiceRegistrationService",
                        "$pkg/com.google.android.katniss.RecognitionService"
                    )
                    else -> listOf(
                        "$pkg/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
                    )
                }

                for (comp in candidates) {
                    Shell.cmd("settings put secure voice_recognition_service $comp").exec()
                    // Также пробуем установить ассистента (для системной кнопки)
                    if (pkg.contains("assistant")) {
                        Shell.cmd("settings put secure assistant $comp").exec()
                    }
                    Log.i(TAG, "Voice recognition service set to: $comp")
                }
                return@withContext true
            }
        }
        
        false
    }

    /**
     * Превращает обычный цвет в "неоновый" путем максимизации насыщенности и яркости.
     */
    private fun neonizeColor(color: Int): String {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[1] = 1.0f // Максимальная насыщенность
        hsv[2] = 1.0f // Максимальная яркость
        val neonColor = android.graphics.Color.HSVToColor(hsv)
        return "0x${Integer.toHexString(neonColor).uppercase()}"
    }

    /**
     * Синхронизация системных цветов через Fabricated Overlays.
     */
    private suspend fun applySystemColorsSync(context: Context, androidVersion: String?) = withContext(Dispatchers.IO) {
        val version = androidVersion?.split(".")?.firstOrNull()?.toIntOrNull() ?: 0
        Log.i(TAG, "Syncing system colors for Android $version...")
        
        // Пытаемся достать текущий системный акцент
        val res = android.content.res.Resources.getSystem()
        val accentId = res.getIdentifier("accent_device_default_dark", "color", "android")
        val baseColor = if (accentId != 0) res.getColor(accentId, null) else 0xFF00E5FF.toInt()
        
        val neonColor = neonizeColor(baseColor)
        Log.i(TAG, "Target Neon Color: $neonColor")
        
        // Попытка применить Fabricated Overlays (даже на 11, вдруг вендор портировал)
        val commands = mutableListOf<String>()
        
        // Основные акценты
        commands.add("cmd overlay fabricate --target android --name DeepNightAccent android:color/accent_device_default_light $neonColor")
        commands.add("cmd overlay fabricate --target android --name DeepNightAccentDark android:color/accent_device_default_dark $neonColor")
        
        // Для Android 12+ добавляем системные палитры
        if (version >= 12) {
            commands.add("cmd overlay fabricate --target android --name DeepNightSys1 --resource android:color/system_accent1_500 --type 0x1c --value $neonColor")
            commands.add("cmd overlay fabricate --target android --name DeepNightSys2 --resource android:color/system_accent2_500 --type 0x1c --value $neonColor")
            commands.add("cmd overlay fabricate --target android --name DeepNightSys3 --resource android:color/system_accent3_500 --type 0x1c --value $neonColor")
            commands.add("cmd overlay fabricate --target android --name DeepNightNeu1 --resource android:color/system_neutral1_500 --type 0x1c --value 0xFF1A1C1E")
            commands.add("cmd overlay fabricate --target android --name DeepNightNeu2 --resource android:color/system_neutral2_500 --type 0x1c --value 0xFF1A1C1E")
        }

        // Включаем всё, что создали
        commands.add("cmd overlay enable com.android.shell:DeepNightAccent")
        commands.add("cmd overlay enable com.android.shell:DeepNightAccentDark")
        if (version >= 12) {
            commands.add("cmd overlay enable com.android.shell:DeepNightSys1")
            commands.add("cmd overlay enable com.android.shell:DeepNightSys2")
            commands.add("cmd overlay enable com.android.shell:DeepNightSys3")
            commands.add("cmd overlay enable com.android.shell:DeepNightNeu1")
            commands.add("cmd overlay enable com.android.shell:DeepNightNeu2")
        }

        // TCL-специфичный хак для цветов (если есть)
        if (version < 12) {
            commands.add("settings put system system_accent_color $neonColor")
        }

        Shell.cmd(*commands.toTypedArray()).exec()
    }

    /**
     * Глубокая очистка и оптимизация FS.
     */
    private suspend fun optimizeSystemPerformance() = withContext(Dispatchers.IO) {
        Shell.cmd(
            "fstrim -v /system",
            "fstrim -v /data",
            "fstrim -v /cache",
            "pm compile -m speed-profile -a" // Перекомпиляция всех приложений под максимальную скорость
        ).exec()
    }

    /**
     * Основной процесс трансформации в Deep Night OS.
     */
    suspend fun transformToDeepNightOS(context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting automatic Deep Night OS transformation...")
        
        if (Shell.isAppGrantedRoot() != true) {
            Log.e(TAG, "Root access not granted!")
            return@withContext false
        }

        try {
            // 1. Сбор информации о системе
            val systemInfo = dumpAndAnalyzeSystem(context)
            val brand = systemInfo["brand"]
            val androidVer = systemInfo["android_ver"]
            Log.i(TAG, "Detected system: $brand (Android $androidVer)")

            // 2. Оптимизация анимаций
            Shell.cmd(
                "settings put global window_animation_scale 0.5",
                "settings put global transition_animation_scale 0.5",
                "settings put global animator_duration_scale 0.5"
            ).exec()

            // 3. Отключение bloatware
            BLOATWARE_PACKAGES.forEach { pkg ->
                Shell.cmd("pm disable-user --user 0 $pkg", "am force-stop $pkg").exec()
            }
            
            // 3.1. Убеждаемся, что Katniss/Assistant включены (нужны для распознавания голоса)
            Shell.cmd("pm enable com.google.android.katniss").exec()
            Shell.cmd("pm enable com.google.android.tv.assistant").exec()

            // 4. Вендорские патчи
            applyVendorTweaks(brand)

            // 5. Динамическая перекраска системы (RRO/Fabricated)
            applySystemColorsSync(context, androidVer)

            // 6. Создание Magisk модуля (включая Bootanimation)
            val moduleCreated = setupMagiskModule(context)

            // 7. Установка лаунчера по умолчанию
            val packageName = context.packageName
            Shell.cmd("cmd package set-home-activity --user 0 $packageName/.MainActivity").exec()

            // 8. Развертывание статических RRO Оверлеев (если есть)
            deployOverlays(context)
            
            // 9. Регистрация системных сервисов (Скринсейвер)
            registerDeepNightServices(context)
            
            // 10. Финальная оптимизация производительности
            optimizeSystemPerformance()

            Log.i(TAG, "Transformation successful!")
            
            if (moduleCreated) {
                Log.i(TAG, "Magisk module created. Scheduling reboot in 3s...")
                delay(3000)
                // Пытаемся перезагрузить разными способами
                Shell.cmd("reboot").exec() 
                Shell.cmd("svc power reboot").exec()
                Shell.cmd("setprop sys.powerctl reboot").exec()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Transformation failed", e)
            false
        }
    }

    /**
     * Регистрация лаунчера как системного скринсейвера и других сервисов.
     */
    private fun registerDeepNightServices(context: Context) {
        val packageName = context.packageName
        val screensaverComponent = "$packageName/.DeepNightScreensaverService"
        
        Shell.cmd(
            "settings put secure screensaver_enabled 1",
            "settings put secure screensaver_components $screensaverComponent",
            "settings put secure screensaver_activate_on_sleep 1",
            "settings put secure screensaver_activate_on_dock 1",
            "settings put secure sleep_timeout 600000" // 10 минут
        ).exec()
    }

    /**
     * Развертывание RRO-оверлеев для изменения системного стиля.
     */
    private suspend fun deployOverlays(context: Context) = withContext(Dispatchers.IO) {
        val moduleDir = "/data/adb/modules/deep_night_os"
        val overlayDir = "$moduleDir/system/overlay"
        Shell.cmd("mkdir -p $overlayDir").exec()
        
        // Список оверлеев для переноса из ассетов
        val overlays = listOf("systemui_neon.apk", "framework_res_neon.apk")
        
        overlays.forEach { name ->
            val assetPath = "overlays/$name"
            val destPath = "$overlayDir/$name"
            
            if (copyAssetToFile(context, assetPath, destPath)) {
                Shell.cmd("chmod 644 $destPath").exec()
                Log.i(TAG, "Overlay deployed: $name")
            }
        }
    }

    private fun copyAssetToFile(context: Context, assetPath: String, destPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use { input ->
                val tempFile = File(context.cacheDir, "temp_asset")
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                val result = Shell.cmd("cp ${tempFile.absolutePath} $destPath").exec()
                tempFile.delete()
                result.isSuccess
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun setupMagiskModule(context: Context): Boolean {
        val moduleDir = "/data/adb/modules/deep_night_os"
        val apkPath = context.packageCodePath
        val packageName = context.packageName

        Log.i(TAG, "Creating Magisk module at $moduleDir...")

        // Очистка и создание структуры
        Shell.cmd(
            "rm -rf $moduleDir",
            "mkdir -p $moduleDir/system/priv-app/DeepNightLauncher",
            "cp $apkPath $moduleDir/system/priv-app/DeepNightLauncher/DeepNightLauncher.apk",
            "chmod 644 $moduleDir/system/priv-app/DeepNightLauncher/DeepNightLauncher.apk",
            "mkdir -p $moduleDir/system/media",
            "touch $moduleDir/auto_mount"
        ).exec()

        // Создание module.prop через cat (более надежно)
        val moduleProp = """
            id=deep_night_os
            name=Deep Night OS Core
            version=2.6
            versionCode=4
            author=DeepNight
            description=Системный приоритет, RRO Overlays и кастомный стиль для Deep Night OS.
        """.trimIndent()
        
        Shell.cmd("echo \"$moduleProp\" > $moduleDir/module.prop").exec()

        // Создание service.sh через Heredoc, чтобы избежать проблем с кавычками
        val serviceCommand = """
            cat << 'EOF' > $moduleDir/service.sh
            #!/system/bin/sh
            while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 5; done
            
            # Принудительная установка лаунчера дефолтным
            cmd package set-home-activity --user 0 $packageName/.MainActivity
            
            # Даем права
            pm grant $packageName android.permission.WRITE_SECURE_SETTINGS
            pm grant $packageName android.permission.RECORD_AUDIO
            appops set $packageName SYSTEM_ALERT_WINDOW allow
            
            # Включаем оверлеи
            for overlay in $(cmd overlay list | grep -i deepnight | awk '{print $2}'); do
                cmd overlay enable ${"$"}{overlay}
            done
            EOF
        """.trimIndent()

        Shell.cmd(serviceCommand).exec()
        Shell.cmd("chmod 755 $moduleDir/service.sh").exec()
        Shell.cmd("chown -R root:root $moduleDir").exec()

        // Проверка создания
        val check = Shell.cmd("ls $moduleDir/module.prop").exec()
        Log.i(TAG, "Module check: ${check.isSuccess}")
        
        return check.isSuccess
    }
}
