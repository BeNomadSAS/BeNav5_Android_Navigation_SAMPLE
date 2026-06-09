package com.benomad.sample.sdk

import android.location.Location
import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.errormanager.Error
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Geometry / formatting helpers centralised in one place.
 *
 * [GeoPoint] takes `(longitude, latitude)` — longitude first. This is documented (the constructor's
 * `@param lon` / `@param lat`), but it is the inverse of the common `(lat, lng)` convention
 * (`android.location.Location`, Google Maps, …) and the SDK does no range validation, so a swapped
 * pair silently lands elsewhere. Build every point through [geoPoint] / [Location.toGeoPoint] so the
 * order is fixed in one place.
 */

/** Builds a [GeoPoint] with the correct `(longitude, latitude)` argument order. */
fun geoPoint(longitude: Double, latitude: Double): GeoPoint = GeoPoint(longitude, latitude)

/** Converts an Android [Location] to a [GeoPoint] (longitude, latitude). */
fun Location.toGeoPoint(): GeoPoint = GeoPoint(longitude, latitude)

/** A fix at exactly (0, 0) usually means "no real fix yet" — treat it as invalid. */
val Location.isValidFix: Boolean get() = longitude != 0.0 || latitude != 0.0

/** Formats a distance in metres as e.g. "850 m" or "12.3 km". */
fun formatDistance(meters: Double): String =
    if (meters >= 1000.0) {
        String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)
    } else {
        "${meters.roundToInt()} m"
    }

/**
 * Formats the distance to the next maneuver, rounded into coarse "steps" so the on-screen value
 * changes at sensible boundaries instead of ticking down metre-by-metre as the SDK reports a new
 * distance on every progress update.
 *
 * Stepping: below 100 m snaps to the nearest 5 m, below ~1 km to the nearest 10 m, then switches to
 * kilometres (one decimal under 9.95 km, whole kilometres above). Because consecutive progress ticks
 * almost always fall in the same bucket, the produced string is usually identical from one tick to
 * the next — so the guidance UI only recomposes the maneuver distance when the displayed value
 * actually changes. Used for the next-maneuver distance only; the remaining-to-destination distance
 * keeps the plain [formatDistance].
 */
fun formatSteppedDistance(meters: Double): String =
    if (meters < METERS_TO_KM_THRESHOLD) {
        val step = if (meters < 100.0) 5 else 10
        "${(meters / step).roundToInt() * step} m"
    } else {
        val km = meters / 1000.0
        if (km < KM_ONE_DECIMAL_THRESHOLD) {
            String.format(Locale.getDefault(), "%.1f km", km)
        } else {
            // Deliberate upward bias (the + 0.5): for whole-kilometre distances we round UP so the
            // figure is never lower than the real remaining distance (we prefer to over- rather than
            // under-state it). Intentional design choice — keep as-is.
            "${(km + 0.5).roundToInt()} km"
        }
    }

// Above this many metres the maneuver distance is shown in kilometres rather than metres.
private const val METERS_TO_KM_THRESHOLD = 995.0

// Below this many kilometres the maneuver distance keeps one decimal ("3.2 km"); above it, whole km.
private const val KM_ONE_DECIMAL_THRESHOLD = 9.95

/** Formats a duration in seconds as e.g. "5 min" or "1 h 20 min". */
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

/**
 * Clock time of arrival, formatted as 24-hour "HH:mm" in the device's default time zone.
 *
 * @param durationSeconds remaining driving time in seconds (from `GuidanceProgress.drivingDurationToDest`).
 */
fun etaFromNow(durationSeconds: Int): String {
    val arrival = Date(System.currentTimeMillis() + durationSeconds * 1000L)
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(arrival)
}

/** Human-readable text for an SDK [Error], preferring the detailed message. */
internal fun Error.readableMessage(): String =
    detailedMessage.ifBlank { messageId }
