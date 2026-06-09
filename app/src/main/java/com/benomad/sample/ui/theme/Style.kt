package com.benomad.sample.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * App-level style tokens, kept here alongside the colour ([Blue40] et al.) and type
 * ([SampleTypography]) theme so a client can restyle the sample from one place.
 *
 * Material's `colorScheme` and `typography` already cover colours and text. These cover the floating
 * overlay cards' silhouette, which has no matching Material shape slot at this radius (so it is not
 * routed through `MaterialTheme.shapes` — that would also move the default Card / bottom-sheet
 * corners). Change these to restyle every overlay card at once: the guidance maneuver / route-info
 * bars and the route-option cards all reference them.
 */

/** Corner radius of the floating overlay cards. */
val SampleCardShape = RoundedCornerShape(10.dp)

/** Drop-shadow elevation of the floating overlay cards. */
val SampleCardElevation = 8.dp
