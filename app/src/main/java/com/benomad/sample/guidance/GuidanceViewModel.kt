package com.benomad.sample.guidance

import android.graphics.Bitmap
import androidx.annotation.MainThread
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benomad.msdk.navigation.listener.ArrivalListener
import com.benomad.msdk.navigation.listener.InstructionsListener
import com.benomad.msdk.navigation.listener.NavigationProgressListener
import com.benomad.msdk.navigation.progress.GuidanceProgress
import com.benomad.msdk.navigation.progress.NavigationProgressState
import com.benomad.msdk.navigation.progress.TrackingProgress
import com.benomad.sample.sdk.SdkProvider
import com.benomad.sample.sdk.etaFromNow
import com.benomad.sample.sdk.formatDistance
import com.benomad.sample.sdk.formatDuration
import com.benomad.sample.sdk.formatSteppedDistance
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the guidance screen. It registers the three Navigation listeners, starts guidance for
 * the route handed over from the route preview ([SdkProvider.pendingRoute]/[SdkProvider.pendingDemoMode]),
 * and maps the callbacks into a [GuidanceUiState].
 *
 * All Navigation listener callbacks are delivered on the main thread, so the [StateFlow] is
 * updated directly from them. Voice guidance is spoken by the engine itself — we never
 * implement `onNewVocalInstruction`; we only override `onNewInstruction` for the visual icon/text.
 */
class GuidanceViewModel(private val sdk: SdkProvider) : ViewModel() {

    private val _uiState = MutableStateFlow(GuidanceUiState())
    val uiState: StateFlow<GuidanceUiState> = _uiState.asStateFlow()

    // Confined to the main thread by their call sites (Compose onReady / Stop button / BackHandler),
    // so plain Booleans are safe here — no synchronization needed.
    private var started = false
    private var stopping = false

    private val progressListener = object : NavigationProgressListener {
        override fun onNavigationProgressChanged(navigationProgress: TrackingProgress) {
            // In GUIDANCE the progress is a GuidanceProgress carrying the route distances/ETA.
            val guidance = navigationProgress as? GuidanceProgress
            _uiState.update {
                it.copy(
                    isReady = navigationProgress.state in READY_STATES,
                    // Stepped (coarse-bucketed) so the maneuver distance updates incrementally, not every metre.
                    distanceToNextText = guidance?.distanceToNextInstruction?.let(::formatSteppedDistance),
                    distanceToDestText = guidance?.distanceToDest?.let(::formatDistance),
                    durationToDestText = guidance?.drivingDurationToDest?.let { s -> formatDuration(s.toLong()) },
                    etaText = guidance?.drivingDurationToDest?.let(::etaFromNow),
                    speedLimitText = navigationProgress.currentSpeedLimit.takeIf { it > 0.0 }?.let { "${it.roundToInt()}" },
                )
            }
        }
    }

    private val instructionsListener = object : InstructionsListener {
        // Bitmaps are non-null only because startSession was given InstructionsIconsStyles.
        override fun onNewInstruction(
            instruction: String,
            maneuver: Bitmap?,
            chainedManeuver: Bitmap?,
            signpost: Bitmap?,
            laneInfo: Bitmap?,
        ) {
            _uiState.update { it.copy(instruction = instruction, maneuverIcon = maneuver?.asImageBitmap()) }
        }
    }

    private val arrivalListener = object : ArrivalListener {
        override fun onDestinationReached() {
            // The engine drops to TRACKING but keeps the session alive — the user decides when to stop.
            _uiState.update { it.copy(isArrived = true) }
        }

        override fun onViaPointReached(iniWaypointIndex: Int, curWaypointIndex: Int, skipped: Boolean) {
            // Single-destination sample: nothing to do.
        }
    }

    init {
        // Register before starting so the first callbacks are not missed.
        sdk.navigationController.addProgressListener(progressListener)
        sdk.navigationController.addInstructionsListener(instructionsListener)
        sdk.navigationController.addArrivalListener(arrivalListener)
    }

    /**
     * Starts the guidance session exactly once. Called from the map's `onReady`, which fires both
     * on the first launch and again after a configuration change — the [started] guard makes sure
     * we never restart the session on rotation (which would lose the route/position).
     *
     * If the engine fails to start (GPS/permission denied, or an engine error) we reset [started]
     * so a later `onReady` can retry, and surface [GuidanceUiState.isError] so the screen shows an
     * error instead of an empty, never-updating guidance UI.
     */
    @MainThread
    fun startGuidanceIfNeeded() {
        if (started) return
        val route = sdk.pendingRoute ?: return
        val demoMode = sdk.pendingDemoMode
        started = true
        // Clear any stale error from a previous failed attempt so this attempt starts clean and a
        // success never leaves the error overlay showing.
        _uiState.update { it.copy(isError = false) }
        viewModelScope.launch {
            val ok = sdk.navigationController.startGuidance(route, demoMode)
            if (ok) {
                // Consume the one-shot hand-off so a recreated ViewModel can't replay this route.
                sdk.pendingRoute = null
            } else {
                // Leave the hand-off intact so a later onReady can retry, and surface the error.
                started = false
                _uiState.update { it.copy(isError = true) }
            }
        }
    }

    /** The user panned the map: drop the follow camera so they can look around. */
    @MainThread
    fun onUserPannedMap() {
        if (_uiState.value.isFollowing) {
            sdk.navigationController.disableFollow()
            _uiState.update { it.copy(isFollowing = false) }
        }
    }

    /** Re-center on the vehicle and resume the 3D follow camera. */
    @MainThread
    fun recenter() {
        sdk.navigationController.enableFollow()
        _uiState.update { it.copy(isFollowing = true) }
    }

    /**
     * Toggles voice guidance. See [com.benomad.sample.guidance.NavigationController.toggleMute]:
     * `audioEnabled` gates whether new announcements are queued, and `muteAudio()`/`unMuteAudio()`
     * also silences/restores an announcement already playing.
     */
    @MainThread
    fun toggleMute() {
        val muted = sdk.navigationController.toggleMute()
        _uiState.update { it.copy(isMuted = muted) }
    }

    /**
     * Stops the session, then invokes [onStopped] (which navigates away). Stopping is awaited
     * BEFORE navigating so the native session is gone before the map view is destroyed — otherwise
     * the engine can deliver a matched position to a dead view and crash. Guarded so the Stop
     * button and a back press don't both trigger it.
     */
    @MainThread
    fun stop(onStopped: () -> Unit) {
        if (stopping) return
        // Terminal: stop() always navigates away (onStopped pops this screen and clears the VM), so
        // `stopping` is deliberately never reset — it only guards against the Stop button and a back
        // press both firing before navigation completes.
        stopping = true
        viewModelScope.launch {
            sdk.navigationController.stopGuidance()
            onStopped()
        }
    }

    override fun onCleared() {
        sdk.navigationController.removeProgressListener(progressListener)
        sdk.navigationController.removeInstructionsListener(instructionsListener)
        sdk.navigationController.removeArrivalListener(arrivalListener)
    }

    private companion object {
        // States in which the guidance UI is considered "live" enough to show.
        val READY_STATES = setOf(
            NavigationProgressState.ON_ROUTE,
            NavigationProgressState.OFF_ROUTE,
            NavigationProgressState.ESTIMATE,
        )
    }
}
