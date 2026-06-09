package com.benomad.sample

import android.net.Uri
import java.util.Locale

/**
 * Centralised navigation route identifiers used by [AppNavHost].
 *
 * The onboarding routes are visited in order by [com.benomad.sample.onboarding.OnboardingRouter]
 * (consent → license → permissions → splash) before reaching the app content.
 * Feature routes (search, route preview, guidance) are reached from the map.
 */
object AppRoutes {
    /** One-time license/terms consent. */
    const val CONSENT = "consent"

    /** License (purchase UUID) entry. */
    const val LICENSE = "license"

    /** Runtime location permission request. */
    const val LOCATION_PERMISSION = "location_permission"

    /** Runtime notification permission request (Android 13+ only). */
    const val NOTIFICATION_PERMISSION = "notification_permission"

    /** SDK initialization + map data download progress. */
    const val SPLASH = "splash"

    /** The interactive map — the app's main screen. */
    const val MAP = "map"

    /** Route preview. Destination passed as path args (lon/lat) plus a query label. */
    const val ROUTE_PREVIEW = "route_preview/{lon}/{lat}?label={label}"

    /** Active turn-by-turn guidance. */
    const val GUIDANCE = "guidance"

    /** Builds a concrete [ROUTE_PREVIEW] route for a destination. */
    fun routePreview(longitude: Double, latitude: Double, label: String): String {
        // Fixed locale + decimals: avoids scientific notation and locale-specific decimal
        // separators in the path (either would break the Double round-trip on the other side).
        val lon = String.format(Locale.US, "%.6f", longitude)
        val lat = String.format(Locale.US, "%.6f", latitude)
        return "route_preview/$lon/$lat?label=${Uri.encode(label)}"
    }
}
