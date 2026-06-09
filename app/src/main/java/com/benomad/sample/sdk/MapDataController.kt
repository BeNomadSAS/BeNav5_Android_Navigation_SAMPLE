package com.benomad.sample.sdk

import com.benomad.msdk.core.Core
import com.benomad.msdk.core.callbacks.OnHybridMapDownloadProgress.OnHybridMapDownloadProgressCallback
import com.benomad.msdk.core.callbacks.OnMapDownloadError.OnMapDownloadErrorCallback
import com.benomad.msdk.core.callbacks.OnMapDownloadProgress.OnMapDownloadProgressCallback
import com.benomad.msdk.core.callbacks.OnMapDownloaded.OnMapDownloadedCallback
import com.benomad.msdk.core.callbacks.OnMapExtractionProgress.OnMapExtractionProgressCallback
import com.benomad.msdk.core.callbacks.OnMapUpdateAvailable.OnMapUpdateAvailableCallback
import com.benomad.msdk.errormanager.Error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Observes BeNomad map-data activity and exposes it as a single [MapDownloadProgress]
 * [StateFlow]. It does **not** choose full vs hybrid — that is decided by the license /
 * LBO inside `Core.init`; this controller only reflects what Core reports and reads the
 * resulting mode via [Core.isUsingHybridMaps].
 *
 * Usage (see `onboarding/SplashViewModel`):
 * 1. [registerDownloadObservers] **before** `Core.init` so a first-launch FULL download
 *    is visible.
 * 2. After Core is ready, call [onCoreReady] to read the map mode and attach the
 *    matching runtime observer (hybrid streaming, or full-mode update auto-accept).
 *
 * All Core callbacks are delivered on the main thread, so updating the flow from them
 * is safe.
 */
class MapDataController {

    private val core: Core = Core.getInstance()

    private val _progress = MutableStateFlow<MapDownloadProgress>(MapDownloadProgress.Idle)
    /** Current map-data progress. */
    val progress: StateFlow<MapDownloadProgress> = _progress.asStateFlow()

    /** True once [onCoreReady] has run and the license selected hybrid (streamed) maps. */
    var isHybrid: Boolean = false
        private set

    // --- FULL-mode observers (first-launch download + later background updates) ---
    private val onDownloadProgress = OnMapDownloadProgressCallback { percent, downloaded, total ->
        _progress.value = MapDownloadProgress.Downloading(percent, downloaded, total)
    }
    private val onExtractionProgress = OnMapExtractionProgressCallback { percent ->
        _progress.value = MapDownloadProgress.Extracting(percent)
    }
    private val onDownloaded = OnMapDownloadedCallback {
        _progress.value = MapDownloadProgress.Ready
    }
    private val onDownloadError = OnMapDownloadErrorCallback { error ->
        _progress.value = MapDownloadProgress.Failed(error.readableMessage())
    }

    // --- FULL-mode: a newer base map exists → accept it (installs on next launch). ---
    private val onUpdateAvailable = OnMapUpdateAvailableCallback {
        core.downloadAvailableUpdate(true)
    }

    // --- HYBRID-mode: background tile streaming health/progress. ---
    private val onHybridProgress = OnHybridMapDownloadProgressCallback { status, percent ->
        _progress.value = when {
            // status: -3 no storage, -2 internal error, -1 network error
            status < 0 -> MapDownloadProgress.Failed("Map streaming error (code $status)")
            // 0 = started, 1 = in progress (percent valid)
            status == 0 || status == 1 -> MapDownloadProgress.StreamingHybrid(percent.toFloat())
            // 2 = finished
            else -> MapDownloadProgress.Ready
        }
    }

    /**
     * Registers the FULL-mode download/extraction observers. Call this **before**
     * `Core.init` so first-launch download progress is not missed.
     */
    fun registerDownloadObservers() {
        core.addOnMapDownloadProgressObserver(onDownloadProgress)
        core.addOnMapExtractionProgressObserver(onExtractionProgress)
        core.addOnMapDownloadedObserver(onDownloaded)
        core.addOnMapDownloadErrorObserver(onDownloadError)
    }

    /**
     * Call once Core is initialized. Reads the resolved map mode and attaches the
     * matching runtime observer, then marks progress [MapDownloadProgress.Ready] so the
     * splash can proceed (unless a download already failed).
     */
    fun onCoreReady() {
        isHybrid = core.isUsingHybridMaps()
        if (isHybrid) {
            core.addOnHybridDownloadProgressObserver(onHybridProgress)
        } else {
            core.addOnMapUpdateAvailableObserver(onUpdateAvailable)
        }
        // Safe to report Ready now: in FULL mode Core fires onCoreReady only after the
        // base map has been downloaded/extracted (the observers above have already run);
        // in HYBRID mode tile streaming is non-blocking and continues on the map screen.
        if (_progress.value !is MapDownloadProgress.Failed) {
            _progress.value = MapDownloadProgress.Ready
        }
    }

    /**
     * HYBRID only: **submits** a request to download the full map of [countryCode] so it becomes
     * available offline. Returns immediately once the request is accepted (`null`) or rejected
     * (an [Error] — e.g. in full mode, or an invalid code); it does NOT wait for the download to
     * finish. Actual progress is reported via [progress] (StreamingHybrid / Ready / Failed). The
     * `withContext(Dispatchers.IO)` is only a defensive hedge in case the native submit does any
     * synchronous validation first.
     *
     * @param countryCode ISO 3166-1 **alpha-3** country code (e.g. "FRA").
     */
    suspend fun downloadCountryForOffline(countryCode: String): Error? =
        withContext(Dispatchers.IO) {
            core.loadFullMapsInCountry(countryCode, /* asyncMode = */ true)
        }

    /** Removes every registered observer. The app-scoped controller normally lives for
     * the whole process, so this is only needed in tests or on teardown. */
    fun dispose() {
        core.removeOnMapDownloadProgressObserver(onDownloadProgress)
        core.removeOnMapExtractionProgressObserver(onExtractionProgress)
        core.removeOnMapDownloadedObserver(onDownloaded)
        core.removeOnMapDownloadErrorObserver(onDownloadError)
        core.removeOnMapUpdateAvailableObserver(onUpdateAvailable)
        core.removeOnHybridDownloadProgressObserver(onHybridProgress)
    }
}
