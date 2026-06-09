package com.benomad.sample.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.benomad.msdk.errormanager.Error
import com.benomad.msdk.mapping.MapState
import com.benomad.msdk.mapping.MapView
import com.benomad.msdk.mapping.callbacks.OnMapReadyCallback
import com.benomad.msdk.mapping.callbacks.OnUserInteractionListener
import com.benomad.sample.sdk.MapStyleProvider

/**
 * Hosts the SDK [MapView] inside Compose via [AndroidView].
 *
 * Key lifecycle rules baked in here:
 * - The map is only safe to manipulate after [OnMapReadyCallback.onMapReady]; the style
 *   and initial camera are therefore applied inside that callback (calling them earlier
 *   silently no-ops).
 * - `onStateChanged` is invoked off the main thread and throttled (~200 ms); we just
 *   forward the camera to the (thread-safe) ViewModel state.
 * - The native map is disposed in `onRelease { onDestroy() }`.
 *
 * Built-in pan / pinch-zoom / rotate / tilt gestures work with zero extra code.
 *
 * @param onReady receives the ready [MapView] so the screen can drive the camera and
 *   attach the navigation engine.
 */
@Composable
fun SampleMapView(
    styleProvider: MapStyleProvider,
    initialCamera: MapState,
    onStateChanged: (MapState) -> Unit,
    onUserInteraction: () -> Unit,
    onReady: (MapView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val mapView = MapView(
                context = context,
                // Suspend callback (off-main, throttled): forward camera moves to the VM.
                onStateChanged = onStateChanged,
            )

            // onMapViewTouched fires on every touch-down, even while follow mode has
            // disabled gestures — the canonical hook to drop out of tracking.
            mapView.onUserInteractionListener = object : OnUserInteractionListener {
                override fun onMapViewTouched() = onUserInteraction()
            }

            mapView.addOnMapReadyObserver(object : OnMapReadyCallback {
                override fun onMapReady() {
                    // The style is loaded during the splash (before this screen). If it were
                    // somehow null here, the map renders unstyled rather than crashing.
                    styleProvider.style()?.let { mapView.setMapStyle(it) }
                    mapView.updateMapState(initialCamera)
                    mapView.redraw(drawTexts = true, tryLock = false)
                    onReady(mapView)
                }

                override fun onMapError(error: Error) {
                    // A real app would surface this; the sample relies on the splash gate.
                }
            })

            mapView
        },
        onRelease = { it.onDestroy() },
    )
}
