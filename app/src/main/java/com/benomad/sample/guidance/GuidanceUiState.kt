package com.benomad.sample.guidance

import androidx.compose.ui.graphics.ImageBitmap

/**
 * UI state for the guidance screen, built from the Navigation listeners.
 *
 * @property maneuverIcon rendered next-maneuver symbol (from `onNewInstruction`), or null.
 * @property instruction next-maneuver text, or null.
 * @property distanceToNextText distance to the next maneuver (e.g. "300 m").
 * @property etaText estimated time of arrival ("HH:mm").
 * @property distanceToDestText remaining distance to the destination.
 * @property durationToDestText remaining driving time.
 * @property speedLimitText the speed limit as a bare integer string (e.g. "80"); the unit is km/h
 *   but is NOT part of the string — the badge renders it inside a circular icon. Null when unknown.
 * @property isReady true once the engine is on/off-route or estimating (UI gate).
 * @property isArrived the destination was reached.
 * @property isMuted voice guidance is muted.
 * @property isFollowing the 3D follow camera is active (false once the user pans the map).
 *   Held here (not in the composable) so it survives configuration changes.
 * @property isError guidance failed to start (GPS/permission denied, or an engine error); the
 *   screen shows an error overlay instead of the guidance UI.
 */
data class GuidanceUiState(
    val maneuverIcon: ImageBitmap? = null,
    val instruction: String? = null,
    val distanceToNextText: String? = null,
    val etaText: String? = null,
    val distanceToDestText: String? = null,
    val durationToDestText: String? = null,
    val speedLimitText: String? = null,
    val isReady: Boolean = false,
    val isArrived: Boolean = false,
    val isMuted: Boolean = false,
    val isFollowing: Boolean = true,
    val isError: Boolean = false,
)
