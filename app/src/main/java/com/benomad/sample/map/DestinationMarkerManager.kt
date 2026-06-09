package com.benomad.sample.map

import androidx.annotation.MainThread
import com.benomad.msdk.core.svs.DynamicLayersGroup
import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.core.svs.Point2D
import com.benomad.msdk.mapping.MapView
import com.benomad.sample.sdk.MapStyleProvider

/**
 * Places single departure / destination POI markers on the map.
 *
 * A "marker" in the mSDK is a point added to a [DynamicLayersGroup] under a class ID whose
 * icon was registered via `MapStyle.createPOIStyle(...)` (done in [MapStyleProvider]). The
 * flow is: attach the group to the [MapView] once → `newPoint(classId, point)` (keep the
 * returned [Point2D] to remove it later) → `redraw()`.
 *
 * The group is created lazily because `DynamicLayersGroup()` requires Core to be
 * initialized. The group is kept here (not in the View) so markers survive view recreation.
 */
class DestinationMarkerManager {

    // Created lazily: DynamicLayersGroup() reads Core.mapGeoScale(), so Core must be
    // initialized first. Kept here (not in the View) so markers survive view recreation.
    private val layers: DynamicLayersGroup by lazy { DynamicLayersGroup() }
    private var departureMarker: Point2D? = null
    private var destinationMarker: Point2D? = null

    /**
     * Binds the layer group to the (ready) map. Safe to call again for a recreated map;
     * a repeated call for the same map is a no-op inside the SDK.
     */
    @MainThread
    fun attach(mapView: MapView) {
        mapView.attachDynamicLayers(layers)
    }

    /** Shows (or moves) the destination marker, replacing any previous one. */
    @MainThread
    fun showDestination(mapView: MapView, point: GeoPoint) {
        // remove(classID, form, true) DETACHES the previous marker (it stays usable); the
        // remove(classID) overload would instead DESTROY every form in that layer.
        destinationMarker?.let { layers.remove(MapStyleProvider.DESTINATION_CLASS_ID, it, true) }
        destinationMarker = layers.newPoint(MapStyleProvider.DESTINATION_CLASS_ID, point, null)
        mapView.redraw(drawTexts = true, tryLock = false)
    }

    /** Shows (or moves) the departure marker, replacing any previous one. */
    @MainThread
    fun showDeparture(mapView: MapView, point: GeoPoint) {
        departureMarker?.let { layers.remove(MapStyleProvider.DEPARTURE_CLASS_ID, it, true) }
        departureMarker = layers.newPoint(MapStyleProvider.DEPARTURE_CLASS_ID, point, null)
        mapView.redraw(drawTexts = true, tryLock = false)
    }

    /** Removes the destination marker. */
    @MainThread
    fun clearDestination(mapView: MapView) {
        destinationMarker?.let { layers.remove(MapStyleProvider.DESTINATION_CLASS_ID, it, true) }
        destinationMarker = null
        mapView.redraw(drawTexts = true, tryLock = false)
    }

    /** Removes both markers. */
    @MainThread
    fun clearAll(mapView: MapView) {
        destinationMarker?.let { layers.remove(MapStyleProvider.DESTINATION_CLASS_ID, it, true) }
        departureMarker?.let { layers.remove(MapStyleProvider.DEPARTURE_CLASS_ID, it, true) }
        destinationMarker = null
        departureMarker = null
        mapView.redraw(drawTexts = true, tryLock = false)
    }
}
