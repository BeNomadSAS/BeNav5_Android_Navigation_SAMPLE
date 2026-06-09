package com.benomad.sample.map

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.mapping.MapState
import com.benomad.msdk.mapping.MapView
import com.benomad.sample.sdk.SdkConfig
import com.benomad.sample.sdk.SdkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A selected destination, shown in the map's info card. */
data class DestinationInfo(
    val title: String,
    val subtitle: String?,
    val point: GeoPoint,
)

/**
 * Owns the map screen's behavior and the small amount of state that must survive view recreation:
 * whether we are following the user, the selected destination, and the last camera position.
 *
 * The screen forwards user intent here and only reads state back (matching the other feature
 * screens) — the composable holds no SDK orchestration. The [MapView] is **never stored** here (a
 * View must not outlive its composition): it is recreated with the view and passed transiently into
 * the methods that need it. This ViewModel calls the shared SDK wrappers via [SdkProvider].
 */
class MapViewModel(private val sdk: SdkProvider) : ViewModel() {

    // Last camera reported by the map; cached so a recreated map can restore it. Kept private —
    // nothing observes it; it is only read by initialCamera().
    private val _camera = MutableStateFlow<MapState?>(null)

    private val _isFollowing = MutableStateFlow(false)
    /** True while the map follows the user position (tracking mode). Drives the center FAB icon. */
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _destination = MutableStateFlow<DestinationInfo?>(null)
    /** The selected destination; held here so it survives configuration changes. */
    val destination: StateFlow<DestinationInfo?> = _destination.asStateFlow()

    /** The camera to apply when the map first loads: cached value, last GPS fix, or the default. */
    fun initialCamera(): MapState =
        _camera.value
            ?: sdk.gpsController.lastKnownPoint()?.let {
                MapState(center = it, tilt = 0.0, zoomLevel = USER_ZOOM, orientation = 0.0)
            }
            ?: MapState(center = SdkConfig.DEFAULT_MAP_CENTER, tilt = 0.0, zoomLevel = DEFAULT_ZOOM, orientation = 0.0)

    /**
     * The map is ready (first load or screen re-entry): rebind the engine + marker layer to
     * [mapView], restore the destination marker, and start device tracking when no session is
     * active. A fresh tracking session also covers returning from guidance (which leaves the engine
     * idle on its demo source with the route still drawn). On a rotation the Activity/MapView are
     * kept alive (android:configChanges), so this is not re-entered.
     */
    @MainThread
    fun onMapReady(mapView: MapView) {
        sdk.navigationController.reattachAndResume(mapView, _isFollowing.value)
        sdk.destinationMarkerManager.attach(mapView)
        _destination.value?.let { sdk.destinationMarkerManager.showDestination(mapView, it.point) }
        viewModelScope.launch {
            if (!sdk.navigationController.isActive && sdk.gpsController.isGpsEnabled()) {
                _isFollowing.value = sdk.navigationController.startTracking()
            } else if (!sdk.navigationController.isActive) {
                // No session and GPS off (e.g. back from guidance with GPS disabled): nothing to follow.
                _isFollowing.value = false
            }
        }
    }

    /** Detach the map from the engine when the screen leaves (the session itself persists). */
    @MainThread
    fun onMapDetached() {
        sdk.navigationController.detachMapView()
    }

    /** A map gesture: drop follow mode so the user can pan freely. */
    @MainThread
    fun onUserPanned() {
        if (_isFollowing.value) {
            sdk.navigationController.disableFollow()
            _isFollowing.value = false
        }
    }

    /**
     * Center/tracking FAB. Re-centers if already following, resumes follow if a session is running,
     * otherwise starts a fresh tracking session ([onUnavailable] fires if it cannot start, e.g. no
     * fix yet). The caller must ensure GPS is enabled first — that check drives a system-settings
     * prompt, which needs an Activity context.
     */
    @MainThread
    fun onCenterPressed(onUnavailable: () -> Unit = {}) {
        when {
            _isFollowing.value -> sdk.navigationController.enableFollow()
            sdk.navigationController.isActive -> {
                sdk.navigationController.enableFollow()
                _isFollowing.value = true
            }
            else -> viewModelScope.launch {
                val started = sdk.navigationController.startTracking()
                _isFollowing.value = started
                if (!started) onUnavailable()
            }
        }
    }

    /** A search result was chosen: stop following, drop a marker, frame the camera, remember it. */
    @MainThread
    fun onDestinationSelected(mapView: MapView, info: DestinationInfo) {
        if (_isFollowing.value) {
            sdk.navigationController.disableFollow()
            _isFollowing.value = false
        }
        sdk.destinationMarkerManager.showDestination(mapView, info.point)
        mapView.zoomTo(DESTINATION_ZOOM, info.point, true)
        _destination.value = info
    }

    /** Clear the selected destination and its marker. */
    @MainThread
    fun clearDestination(mapView: MapView) {
        sdk.destinationMarkerManager.clearDestination(mapView)
        _destination.value = null
    }

    /**
     * Caches the camera reported by the map's throttled `onStateChanged`. Thread-safe: the SDK
     * delivers this callback OFF the main thread, and [MutableStateFlow] value assignment is safe.
     */
    fun onMapStateChanged(state: MapState) {
        _camera.value = state
    }

    private companion object {
        // Zoom levels: when centering on the user, when framing a chosen destination, and the
        // default when there is no GPS fix.
        const val USER_ZOOM = 16.0
        const val DESTINATION_ZOOM = 15.0
        const val DEFAULT_ZOOM = 12.0
    }
}
