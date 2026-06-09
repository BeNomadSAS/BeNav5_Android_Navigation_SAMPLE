package com.benomad.sample.sdk

import android.content.Context
import com.benomad.msdk.geocoder.OnlineGeoCoder
import com.benomad.msdk.planner.Planner
import com.benomad.msdk.planner.route.Route
import com.benomad.sample.guidance.GpsController
import com.benomad.sample.guidance.NavigationController
import com.benomad.sample.map.DestinationMarkerManager
import com.benomad.sample.onboarding.OnboardingPreferences
import com.benomad.sample.route.RoutePlanner
import com.benomad.sample.route.VehicleProfileRepository

/**
 * Manual dependency-injection root for the BeNomad SDK objects.
 *
 * Created once in [com.benomad.sample.SampleApp.onCreate] and held for the whole
 * process lifetime. It replaces a DI framework (Hilt/Dagger) with a single, explicit
 * container so a client reading the sample can see exactly what is shared and where.
 *
 * SDK singletons (the online geocoder, route planner, navigation controller and GPS
 * source) are exposed here as lazy properties — they must be created only after
 * `Core.init(...)` has completed, so screens obtain them on demand rather than at
 * construction time.
 *
 * @property appContext the application [Context] (never an Activity) — the correct
 *   context for long-lived SDK objects such as `OnlineGeoCoder` and `Navigation`.
 */
class SdkProvider(internal val appContext: Context) {

    /** Persisted onboarding state (consent flag + the user-supplied license). */
    val onboardingPreferences: OnboardingPreferences by lazy { OnboardingPreferences(appContext) }

    /** Wraps the asynchronous `Core.init(...)` call (license + LBO) as a suspend function. */
    val sdkInitializer: SdkInitializer by lazy { SdkInitializer() }

    /** Observes map-data download/streaming progress (full vs hybrid is decided by Core). */
    val mapDataController: MapDataController by lazy { MapDataController() }

    /** Loads and caches the map style chart applied to the MapView. */
    val mapStyleProvider: MapStyleProvider by lazy { MapStyleProvider() }

    /** Device GPS source, bound to the navigation engine for tracking/guidance. */
    val gpsController: GpsController by lazy { GpsController(appContext) }

    /** Tracking + guidance wrapper around the Navigation singleton. */
    val navigationController: NavigationController by lazy { NavigationController(appContext, gpsController) }

    /** Online forward-geocoding / autocomplete engine for destination search. */
    val onlineGeoCoder: OnlineGeoCoder by lazy { OnlineGeoCoder(appContext) }

    /** Places departure/destination POI markers on the map. */
    val destinationMarkerManager: DestinationMarkerManager by lazy { DestinationMarkerManager() }

    /** Vehicle profiles imported from assets/vehicle_profiles.json. */
    val vehicleProfileRepository: VehicleProfileRepository by lazy { VehicleProfileRepository(appContext) }

    /** Single route-computation engine, kept for the whole app session. */
    val planner: Planner by lazy { Planner() }

    /** Computes routes (map-match + plan) as suspend functions. */
    val routePlanner: RoutePlanner by lazy { RoutePlanner(planner) }

    /**
     * One-shot hand-off from the route preview to the guidance screen: the preview writes the
     * selected [pendingRoute] + [pendingDemoMode] just before navigating ("Start"), and the
     * guidance screen reads them once on entry and clears [pendingRoute] (so a recreated guidance
     * ViewModel can't replay a stale route). Written/read on the main thread (ViewModel coroutines),
     * so no synchronization is needed.
     */
    var pendingRoute: Route? = null
    var pendingDemoMode: Boolean = false
}
