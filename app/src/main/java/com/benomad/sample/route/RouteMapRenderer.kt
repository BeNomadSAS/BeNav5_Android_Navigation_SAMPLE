package com.benomad.sample.route

import androidx.annotation.MainThread
import com.benomad.msdk.core.svs.DynamicLayersGroup
import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.msdk.core.svs.Point2D
import com.benomad.msdk.core.svs.Polyline2D
import com.benomad.msdk.mapping.MapView
import com.benomad.msdk.planner.route.Route
import com.benomad.sample.sdk.MapStyleProvider

/**
 * Draws the computed routes on a preview map: every route's polyline (the selected one on top in
 * the selected style, the alternatives beneath in the alternative style) plus the
 * departure/destination markers, framed to the selected route.
 *
 * ### `DynamicLayersGroup` form-ownership rules that shape this class
 * 1. **A [Form] belongs to exactly one [DynamicLayersGroup], under exactly one classID at a time**
 *    (documented on `DynamicLayersGroup.addForm`). `remove(classID, form, true)` only succeeds if the
 *    form is currently in *that* classID's layer — calling it for the wrong class logs "Form does not
 *    belong to a DynamicLayersGroup" / "Form couldn't be removed from the layer corresponding to the
 *    classID". So we remember which class each polyline is in and only detach it from *that* class.
 * 2. **Forms persist in the group across `MapView` recreation.** The group is owned by the
 *    ViewModel (survives rotation) and simply re-bound to the freshly created `MapView` via
 *    [attach] in `onMapReady`; the forms it already holds are re-rendered automatically. Re-adding
 *    them would log "Form is already added to a DynamicLayersGroup". So on a pure rotation we touch
 *    **no** forms — we only re-frame the camera.
 * 3. **`remove(classID)` (no form) DESTROYS the forms; `remove(classID, form, true)` only DETACHES**
 *    them (still re-addable). We use the detaching form to move a polyline between the selected and
 *    alternative classes when the selection changes.
 *
 * [showRoutes] is therefore driven by *what changed* — the route set, the selection, or just the
 * view — never by blindly clearing and redrawing.
 */
class RouteMapRenderer {

    private val layers: DynamicLayersGroup by lazy { DynamicLayersGroup() }

    private var drawnRoutes: List<Route> = emptyList()
    /** The polyline form of each drawn route (null for a degenerate, zero-geometry route). */
    private var polylines: List<Polyline2D?> = emptyList()
    /** The classID each polyline is currently added under (-1 = not added), parallel to [polylines]. */
    private var polylineClassIds: LongArray = LongArray(0)
    private var departureMarker: Point2D? = null
    private var destinationMarker: Point2D? = null
    private var lastSelectedIndex: Int = -1
    private var lastFramedMapView: MapView? = null

    /** Binds the (persistent) layer group to a — possibly freshly recreated — [MapView]. */
    @MainThread
    fun attach(mapView: MapView) {
        mapView.attachDynamicLayers(layers)
    }

    /**
     * Draws [routes] with the route at [selectedIndex] highlighted, plus endpoint markers, and
     * frames the camera to the selected route. Safe to call repeatedly: it reacts only to what
     * actually changed since the last call.
     *
     * [start] and [end] are assumed stable for the lifetime of a preview (the ViewModel captures the
     * departure once — see `RoutePreviewViewModel.compute`), so the endpoint markers are added once
     * and never re-positioned.
     */
    @MainThread
    fun showRoutes(
        mapView: MapView,
        routes: List<Route>,
        selectedIndex: Int,
        start: GeoPoint,
        end: GeoPoint,
    ) {
        val routeSetChanged = routes !== drawnRoutes
        val selectionChanged = selectedIndex != lastSelectedIndex
        val mapRecreated = mapView !== lastFramedMapView

        if (routeSetChanged) {
            // New route set (e.g. a profile change recomputed the routes): detach the previous
            // polylines from the classes they are in, then add the new ones. Capture each new
            // polyline form exactly once (the getter may allocate).
            removeAllPolylines()
            polylines = routes.map { it.polyline2D }
            polylineClassIds = LongArray(polylines.size) { NOT_ADDED }
            drawnRoutes = routes
            addPolylines(selectedIndex)
        } else if (selectionChanged) {
            // Same forms, different highlight: move only the polylines whose class changed between
            // the selected (top) and alternative classes.
            reclassifyPolylines(selectedIndex)
        }
        // else (pure rotation / no change): the forms already live in the group and are re-rendered
        // by the re-bound view — nothing to add or remove.

        // Markers are fixed for a preview (same departure/destination), so add them once; they
        // then persist in the group across selection changes and rotations.
        if (departureMarker == null) {
            departureMarker = layers.newPoint(MapStyleProvider.DEPARTURE_CLASS_ID, start, null)
        }
        if (destinationMarker == null) {
            destinationMarker = layers.newPoint(MapStyleProvider.DESTINATION_CLASS_ID, end, null)
        }

        mapView.redraw(drawTexts = true, tryLock = false)

        // Frame the selected route when the route set changed or the map was recreated (rotation) —
        // NOT on a selection-only change, so the user's manual pan is kept. On rotation the MapView
        // is recreated from scratch, so the prior pan/zoom is gone regardless; reframing to the
        // route bounds is the correct recovery, not a loss of preserved state.
        if (routeSetChanged || mapRecreated) {
            routes.getOrNull(selectedIndex)?.boundingBox?.let { mapView.zoomToRect(it, true) }
        }
        lastSelectedIndex = selectedIndex
        lastFramedMapView = mapView
    }

    /** The class a route's polyline should be in for the given selection. */
    private fun classFor(index: Int, selectedIndex: Int): Long =
        if (index == selectedIndex) MapStyleProvider.ROUTE_SELECTED_CLASS_ID
        else MapStyleProvider.ROUTE_ALTERNATIVE_CLASS_ID

    /** Adds every captured polyline under its class for [selectedIndex] and records the class. */
    @MainThread
    private fun addPolylines(selectedIndex: Int) {
        polylines.forEachIndexed { index, polyline ->
            polyline ?: return@forEachIndexed
            val classId = classFor(index, selectedIndex)
            layers.addForm(classId, polyline, null)
            polylineClassIds[index] = classId
        }
    }

    /** Moves only the polylines whose target class differs from where they currently are. */
    @MainThread
    private fun reclassifyPolylines(selectedIndex: Int) {
        polylines.forEachIndexed { index, polyline ->
            polyline ?: return@forEachIndexed
            val target = classFor(index, selectedIndex)
            val current = polylineClassIds[index]
            if (current != target) {
                // Detach from the class it is actually in (true = keep the form re-addable)…
                if (current != NOT_ADDED) layers.remove(current, polyline, true)
                // …and add it under the new class.
                layers.addForm(target, polyline, null)
                polylineClassIds[index] = target
            }
        }
    }

    /** Detaches all currently-drawn polylines from the exact classes they were added under. */
    @MainThread
    private fun removeAllPolylines() {
        polylines.forEachIndexed { index, polyline ->
            polyline ?: return@forEachIndexed
            val current = polylineClassIds.getOrElse(index) { NOT_ADDED }
            if (current != NOT_ADDED) layers.remove(current, polyline, true)
        }
    }

    private companion object {
        const val NOT_ADDED = -1L
    }
}
