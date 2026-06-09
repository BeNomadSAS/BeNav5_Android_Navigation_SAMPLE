package com.benomad.sample.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benomad.sample.sdk.MapDownloadProgress
import com.benomad.sample.sdk.SdkInitializer
import com.benomad.sample.sdk.SdkProvider
import com.benomad.sample.sdk.readableMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives SDK initialization on the splash screen: it registers the map-download
 * observers, runs [SdkInitializer.initialize] (the license / LBO call), and exposes a
 * single [UiState] combining the init phase with live map-download progress.
 */
class SplashViewModel(private val sdk: SdkProvider) : ViewModel() {

    enum class Phase { Initializing, Ready, Failed }

    data class UiState(
        val phase: Phase = Phase.Initializing,
        val mapProgress: MapDownloadProgress = MapDownloadProgress.Idle,
        val errorMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var started = false
    private var initJob: Job? = null

    init {
        // Mirror map-download progress into the UI for the whole VM lifetime.
        viewModelScope.launch {
            sdk.mapDataController.progress.collect { progress ->
                _uiState.update { it.copy(mapProgress = progress) }
            }
        }
    }

    /** Called once when the splash screen appears. */
    fun start() {
        if (started) return
        started = true
        // Register BEFORE Core.init so a first-launch (FULL) download is visible. The `started`
        // guard already ensures this runs exactly once.
        sdk.mapDataController.registerDownloadObservers()
        runInitialization()
    }

    /**
     * Re-attempts initialization after a failure (e.g. the user fixed connectivity).
     * It intentionally does not reset [started]; it re-runs init directly, bypassing the
     * once-only guard that protects [start] against recomposition.
     */
    fun retry() {
        _uiState.update { UiState() }
        // Cancel any still-running attempt so its result can't overwrite the reset state.
        initJob?.cancel()
        runInitialization()
    }

    private fun runInitialization() {
        val prefs = sdk.onboardingPreferences
        initJob = viewModelScope.launch {
            when (val result = sdk.sdkInitializer.initialize(sdk.appContext, prefs.purchaseUuid)) {
                is SdkInitializer.InitResult.Success -> {
                    // Core is up: read the resolved map mode and attach runtime observers.
                    sdk.mapDataController.onCoreReady()
                    // Load the map style chart (deployed by Core.init) before showing the map.
                    val styleError = sdk.mapStyleProvider.load(sdk.appContext)
                    if (styleError != null) {
                        _uiState.update { it.copy(phase = Phase.Failed, errorMessage = styleError.readableMessage()) }
                    } else {
                        _uiState.update { it.copy(phase = Phase.Ready) }
                    }
                }

                is SdkInitializer.InitResult.Failure -> {
                    _uiState.update { it.copy(phase = Phase.Failed, errorMessage = result.message) }
                }
            }
        }
    }
}
