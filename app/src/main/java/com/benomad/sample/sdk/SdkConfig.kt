package com.benomad.sample.sdk

import com.benomad.msdk.core.svs.GeoPoint
import com.benomad.sample.BuildConfig

/**
 * Static configuration for the BeNomad SDK integration.
 *
 * The license values are *dev conveniences* read from `local.properties` at build
 * time (via [BuildConfig]); they are empty by default and the user can instead type
 * the license on the in-app license entry screen.
 */
object SdkConfig {

    /**
     * Name of the assets sub-folder that `Core.init(...)` deploys to scoped external
     * storage on first launch. It must contain the map style (`day.cht`/`day.xml`),
     * `Fonts/`, `img/` icons and `vehicle.png`. See `assets/benomad_resources/`.
     */
    const val RESOURCES_FOLDER_IN_ASSETS = "benomad_resources"

    /** Optional purchase UUID (license) from local.properties; empty if unset. */
    val defaultPurchaseUuid: String get() = BuildConfig.MSDK_PURCHASE_UUID

    /**
     * Default map centre / search bias, used when there is no GPS fix yet. Set this to a city
     * inside your licensed map coverage (default: Paris). Centralized here so a client retargeting
     * the sample edits one place instead of several.
     */
    val DEFAULT_MAP_CENTER: GeoPoint get() = geoPoint(2.348392, 48.853495)
}
