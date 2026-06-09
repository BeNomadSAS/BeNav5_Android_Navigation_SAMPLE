package com.benomad.sample.route

import android.content.Context
import android.util.Log
import com.benomad.msdk.core.carto.TranspMode
import com.benomad.msdk.vehiclemanager.model.Vehicle
import com.benomad.msdk.vehiclemanager.model.VehicleModel
import com.benomad.msdk.vehiclemanager.model.profile.MajorRoadsSpeedProfile
import com.benomad.msdk.vehiclemanager.model.profile.Profile
import com.benomad.msdk.vehiclemanager.model.profile.SpeedProfile
import org.json.JSONObject

/** A named vehicle profile parsed from the bundled JSON. */
data class SampleVehicleProfile(
    val id: String,
    val name: String,
    val profile: Profile,
)

/**
 * Loads the editable vehicle profiles from `assets/vehicle_profiles.json` and builds the
 * mSDK [Profile]/[Vehicle] objects routing needs — the sample's "import a profile" story,
 * with no database, RNC or settings UI.
 *
 * Units in the JSON: distances in METRES, weights in METRIC TONNES, speed in KM/H.
 * `transportationMode` is a [TranspMode] enum name (PASSENGER_CAR, DELIVERY_TRUCK,
 * PEDESTRIAN, BICYCLE, …). Truck dimensions/weights only take effect when the mode is a
 * truck mode AND the license allows it.
 */
class VehicleProfileRepository(private val appContext: Context) {

    /** Parsed once and cached. Falls back to a single car profile if the asset is unreadable/empty. */
    val profiles: List<SampleVehicleProfile> by lazy {
        runCatching { parseProfiles() }.getOrElse { emptyList() }.ifEmpty { listOf(defaultCar()) }
    }

    /** Wraps a profile into the [Vehicle] accepted by the route planner. */
    fun vehicleFor(profile: SampleVehicleProfile): Vehicle =
        Vehicle(
            id = profile.id,
            vehicleModel = VehicleModel(
                id = profile.id,
                name = profile.name,
                manufacturer = null,
                profile = profile.profile,
                variant = null,
                year = null,
            ),
        )

    private fun parseProfiles(): List<SampleVehicleProfile> {
        val json = appContext.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
        val array = JSONObject(json).getJSONArray("profiles")
        // Parse entries independently so one malformed profile doesn't discard the whole list.
        return (0 until array.length()).mapNotNull { index ->
            runCatching { parseProfile(array.getJSONObject(index)) }
                .onFailure { Log.w(TAG, "Skipping malformed vehicle profile at index $index", it) }
                .getOrNull()
        }
    }

    private fun parseProfile(json: JSONObject): SampleVehicleProfile {
        val mode = TranspMode.entries.firstOrNull { it.name == json.optString("transportationMode") }
            ?: TranspMode.PASSENGER_CAR
        val profile = Profile(
            transportationMode = mode,
            height = json.optDouble("heightM", 0.0),
            length = json.optDouble("lengthM", 0.0),
            width = json.optDouble("widthM", 0.0),
            weight = json.optDouble("weightT", 0.0),
            weightPerAxle = json.optDouble("weightPerAxleT", 0.0),
            // -1 is the SDK "unset" sentinel; default to 0 for a clean profile.
            nbTrailers = json.optInt("nbTrailers", 0),
            maxSpeed = json.optDouble("maxSpeedKmh", 0.0),
            // Bias trucks toward major roads when requested.
            speedProfile = if (json.optBoolean("majorRoads", false)) MajorRoadsSpeedProfile() else SpeedProfile(),
        )
        return SampleVehicleProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            profile = profile,
        )
    }

    private fun defaultCar(): SampleVehicleProfile =
        SampleVehicleProfile("car", "Car", Profile(transportationMode = TranspMode.PASSENGER_CAR, nbTrailers = 0))

    private companion object {
        const val ASSET_FILE = "vehicle_profiles.json"
        const val TAG = "VehicleProfileRepo"
    }
}
