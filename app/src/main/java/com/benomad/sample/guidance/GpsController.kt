package com.benomad.sample.guidance

import android.content.Context
import android.content.Intent
import androidx.annotation.MainThread
import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.gps.LocationDataSource
import com.benomad.msdk.gps.LocationFromBuiltInGPS
import com.benomad.msdk.gps.LocationFromRoute
import com.benomad.msdk.gps.manager.GPSManager
import com.benomad.msdk.navigation.Navigation
import com.benomad.msdk.planner.route.Route
import com.benomad.sample.sdk.isValidFix
import com.benomad.sample.sdk.toGeoPoint

/**
 * Owns the location sources and binds them to the navigation engine.
 *
 * The same [LocationDataSource] instance must be handed to BOTH [GPSManager.start] and the
 * [Navigation] engine ([bind]); the engine then advances the user puck / tracking camera.
 *
 * Two sources are used: the real device GPS ([deviceSource]) for tracking and real guidance,
 * and a simulated [LocationFromRoute] ([demoSource]) that drives along a planned route for
 * demo mode. Both go through the identical start/bind path.
 */
class GpsController(appContext: Context) {

    private val deviceGps = LocationFromBuiltInGPS(appContext)

    /** Whether the device GPS provider is turned on in system settings. */
    fun isGpsEnabled(): Boolean = deviceGps.isFeatureEnabled()

    /** Intent to open the system location-source settings (when GPS is off). */
    fun gpsActivationIntent(): Intent = deviceGps.getIntentForGPSActivation()

    /**
     * Last known device position as a [GeoPoint], or null if there is no valid fix.
     * Reads the device's last fix directly, so it works before tracking starts. Requires
     * location permission; returns null (caught) if it is not granted.
     */
    fun lastKnownPoint(): GeoPoint? = try {
        deviceGps.lastKnownLocation()?.takeIf { it.isValidFix }?.toGeoPoint()
    } catch (securityException: SecurityException) {
        null
    }

    /** The real device GPS source (tracking + real guidance). */
    fun deviceSource(): LocationDataSource = deviceGps

    /** A simulated source that drives along [route] at [DEMO_SPEED_PERCENT]% of the speed limits. */
    fun demoSource(route: Route): LocationDataSource = LocationFromRoute(route, DEMO_SPEED_PERCENT)

    /**
     * Starts the given [source]. MUST be called on the **main thread**: built-in GPS registers
     * a GNSS status callback, which builds a Handler on the calling thread and crashes on a
     * thread without a Looper.
     *
     * @return true if started; false if location permission is missing/revoked.
     */
    @MainThread
    fun start(source: LocationDataSource): Boolean = try {
        GPSManager.start(source)
    } catch (securityException: SecurityException) {
        false
    }

    /**
     * Binds [source] to [navigation] so the engine drives the puck/camera. Heavy native call
     * (it stops any running session before swapping the source) — run **off the main thread**.
     *
     * @return true once the engine is initialized with the source.
     */
    fun bind(navigation: Navigation, source: LocationDataSource): Boolean {
        val error = if (!navigation.isInit()) {
            navigation.initLocationDataSource(source)
        } else {
            // No context here: bind() swaps the source with no active guidance session, so there is
            // no foreground notification to dismiss (unlike NavigationController.stopGuidance, which
            // passes appContext to also tear down TTS + the notification).
            navigation.stopSession()
            navigation.changeLocationDataSource(source)
        }
        // initLocationDataSource / changeLocationDataSource can signal success with a non-null Error
        // whose code is 0 (undocumented — the KDoc says only "Error or null"); treat code 0 as success.
        return (error == null || error.code == 0L) && navigation.isInit()
    }

    /** Stops the active source. Like [start], runs on the main thread (it deregisters the GNSS callback). */
    @MainThread
    fun stop() = GPSManager.stop()

    private companion object {
        // Percent of the route's speed limits for the demo simulation (valid range [1,400]).
        const val DEMO_SPEED_PERCENT = 100
    }
}
