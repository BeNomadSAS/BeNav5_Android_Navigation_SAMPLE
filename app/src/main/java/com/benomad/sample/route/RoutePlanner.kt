package com.benomad.sample.route

import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.errormanager.Error
import com.benomad.msdk.geocoder.Address
import com.benomad.msdk.geocoder.results.AddressCreationCallback
import com.benomad.msdk.planner.Planner
import com.benomad.msdk.planner.RoutePlan
import com.benomad.msdk.planner.listener.ComputeRouteListener
import com.benomad.msdk.planner.route.Route
import com.benomad.msdk.planner.route.RouteResult
import com.benomad.msdk.planner.errorcode.PlannerErrorCodes
import com.benomad.msdk.planner.route.options.RouteOptions
import com.benomad.msdk.vehiclemanager.model.Vehicle
import com.benomad.sample.sdk.readableMessage
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Computes a route from a departure to a destination with a vehicle profile, wrapping the
 * callback-based [Planner] API in clean `suspend` functions.
 *
 * Two SDK behaviours the published docs don't make clear are handled here:
 * 1. **Map-matching is required.** `RoutePlan` accepts raw `GeoPoint`s and nothing on
 *    `computeRoute`/`RoutePlan` says they must be matched, yet a raw point has no road location, so
 *    `computeRoute` throws inside its background coroutine and fires NO listener callback — the call
 *    silently drops. Both endpoints are first run through `Address.create` and the matched
 *    `Address.location` is used.
 * 2. **Single-flight.** `computeRoute` rejects a concurrent call with a `BUSY` error (not surfaced
 *    in the published API reference). We cancel the previous computation on coroutine cancellation
 *    and retry briefly on BUSY (see [compute]).
 *
 * All Planner/Geocoder callbacks are delivered on the main thread.
 */
class RoutePlanner(private val planner: Planner) {

    /** Outcome of [computeRoute]. */
    sealed interface PlanResult {
        /**
         * Routes found (the primary first, then up to 2 alternatives); [start]/[end] are the
         * map-matched endpoints used for drawing markers.
         */
        data class Success(val routes: List<Route>, val start: GeoPoint, val end: GeoPoint) : PlanResult

        data class Failure(val message: String) : PlanResult
    }

    /**
     * Map-matches both endpoints, then computes the route (plus up to 2 alternatives) with the
     * given [vehicle] (null → the Planner uses a default passenger-car profile).
     */
    suspend fun computeRoute(start: GeoPoint, end: GeoPoint, vehicle: Vehicle?): PlanResult {
        val matchedStart = mapMatch(start) ?: return PlanResult.Failure(MSG_MATCH_START)
        val matchedEnd = mapMatch(end) ?: return PlanResult.Failure(MSG_MATCH_DESTINATION)

        val plan = RoutePlan(
            departures = listOf(matchedStart),
            destinations = listOf(matchedEnd),
            // Required by RoutePlan; this sample supports only direct routes (no waypoints).
            viaPoints = emptyList(),
            // maxAlternativeRoutes is clamped to [0,2] by the SDK → up to 3 routes total.
            routeOptions = RouteOptions(vehicle = vehicle, maxAlternativeRoutes = MAX_ALTERNATIVE_ROUTES),
        )
        return when (val outcome = compute(plan)) {
            is Outcome.Ok -> PlanResult.Success(outcome.routes, matchedStart, matchedEnd)
            is Outcome.Err -> PlanResult.Failure(outcome.message)
        }
    }

    /**
     * Snaps a raw coordinate to the road network; null if no address could be created (or the
     * address has no road location).
     *
     * Note: `Address.create` has no cancellation hook — if this coroutine is cancelled the
     * background geocoding still completes, but the `isActive` guard discards its late result.
     */
    private suspend fun mapMatch(point: GeoPoint): GeoPoint? = suspendCancellableCoroutine { continuation ->
        Address.create(
            position = point,
            callback = object : AddressCreationCallback {
                override fun onResult(address: Address?, listIndex: Int?) {
                    if (continuation.isActive) continuation.resume(address?.location)
                }
            },
        )
    }

    private sealed interface Outcome {
        data class Ok(val routes: List<Route>) : Outcome
        data class Err(val message: String, val busy: Boolean = false) : Outcome
    }

    /**
     * Computes the route, retrying briefly on BUSY.
     *
     * The Planner runs one computation at a time. When the user switches vehicle profile quickly, a
     * new computation can arrive while the previous one is still finishing — `cancel()` returns
     * before the engine is actually free (its timing isn't documented) — and receive a transient
     * BUSY error. We retry a few times after a short delay so the user never sees that flash; a
     * client that cancels then immediately recomputes must handle the same race.
     */
    private suspend fun compute(plan: RoutePlan): Outcome {
        var outcome = computeOnce(plan)
        var retries = 0
        while (outcome is Outcome.Err && outcome.busy && retries < MAX_BUSY_RETRIES) {
            delay(BUSY_RETRY_DELAY_MS)
            outcome = computeOnce(plan)
            retries++
        }
        return outcome
    }

    private suspend fun computeOnce(plan: RoutePlan): Outcome = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { planner.cancel() }
        planner.computeRoute(
            plan,
            object : ComputeRouteListener {
                override fun onComputeError(planner: Planner, error: Error) {
                    if (continuation.isActive) {
                        continuation.resume(
                            Outcome.Err(error.readableMessage(), busy = error.code == PlannerErrorCodes.BUSY.code),
                        )
                    }
                }

                override fun onComputeFinished(planner: Planner, results: List<RouteResult>) {
                    // Collect every usable route (primary first, then alternatives). A
                    // RouteResult can carry a route AND a per-route error; an empty list means
                    // no route was found.
                    val routes = results.mapNotNull { it.route }
                    if (continuation.isActive) {
                        continuation.resume(
                            if (routes.isNotEmpty()) Outcome.Ok(routes) else Outcome.Err(MSG_NO_ROUTE),
                        )
                    }
                }
            },
        )
    }

    private companion object {
        const val MSG_MATCH_START = "Could not match the departure to a road."
        const val MSG_MATCH_DESTINATION = "Could not match the destination to a road."
        const val MSG_NO_ROUTE = "No route found."
        const val MAX_BUSY_RETRIES = 3
        const val BUSY_RETRY_DELAY_MS = 150L

        /** Alternative routes to request (SDK clamps to [0,2]); 2 → up to 3 routes total. */
        const val MAX_ALTERNATIVE_ROUTES = 2
    }
}
