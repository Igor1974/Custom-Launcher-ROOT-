package com.deepnight.launcher

import android.service.dreams.DreamService
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.deepnight.launcher.ui.theme.CustomLauncherRootTheme
import com.deepnight.launcher.visualizer.AudioVisualizerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope

/**
 * Системный сервис скринсейвера для Deep Night OS.
 * Обеспечивает работу "живых" обоев и инфо-панели в режиме ожидания.
 */
class DeepNightScreensaverService : DreamService() {

    private val statsFlow = MutableStateFlow<SystemStats?>(null)
    private var visualizerManager: AudioVisualizerManager? = null
    private val scope = MainScope()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isInteractive = true
        isFullscreen = true

        // Инициализируем визуализатор (глобальный микс)
        visualizerManager = AudioVisualizerManager(0)

        val composeView = ComposeView(this).apply {
            setContent {
                val stats by statsFlow.collectAsState()
                val spectrumState = visualizerManager?.spectrum?.collectAsState(initial = FloatArray(0))
                val spectrum = spectrumState?.value ?: FloatArray(0)
                val wallpaperUrl = "" // Можно добавить загрузку из настроек

                CustomLauncherRootTheme {
                    val saverType = LauncherSettings.getScreensaverType(context)
                    
                    if (saverType == "AERIAL" || saverType == "LOCAL") {
                        AerialDreamScreensaver(
                            stats = stats,
                            fftData = spectrum,
                            isLocalOnly = (saverType == "LOCAL"),
                            onDismiss = { finish() }
                        )
                    } else {
                        DeepNightScreensaver(
                            wallpaperUrl = wallpaperUrl,
                            stats = stats,
                            fftData = spectrum,
                            onDismiss = { finish() }
                        )
                    }
                }
            }
        }

        // Необходимо для работы Compose в Service
        val lifecycleOwner = MyLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        
        val viewModelStoreOwner = MyViewModelStoreOwner()
        composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
        
        val savedStateRegistryOwner = MySavedStateRegistryOwner()
        savedStateRegistryOwner.performRestore(null)
        composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

        setContentView(composeView)

        // Фоновое обновление статистики
        scope.launch {
            while (true) {
                statsFlow.value = SystemInfoRepository.fetchFullStats(this@DeepNightScreensaverService)
                kotlinx.coroutines.delay(15000)
            }
        }
    }

    override fun onDetachedFromWindow() {
        visualizerManager?.release()
        visualizerManager = null
        super.onDetachedFromWindow()
    }

    private class MyLifecycleOwner : androidx.lifecycle.LifecycleOwner {
        private val registry = androidx.lifecycle.LifecycleRegistry(this)
        override val lifecycle = registry
        fun handleLifecycleEvent(event: androidx.lifecycle.Lifecycle.Event) = registry.handleLifecycleEvent(event)
    }

    private class MyViewModelStoreOwner : androidx.lifecycle.ViewModelStoreOwner {
        private val store = androidx.lifecycle.ViewModelStore()
        override val viewModelStore = store
    }

    private class MySavedStateRegistryOwner : androidx.savedstate.SavedStateRegistryOwner {
        private val registry = androidx.savedstate.SavedStateRegistryController.create(this)
        override val savedStateRegistry = registry.savedStateRegistry
        private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
        override val lifecycle = lifecycleRegistry
        fun performRestore(state: android.os.Bundle?) = registry.performRestore(state)
    }
}
