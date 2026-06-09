package com.benomad.sample.guidance

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benomad.msdk.mapping.MapState
import com.benomad.sample.R
import com.benomad.sample.map.SampleMapView
import com.benomad.sample.sdk.SdkConfig
import com.benomad.sample.sdk.SdkProvider
import com.benomad.sample.sdkViewModel
import com.benomad.sample.ui.components.CircularIconButton
import com.benomad.sample.ui.theme.SampleCardElevation
import com.benomad.sample.ui.theme.SampleCardShape

/**
 * Turn-by-turn guidance screen: a full-screen map that the Navigation engine drives (vehicle
 * symbol + route), a top maneuver bar (icon + distance + text), a bottom route-info bar
 * (remaining time + ETA / distance + stop), top-right mute + re-center controls, an optional
 * speed-limit badge, and an "arrived" card.
 *
 * The route + demo flag are taken from [SdkProvider.pendingRoute]/[SdkProvider.pendingDemoMode],
 * set by the route preview. Voice guidance plays automatically (the engine speaks it).
 */
@Composable
fun GuidanceScreen(sdk: SdkProvider, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = sdkViewModel(sdk) { GuidanceViewModel(it) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Pre-start camera (the engine takes over with the 3D follow camera once guidance starts).
    val initialCamera = remember {
        val point = sdk.gpsController.lastKnownPoint() ?: SdkConfig.DEFAULT_MAP_CENTER
        MapState(center = point, tilt = 0.0, zoomLevel = INITIAL_ZOOM, orientation = 0.0)
    }

    // Stop the session, THEN navigate away. Both the Stop button and a back press route through
    // here so the session is always torn down cleanly (never on a mere rotation). Remembered so it
    // is not re-allocated on every progress tick.
    val stopAndExit = remember(viewModel, onExit) { { viewModel.stop(onExit) } }
    BackHandler { stopAndExit() }

    // Landscape lays the bars out as left-aligned rounded cards (the maneuver card capped to a third
    // of the width) with the controls + speed on the right; portrait keeps full-width top/bottom bars.
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = modifier.fillMaxSize()) {

        SampleMapView(
            styleProvider = sdk.mapStyleProvider,
            initialCamera = initialCamera,
            onStateChanged = {},
            onUserInteraction = viewModel::onUserPannedMap,
            onReady = { view ->
                // Fires once when the native map loads. With android:configChanges declared, the
                // Activity and this (native GL) MapView are kept alive on rotation, so onReady is NOT
                // re-entered on a config change. reattachAndResume binds the view and resumes follow
                // if a session is active; startGuidanceIfNeeded starts the session only once (guarded
                // by `started`). Both stay correct if the view is ever recreated by a non-config path
                // (e.g. process death).
                sdk.navigationController.reattachAndResume(view, viewModel.uiState.value.isFollowing)
                viewModel.startGuidanceIfNeeded()
            },
            modifier = Modifier.fillMaxSize(),
        )

        val instruction = state.instruction
        if (isLandscape) {
            // Top-left: the next-maneuver card, capped to one third of the screen width.
            if (state.isReady && instruction != null) {
                NextInstructionBar(
                    maneuverIcon = state.maneuverIcon,
                    distanceToNext = state.distanceToNextText,
                    instruction = instruction,
                    shape = SampleCardShape,
                    shadowElevation = SampleCardElevation,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
                        .padding(12.dp)
                        .widthIn(max = (configuration.screenWidthDp / 3).dp),
                )
            }
            // Top-right: mute + recenter.
            GuidanceControls(
                isMuted = state.isMuted,
                isFollowing = state.isFollowing,
                onToggleMute = viewModel::toggleMute,
                onRecenter = viewModel::recenter,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(12.dp),
            )
            // Bottom-left: route-info card (wraps its content).
            RouteInfoBar(
                distanceToDest = state.distanceToDestText,
                durationToDest = state.durationToDestText,
                etaText = state.etaText,
                onStop = stopAndExit,
                shape = SampleCardShape,
                shadowElevation = SampleCardElevation,
                fillContentWidth = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(12.dp)
                    .widthIn(max = (configuration.screenWidthDp / 3).dp),
            )
            // Bottom-right: speed limit, clear of the left-aligned cards.
            state.speedLimitText?.let { limit ->
                SpeedLimitBadge(
                    limit = limit,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .safeDrawingPadding()
                        .padding(16.dp),
                )
            }
        } else {
            // Portrait top: full-width maneuver bar, then the mute + recenter controls beneath it.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding(),
            ) {
                if (state.isReady && instruction != null) {
                    NextInstructionBar(
                        maneuverIcon = state.maneuverIcon,
                        distanceToNext = state.distanceToNextText,
                        instruction = instruction,
                        shape = SampleCardShape,
                        shadowElevation = SampleCardElevation,
                        // Full width less a small margin → a rounded card floating over the map,
                        // rather than a square bar that looks like it cuts the map at the status bar.
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    )
                }
                GuidanceControls(
                    isMuted = state.isMuted,
                    isFollowing = state.isFollowing,
                    onToggleMute = viewModel::toggleMute,
                    onRecenter = viewModel::recenter,
                    modifier = Modifier.align(Alignment.End).padding(12.dp),
                )
            }
            // Portrait bottom: the speed limit floats above the full-width route-info bar (right-aligned).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                state.speedLimitText?.let { limit ->
                    SpeedLimitBadge(
                        limit = limit,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 16.dp, bottom = 8.dp),
                    )
                }
                RouteInfoBar(
                    distanceToDest = state.distanceToDestText,
                    durationToDest = state.durationToDestText,
                    etaText = state.etaText,
                    onStop = stopAndExit,
                    shape = SampleCardShape,
                    shadowElevation = SampleCardElevation,
                    // Full width less a small margin → floats above the navigation bar instead of
                    // sitting flush as a square bar with map peeking below it.
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                )
            }
        }

        // Arrived overlay.
        if (state.isArrived) {
            ArrivedCard(
                onDone = stopAndExit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        }

        // Failed-to-start overlay (GPS/permission/engine error) — lets the user back out cleanly.
        if (state.isError) {
            ErrorCard(
                onBack = stopAndExit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        }
    }

    // Integration note (a Compose lifecycle choice, not an SDK quirk): we deliberately do NOT
    // detachMapView() here. Compose's onDispose also fires on a configuration change, and on
    // screen-exit it runs during the pop transition — after the map screen has already re-attached
    // its OWN MapView; detaching here would then race that re-attach and leave the map unbound (no
    // vehicle, no gestures). We don't need to anyway: the SDK attaches only one MapView at a time (a
    // new attachMapView replaces + detaches the previous — documented), and a destroyed view
    // auto-detaches via onMapDestroyed. The session is stopped explicitly on Stop / back (stopAndExit).
}

/**
 * Next-maneuver bar: the maneuver icon on the left (≈30% width), then the distance + instruction.
 * Takes only the fields it renders (not the whole GuidanceUiState) so it is not recomposed by the
 * per-second changes to ETA/speed-limit/remaining-distance.
 *
 * [shape] and [shadowElevation] let the caller render a flat bar (the defaults) or a rounded,
 * shadowed floating card (what both orientations use).
 */
@Composable
private fun NextInstructionBar(
    maneuverIcon: ImageBitmap?,
    distanceToNext: String?,
    instruction: String,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    shadowElevation: Dp = 0.dp,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = shadowElevation,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (maneuverIcon != null) {
                Image(
                    bitmap = maneuverIcon,
                    contentDescription = null,
                    modifier = Modifier.weight(0.3f).padding(8.dp),
                )
            } else {
                Spacer(Modifier.weight(0.3f))
            }
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(0.7f)) {
                // Hide the distance once it rounds to "0 m" at the maneuver point — icon + text remain.
                distanceToNext?.takeUnless { it.startsWith("0 ") }
                    ?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
                Text(instruction, style = MaterialTheme.typography.bodyLarge, maxLines = 4)
            }
        }
    }
}

/**
 * Bottom info bar: an outlined "stop" (×) button, then the remaining time with the arrival clock /
 * remaining distance beneath.
 *
 * [shape] and [shadowElevation] style it as a flat bar (defaults) or a rounded, shadowed card.
 * [fillContentWidth] true gives the text [Column] `weight(1f)` to spread across a full-width bar
 * (portrait); false lets it wrap so the card shrinks to its content (the capped-width landscape card).
 */
@Composable
private fun RouteInfoBar(
    distanceToDest: String?,
    durationToDest: String?,
    etaText: String?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    shadowElevation: Dp = 0.dp,
    fillContentWidth: Boolean = true,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = shadowElevation,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedIconButton(onClick = onStop) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.guidance_stop))
            }
            // Full-width (portrait) spreads the text via weight; the wrapped landscape card does not.
            Column(modifier = if (fillContentWidth) Modifier.weight(1f) else Modifier) {
                durationToDest?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    etaText?.let {
                        Text(
                            text = stringResource(R.string.guidance_eta, it),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (etaText != null && distanceToDest != null) {
                        Text(text = "  |  ", style = MaterialTheme.typography.bodySmall)
                    }
                    distanceToDest?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/**
 * The mute toggle and (only while the follow camera is off) the re-center button, stacked
 * vertically. Shared by both orientations — the caller positions it via [modifier].
 */
@Composable
private fun GuidanceControls(
    isMuted: Boolean,
    isFollowing: Boolean,
    onToggleMute: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularIconButton(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = stringResource(
                if (isMuted) R.string.guidance_unmute else R.string.guidance_mute,
            ),
            onClick = onToggleMute,
        )
        if (!isFollowing) {
            CircularIconButton(
                painter = painterResource(R.drawable.icon_position),
                contentDescription = stringResource(R.string.guidance_recenter),
                onClick = onRecenter,
            )
        }
    }
}

/** Circular speed-limit sign: white disc with a red ring and the limit (km/h) centered. */
@Composable
private fun SpeedLimitBadge(limit: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(72.dp).clip(CircleShape),
        color = Color.White,
    ) {
        Box(
            modifier = Modifier
                .border(6.dp, Color.Red, CircleShape)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = limit, color = Color.Black, fontWeight = FontWeight.W500, fontSize = 21.sp)
        }
    }
}

@Composable
private fun ArrivedCard(onDone: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.guidance_arrived),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone) { Text(stringResource(R.string.guidance_done)) }
        }
    }
}

@Composable
private fun ErrorCard(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.guidance_error),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text(stringResource(R.string.route_back)) }
        }
    }
}

// Pre-start camera zoom, before the engine takes over with the 3D follow camera.
private const val INITIAL_ZOOM = 16.0
