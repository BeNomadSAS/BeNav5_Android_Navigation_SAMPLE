package com.benomad.sample.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's standard map/overlay control button: a default 48 dp [IconButton] with a soft shadow,
 * a surface-coloured circular background, and an on-surface icon tint. Used for the map controls
 * (zoom / reset-north / recenter) and the guidance recenter button, so they all share one look.
 *
 * [tint], [shape] and [elevation] default to the standard circular look but can be overridden per
 * call so a client can re-skin the button without forking it.
 */
@Composable
fun CircularIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = CircleShape,
    elevation: Dp = 8.dp,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .shadow(elevation = elevation, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}

/** [CircularIconButton] variant taking a drawable [Painter] (e.g. the brand recenter icon). */
@Composable
fun CircularIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = CircleShape,
    elevation: Dp = 8.dp,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .shadow(elevation = elevation, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Icon(painter = painter, contentDescription = contentDescription, tint = tint)
    }
}
