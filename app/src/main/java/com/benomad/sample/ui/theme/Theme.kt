package com.benomad.sample.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Color.DarkGray,
    inverseSurface = Ink,
    inverseOnSurface = Color.White,
    background = Color.White,
    onBackground = Ink,
    outline = Dust,
    surfaceContainer = Blue60,
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Ink,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    surface = Ink,
    onSurface = Color.White,
    onSurfaceVariant = Color.LightGray,
    inverseSurface = Color.White,
    inverseOnSurface = Ink,
    background = Ink,
    onBackground = Color.White,
    outline = Dust,
    surfaceContainer = Blue60,
)

/**
 * Material 3 theme for the sample. Follows the system light/dark setting. Dynamic colour is
 * intentionally omitted so the brand look is consistent across devices.
 */
@Composable
fun BeNomadSampleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SampleTypography,
        content = content,
    )
}
