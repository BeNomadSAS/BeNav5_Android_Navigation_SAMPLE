package com.benomad.sample.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.planner.route.Route
import com.benomad.msdk.planner.sheet.Instruction
import com.benomad.sample.sdk.SdkProvider
import com.benomad.sample.sdk.etaFromNow
import com.benomad.sample.sdk.formatDistance
import com.benomad.sample.sdk.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Computes and holds the route from the current position to [destination], for the
 * selected vehicle profile, and exposes the summary + maneuver list for the preview.
 */
class RoutePreviewViewModel(
    private val sdk: SdkProvider,
    private val destination: GeoPoint,
) : ViewModel() {

    private val _state = MutableStateFlow(RoutePreviewState(profiles = sdk.vehicleProfileRepository.profiles))
    val state: StateFlow<RoutePreviewState> = _state.asStateFlow()

    /**
     * Renders the route on the preview map. Held in the ViewModel so its layer group (and the
     * route polyline form attached to it) survive configuration changes — see [RouteMapRenderer].
     */
    val routeRenderer = RouteMapRenderer()

    private var computeJob: Job? = null

    // Captured once for the whole preview: every profile recompute starts from the SAME departure,
    // so a new GPS fix mid-preview can't shift the route origin (or the departure marker, which the
    // renderer adds once) between profile switches.
    private var previewStart: GeoPoint? = null

    init {
        // Compute for the first profile straight away.
        sdk.vehicleProfileRepository.profiles.firstOrNull()?.let { selectProfile(it.id) }
    }

    /** Selects a vehicle profile and (re)computes the route(s). */
    fun selectProfile(profileId: String) {
        _state.update { it.copy(selectedProfileId = profileId) }
        compute()
    }

    /** Selects which computed route is highlighted and will be navigated. */
    fun selectRoute(index: Int) {
        _state.update {
            it.copy(
                selectedRouteIndex = index,
                maneuvers = it.routes.getOrNull(index)?.toManeuverItems().orEmpty(),
            )
        }
    }

    /**
     * Stores the selected route + demo flag so the guidance screen can pick them up.
     * @return false if there is no route yet (the caller should not navigate).
     */
    fun prepareNavigation(demoMode: Boolean): Boolean {
        val state = _state.value
        val route = state.routes.getOrNull(state.selectedRouteIndex) ?: return false
        sdk.pendingRoute = route
        sdk.pendingDemoMode = demoMode
        return true
    }

    private fun compute() {
        val profile = sdk.vehicleProfileRepository.profiles
            .firstOrNull { it.id == _state.value.selectedProfileId } ?: return

        // Fast cached read of the last device fix — safe to call on the main thread. Captured once
        // in previewStart so the departure stays fixed across profile recomputes.
        val start = previewStart ?: sdk.gpsController.lastKnownPoint()?.also { previewStart = it }
        if (start == null) {
            _state.update { it.copy(error = MSG_NO_LOCATION, isComputing = false, routes = emptyList()) }
            return
        }

        computeJob?.cancel()
        // Clear stale route data so the UI shows a clean loading state while recomputing.
        _state.update {
            it.copy(isComputing = true, error = null, routes = emptyList(), summaries = emptyList(), maneuvers = emptyList())
        }
        computeJob = viewModelScope.launch {
            val vehicle = sdk.vehicleProfileRepository.vehicleFor(profile)
            when (val result = sdk.routePlanner.computeRoute(start, destination, vehicle)) {
                is RoutePlanner.PlanResult.Success -> {
                    val routes = result.routes
                    _state.update {
                        it.copy(
                            isComputing = false,
                            routes = routes,
                            selectedRouteIndex = 0,
                            start = result.start,
                            end = result.end,
                            summaries = routes.map { route ->
                                RouteSummary(
                                    distanceText = formatDistance(route.length.toDouble()),
                                    durationText = formatDuration(route.travelDuration),
                                    arrivalText = etaFromNow(route.travelDuration.toInt()),
                                )
                            },
                            // Success guarantees a non-empty list, so the primary route exists.
                            maneuvers = routes.first().toManeuverItems(),
                            error = null,
                        )
                    }
                }

                is RoutePlanner.PlanResult.Failure -> {
                    _state.update { it.copy(isComputing = false, error = result.message, routes = emptyList()) }
                }
            }
        }
    }

    private fun Route.toManeuverItems(): List<ManeuverItem> =
        sheet?.instructions.orEmpty().map { instruction ->
            ManeuverItem(
                instruction = instruction.toReadableText(),
                distanceText = formatDistance(instruction.length.toDouble()),
            )
        }

    private fun Instruction.toReadableText(): String {
        val maneuver = maneuverType.name
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar { it.uppercase() }
        val road = toName?.takeIf { it.isNotBlank() }
        return if (road != null) "$maneuver onto $road" else maneuver
    }

    private companion object {
        const val MSG_NO_LOCATION = "Current location unavailable — enable GPS and try again."
    }
}
