package com.benomad.sample.route

import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.planner.route.Route

/** A single turn-by-turn entry shown in the preview's maneuver list. */
data class ManeuverItem(
    val instruction: String,
    val distanceText: String,
)

/** Distance/duration/arrival summary for one computed route (shown on the selectable route cards). */
data class RouteSummary(
    val distanceText: String,
    val durationText: String,
    val arrivalText: String,
)

/**
 * UI state for the route preview.
 *
 * @property profiles the vehicle profiles the user can pick from.
 * @property selectedProfileId the currently selected profile id.
 * @property isComputing a route computation is in flight.
 * @property routes all computed routes (primary first, then up to 2 alternatives).
 * @property selectedRouteIndex which route is highlighted / will be navigated.
 * @property summaries per-route distance/duration, parallel to [routes].
 * @property start map-matched departure (for drawing), or null.
 * @property end map-matched destination (for drawing), or null.
 * @property maneuvers the turn-by-turn list for the *selected* route.
 * @property error a user-facing error, or null.
 */
data class RoutePreviewState(
    val profiles: List<SampleVehicleProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val isComputing: Boolean = false,
    val routes: List<Route> = emptyList(),
    val selectedRouteIndex: Int = 0,
    val summaries: List<RouteSummary> = emptyList(),
    val start: GeoPoint? = null,
    val end: GeoPoint? = null,
    val maneuvers: List<ManeuverItem> = emptyList(),
    val error: String? = null,
)
