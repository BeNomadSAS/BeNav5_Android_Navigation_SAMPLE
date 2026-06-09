package com.benomad.sample.map

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.mapping.MapView
import com.benomad.sample.R
import com.benomad.sample.sdk.MapDownloadProgress
import com.benomad.sample.sdk.SdkProvider
import com.benomad.sample.sdk.readableMessage
import com.benomad.sample.sdkViewModel
import com.benomad.sample.search.DestinationSearchBar
import com.benomad.sample.search.SearchViewModel
import com.benomad.sample.ui.components.CircularIconButton
import kotlinx.coroutines.launch

/**
 * The interactive map screen.
 *
 * Demonstrates: map display, gesture + programmatic interaction (zoom / reset-north),
 * center-on-user tracking (auto-engaged at launch), destination **search** with a POI
 * **marker** at the chosen point, the hybrid streaming indicator, and the offline pre-cache demo.
 *
 * All tracking/follow and marker orchestration lives in [MapViewModel]; this composable only reads
 * state and forwards user intent (matching the other feature screens). The direct camera commands
 * (zoom / reset-north) are issued on the remembered [MapView], which is never stored in the VM.
 */
@Composable
fun MapScreen(
    sdk: SdkProvider,
    onPlanRoute: (destination: GeoPoint, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val mapViewModel = sdkViewModel(sdk) { MapViewModel(it) }
    val searchViewModel = sdkViewModel(sdk) { SearchViewModel(it) }

    val isFollowing by mapViewModel.isFollowing.collectAsStateWithLifecycle()
    val mapProgress by sdk.mapDataController.progress.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val destination by mapViewModel.destination.collectAsStateWithLifecycle()
    var mapView by remember { mutableStateOf<MapView?>(null) }

    // Open the map centered on the user's last known position (falls back to the default).
    val initialCamera = remember { mapViewModel.initialCamera() }

    // Re-center / tracking action shared by the recenter control. GPS-disabled is handled here (it
    // opens system settings, which needs an Activity context); the follow/track orchestration is in the VM.
    val onRecenter: () -> Unit = {
        val gps = sdk.gpsController
        if (!gps.isGpsEnabled()) {
            context.startActivity(gps.gpsActivationIntent())
        } else {
            mapViewModel.onCenterPressed(
                onUnavailable = {
                    Toast.makeText(context, R.string.map_location_unavailable, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        SampleMapView(
            styleProvider = sdk.mapStyleProvider,
            initialCamera = initialCamera,
            onStateChanged = mapViewModel::onMapStateChanged,
            onUserInteraction = mapViewModel::onUserPanned,
            onReady = { view ->
                mapView = view
                // Binds the engine + marker layer to this view, restores the destination marker,
                // and starts device tracking when idle (see MapViewModel.onMapReady).
                mapViewModel.onMapReady(view)
            },
            modifier = Modifier.fillMaxSize(),
        )

        // --- Top: search field, then the control buttons right-aligned beneath it. The search
        // suggestions render inside the field, so they push the buttons down and release them on
        // selection. ---
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DestinationSearchBar(
                state = searchState,
                onQueryChange = searchViewModel::onQueryChange,
                onClear = {
                    searchViewModel.clear()
                    mapView?.let { mapViewModel.clearDestination(it) }
                },
                onResultSelected = { result ->
                    val coordinate = result.coordinate
                    val mv = mapView
                    // Only act once the map is ready AND the result has a routable coordinate.
                    if (coordinate != null && mv != null) {
                        focusManager.clearFocus()
                        val label = result.addressLabel ?: result.place
                        mapViewModel.onDestinationSelected(
                            mv,
                            DestinationInfo(result.place, result.addressLabel, coordinate),
                        )
                        // Show the chosen address in the field instead of a separate card.
                        searchViewModel.showSelected(label)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (mapProgress is MapDownloadProgress.StreamingHybrid) {
                StreamingIndicator()
            }

            // Zoom + recenter — stacked at the right, just under the search field.
            Column(
                modifier = Modifier.align(Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapControlButton(Icons.Filled.Add, stringResource(R.string.map_zoom_in)) {
                    mapView?.let { it.zoom(it.zoomLevel + 1.0, true) }
                }
                MapControlButton(Icons.Filled.Remove, stringResource(R.string.map_zoom_out)) {
                    mapView?.let { it.zoom(it.zoomLevel - 1.0, true) }
                }
                // Recenter is hidden while already following the user; it appears only once the user
                // has panned away, to re-engage tracking.
                if (!isFollowing) {
                    CircularIconButton(
                        painter = painterResource(R.drawable.icon_position),
                        contentDescription = stringResource(R.string.map_center),
                        onClick = onRecenter,
                    )
                }
            }
        }

        // --- Offline pre-cache demo (hybrid licenses only, bottom-start) ---
        if (sdk.mapDataController.isHybrid) {
            CircularIconButton(
                icon = Icons.Filled.CloudDownload,
                contentDescription = stringResource(R.string.map_cache_offline),
                onClick = {
                    scope.launch {
                        val error = sdk.mapDataController.downloadCountryForOffline(OFFLINE_COUNTRY)
                        val message = if (error == null) {
                            context.getString(R.string.map_offline_started)
                        } else {
                            error.readableMessage()
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(16.dp),
            )
        }

        // --- Plan-route button: shown once a destination is selected, bottom-right (right edge
        // aligned with the control buttons). ---
        destination?.let { info ->
            Button(
                onClick = { onPlanRoute(info.point, info.title) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 12.dp, bottom = 16.dp),
            ) {
                Text(stringResource(R.string.map_plan_route))
            }
        }
    }

    // Detach the map from the engine when leaving (the session persists on the singleton; the
    // guidance flow manages stopping it). onDispose runs before the AndroidView's onRelease →
    // onDestroy, so the engine is unbound before the native GL surface is destroyed.
    DisposableEffect(Unit) {
        onDispose { mapViewModel.onMapDetached() }
    }
}

/** Small chip shown while hybrid map tiles stream in the background. */
@Composable
private fun StreamingIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.map_streaming), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A circular control button used for the zoom / reset-north overlay (shared app button style). */
@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CircularIconButton(icon = icon, contentDescription = contentDescription, onClick = onClick, modifier = modifier)
}

// ISO 3166-1 alpha-3 country to pre-cache in the offline demo.
private const val OFFLINE_COUNTRY = "FRA"
