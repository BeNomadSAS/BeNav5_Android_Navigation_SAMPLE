package com.benomad.sample.sdk

import android.content.Context
import android.graphics.Color
import com.benomad.msdk.errormanager.Error
import com.benomad.msdk.errormanager.enumerations.ErrorType
import com.benomad.msdk.mapping.style.MapStyle
import com.benomad.msdk.mapping.style.MapStyleLoader
import com.benomad.msdk.mapping.style.POIStyle
import com.benomad.msdk.mapping.style.PolylineStyle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt

/**
 * Loads and caches the bundled map style (a BeNomad graphic chart, `.cht`).
 *
 * A style is loaded once, app-wide, via [MapStyleLoader]. It must be loaded **after**
 * `Core.init` has run, because the chart + its PNG resources are deployed from
 * `assets/benomad_resources/` into the app's external files directory during init —
 * the chart sits at `<externalFilesDir>/day.cht` with the same directory as its base.
 *
 * The sample ships a single "Day" style to stay minimal; add a night chart the same way
 * if you want a dark style.
 */
class MapStyleProvider {

    /**
     * Loads the day style if not already loaded (idempotent). Returns `null` on success
     * or an [Error] if the chart could not be loaded (e.g. assets not deployed).
     * Runs file/native work off the main thread.
     */
    suspend fun load(appContext: Context): Error? = withContext(Dispatchers.IO) {
        if (MapStyleLoader.getStyle(STYLE_NAME) != null) return@withContext null
        // Surface a missing external dir as an Error (so the splash shows it) rather than
        // throwing an uncaught exception inside the coroutine.
        val baseDir = appContext.getExternalFilesDir(null) ?: return@withContext Error(
            ErrorType.CORE_RESOURCES,
            -1L,
            "External storage unavailable",
            "The external files directory is not available, so the map style cannot be loaded.",
        )
        val chartFile = File(baseDir, CHART_FILE)
        val loadError = MapStyleLoader.loadStyle(STYLE_NAME, chartFile, baseDir)
        if (loadError != null) return@withContext loadError
        // Register the marker + route polyline styles on the freshly loaded chart.
        registerLayerStyles(baseDir.path)
        null
    }

    /** The loaded style to hand to `MapView.setMapStyle(...)`, or null if not loaded yet. */
    fun style(): MapStyle? = MapStyleLoader.getStyle(STYLE_NAME)

    /**
     * Registers the dynamic-layer styles on the loaded chart: the departure/destination
     * marker icons and the route polyline.
     *
     * `POIStyle.icon` must be an ABSOLUTE path to a PNG; the icons are deployed under
     * `<externalFilesDir>/img/` by Core.init. The `createPOIStyle`/`createPolylineStyle`
     * returns are intentionally ignored: the only non-null case is a duplicate registration
     * for the same class ID, which is benign here.
     */
    private fun registerLayerStyles(baseDirPath: String) {
        // The style is always loaded by the time this runs (load() calls it only after a successful
        // loadStyle). Fail loudly if that invariant is ever broken, instead of silently skipping
        // the marker/route styles (which would surface later as missing icons/polylines).
        val style = checkNotNull(MapStyleLoader.getStyle(STYLE_NAME)) {
            "registerLayerStyles called before the style was loaded"
        }
        style.createPOIStyle(DEPARTURE_CLASS_ID, POIStyle(icon = "$baseDirPath/img/routestart.png"))
        style.createPOIStyle(DESTINATION_CLASS_ID, POIStyle(icon = "$baseDirPath/img/routestop.png"))
        // Selected route: solid blue, drawn on top (higher classID = higher z-index).
        style.createPolylineStyle(
            ROUTE_SELECTED_CLASS_ID,
            PolylineStyle(
                color = "#0049D6".toColorInt(),
                centerColor = "#0049D6".toColorInt(),
                borderColor = Color.WHITE,
                width = 8,
            ),
        )
        // Alternative routes: lighter blue, drawn beneath the selected route.
        style.createPolylineStyle(
            ROUTE_ALTERNATIVE_CLASS_ID,
            PolylineStyle(
                color = "#84A1D9".toColorInt(),
                centerColor = "#84A1D9".toColorInt(),
                borderColor = Color.WHITE,
                width = 6,
            ),
        )
    }

    companion object {
        /** Registry name of the day style. */
        const val STYLE_NAME = "Day"

        /** Chart file deployed by Core.init (from assets/benomad_resources/day.cht). */
        private const val CHART_FILE = "day.cht"

        /** Reserved class IDs whose icons (route start/stop) exist in the stock chart. */
        const val DEPARTURE_CLASS_ID = 53100L
        const val DESTINATION_CLASS_ID = 53101L

        // Custom class IDs (12000–12999) for the route polylines. The selected route has the
        // higher ID so it is always rendered on top of the alternatives.
        const val ROUTE_ALTERNATIVE_CLASS_ID = 12300L
        const val ROUTE_SELECTED_CLASS_ID = 12301L
    }
}
