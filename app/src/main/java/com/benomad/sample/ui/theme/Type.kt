package com.benomad.sample.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's type scale: the system font in a **Light** weight throughout. We keep the Material 3
 * default sizes / line-heights and only override the weight, so the type hierarchy
 * (display / headline / title / body / label) stays intact while matching the brand's light look.
 */
private val Default = Typography()

val SampleTypography = Default.copy(
    displayLarge = Default.displayLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    displayMedium = Default.displayMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    displaySmall = Default.displaySmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    headlineLarge = Default.headlineLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    headlineMedium = Default.headlineMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    headlineSmall = Default.headlineSmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    titleLarge = Default.titleLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    titleMedium = Default.titleMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    titleSmall = Default.titleSmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    bodyLarge = Default.bodyLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    bodyMedium = Default.bodyMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    bodySmall = Default.bodySmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    labelLarge = Default.labelLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    labelMedium = Default.labelMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
    labelSmall = Default.labelSmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light),
)
