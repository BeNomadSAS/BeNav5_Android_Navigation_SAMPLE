package com.benomad.sample.guidance

import android.content.Context
import android.graphics.Color
import com.benomad.msdk.navigation.icon.InstructionsIconsStyles
import com.benomad.msdk.planner.symbolicmaneuver.SymbolicManeuverStyle

/**
 * Builds the symbolic-maneuver icon style used during guidance.
 *
 * Passing a non-null [InstructionsIconsStyles] to `Navigation.startSession` is what makes the
 * engine render the maneuver bitmap delivered in `InstructionsListener.onNewInstruction`
 * (without it, those bitmaps are always null and you only get text).
 *
 * Note: [SymbolicManeuverStyle]'s default `backgroundColor` is **opaque blue**, which looks
 * wrong on a light maneuver bar — we override it to transparent so only the arrow shows.
 */
object GuidanceStyles {

    fun instructionsIconsStyles(context: Context): InstructionsIconsStyles {
        val sizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        return InstructionsIconsStyles(
            maneuverStyle = SymbolicManeuverStyle(
                maxWidth = sizePx,
                maxHeight = sizePx,
                backgroundColor = Color.TRANSPARENT,
                penColor = Color.GRAY,   // segments the maneuver does NOT follow
                routePenColor = Color.BLACK, // the segment the maneuver follows (visible on a light bar)
            ),
            useDarkModeIcons = false,
        )
    }

    // Shared with the guidance maneuver bar so the engine-rendered bitmap and the Compose Image
    // that displays it are the same logical size (they must not drift apart).
    internal const val ICON_SIZE_DP = 56
}
