package com.benomad.sample.route

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.mapping.MapState
import com.benomad.msdk.mapping.MapView
import com.benomad.sample.R
import com.benomad.sample.map.SampleMapView
import com.benomad.sample.sdk.SdkProvider
import com.benomad.sample.sdkViewModel
import com.benomad.sample.ui.theme.SampleCardShape

/**
 * Route preview: shows the route (polyline + endpoints) from the current position to the chosen
 * [destination] for a selected vehicle profile, as a list of selectable route-option cards. Each
 * card has a button that opens that route's full turn-by-turn list in a bottom sheet. "Start" /
 * "Start (demo)" hand the selected route to the guidance screen.
 *
 * Responsive layout: **portrait** stacks the map above the detail panel; **landscape** puts the
 * panel in a fixed-width column beside the map so the route stays visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePreviewScreen(
    sdk: SdkProvider,
    destination: GeoPoint,
    destinationLabel: String,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = sdkViewModel(sdk) { RoutePreviewViewModel(it, destination) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Owned by the ViewModel so the route drawing survives configuration changes.
    val renderer = viewModel.routeRenderer
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var showRouteSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Draw whenever the map, the route set, or the selection changes. This also re-runs after a
    // rotation (the MapView reference changes), re-rendering on the freshly created map. start/end
    // are keyed too: compute() always updates them atomically with routes today, but listing them
    // keeps the redraw correct if that ever changes.
    LaunchedEffect(mapView, state.routes, state.selectedRouteIndex, state.start, state.end) {
        val mv = mapView
        val start = state.start
        val end = state.end
        if (mv != null && state.routes.isNotEmpty() && start != null && end != null) {
            renderer.showRoutes(mv, state.routes, state.selectedRouteIndex, start, end)
        }
    }

    val onStart: (Boolean) -> Unit = { demoMode ->
        if (viewModel.prepareNavigation(demoMode)) onStartNavigation()
    }
    val onMapReady: (MapView) -> Unit = { view ->
        mapView = view
        renderer.attach(view)
    }
    // A card's route-sheet button selects that route and opens its turn-by-turn sheet.
    val onShowRouteSheet: (Int) -> Unit = { index ->
        viewModel.selectRoute(index)
        showRouteSheet = true
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Resolve the title once (fall back to a generic label when no address came through).
    val displayTitle = destinationLabel.ifBlank { stringResource(R.string.route_destination) }

    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PreviewMap(sdk, destination, Modifier.fillMaxSize(), onMapReady)
                BackButton(onBack, Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            PreviewPanel(
                state = state,
                title = displayTitle,
                showTitle = true,
                onSelectProfile = viewModel::selectProfile,
                onSelectRoute = viewModel::selectRoute,
                onShowRouteSheet = onShowRouteSheet,
                onStart = onStart,
                modifier = Modifier.width(360.dp).fillMaxHeight(),
            )
        }
    } else {
        Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
            Header(displayTitle, onBack)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                PreviewMap(sdk, destination, Modifier.fillMaxSize(), onMapReady)
            }
            PreviewPanel(
                state = state,
                title = displayTitle,
                showTitle = false,
                onSelectProfile = viewModel::selectProfile,
                onSelectRoute = viewModel::selectRoute,
                onShowRouteSheet = onShowRouteSheet,
                onStart = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showRouteSheet) {
        RouteSheetSheet(
            sheetState = sheetState,
            summary = state.summaries.getOrNull(state.selectedRouteIndex),
            maneuvers = state.maneuvers,
            onDismiss = { showRouteSheet = false },
        )
    }
}

@Composable
private fun PreviewMap(
    sdk: SdkProvider,
    destination: GeoPoint,
    modifier: Modifier = Modifier,
    onReady: (MapView) -> Unit,
) {
    SampleMapView(
        styleProvider = sdk.mapStyleProvider,
        initialCamera = MapState(center = destination, tilt = 0.0, zoomLevel = PREVIEW_ZOOM, orientation = 0.0),
        onStateChanged = {},
        onUserInteraction = {},
        onReady = onReady,
        modifier = modifier,
    )
}

@Composable
private fun Header(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.route_back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Floating back button used in landscape, where there is no header row. */
@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, tonalElevation = 3.dp) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.route_back))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewPanel(
    state: RoutePreviewState,
    title: String,
    showTitle: Boolean,
    onSelectProfile: (String) -> Unit,
    onSelectRoute: (Int) -> Unit,
    onShowRouteSheet: (Int) -> Unit,
    onStart: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, tonalElevation = 3.dp) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (showTitle) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            // Vehicle profile picker (kept here — the sample has no settings screen).
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.profiles.forEach { profile ->
                    FilterChip(
                        selected = profile.id == state.selectedProfileId,
                        onClick = { onSelectProfile(profile.id) },
                        label = { Text(profile.name) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                state.isComputing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.route_computing))
                }

                state.error != null -> {
                    Text(text = state.error, color = MaterialTheme.colorScheme.error)
                }

                state.routes.isNotEmpty() -> {
                    // One selectable card per route (primary + alternatives). Tapping a card selects
                    // it; its list button opens that route's turn-by-turn sheet.
                    state.summaries.forEachIndexed { index, summary ->
                        RouteOptionCard(
                            summary = summary,
                            isSelected = index == state.selectedRouteIndex,
                            onClick = { onSelectRoute(index) },
                            onShowRouteSheet = { onShowRouteSheet(index) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { onStart(false) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.route_start))
                        }
                        OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.route_start_demo))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A selectable route-option card: duration + distance on top, arrival time + a route-sheet button
 * below. The selected card is filled with the primary colour; unselected cards show a leading
 * accent bar (the brand app's preview styling).
 */
@Composable
private fun RouteOptionCard(
    summary: RouteSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onShowRouteSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Row(
        modifier = modifier
            .clip(SampleCardShape)
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
            )
            .height(IntrinsicSize.Min),
    ) {
        if (!isSelected) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = summary.durationText, color = contentColor, fontWeight = FontWeight.Bold)
                Text(text = summary.distanceText, color = contentColor)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = summary.arrivalText, color = contentColor)
                IconButton(onClick = onShowRouteSheet, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.route_sheet),
                        tint = contentColor,
                    )
                }
            }
        }
    }
}

/** The full turn-by-turn list for the selected route, shown in a modal bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteSheetSheet(
    sheetState: SheetState,
    summary: RouteSummary?,
    maneuvers: List<ManeuverItem>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(text = stringResource(R.string.route_sheet), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            summary?.let {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = it.durationText, fontWeight = FontWeight.Bold)
                    Text(text = it.distanceText)
                }
                Text(
                    text = stringResource(R.string.guidance_eta, it.arrivalText),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ManeuverList(maneuvers, Modifier.heightIn(max = 420.dp))
        }
    }
}

@Composable
private fun ManeuverList(maneuvers: List<ManeuverItem>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(maneuvers) { item ->
            // A leading per-maneuver icon could be added here once a maneuver-type → icon mapping
            // is available; for now each row is the instruction text + its distance.
            ListItem(
                headlineContent = { Text(item.instruction) },
                trailingContent = { Text(item.distanceText) },
            )
            HorizontalDivider()
        }
    }
}

// Initial preview zoom before the camera is reframed to the route's bounding box.
private const val PREVIEW_ZOOM = 13.0
