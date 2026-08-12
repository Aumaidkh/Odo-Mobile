package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Odo's icon primitive — a themed wrapper over Material's [Icon] that defaults the
 * tint to the ambient [LocalContentColor] and the box to [OdoTheme.iconSizes]
 * `large`, so icons pick up the surrounding ink and a consistent size without each
 * call site restating them. (The design system ships no Material-icons *asset*
 * dependency; feed it an [ImageVector]/[Painter] from `composeResources` or a
 * hand-built vector.)
 *
 * Pass a [contentDescription] for a meaningful icon (a screen reader reads it);
 * pass `null` for a purely decorative one sitting next to its own label.
 *
 * ```
 * OdoIcon(vectorScan, contentDescription = "Scan bill")
 * OdoIcon(painterResource(Res.drawable.ic_check), contentDescription = null,
 *         tint = OdoTheme.colors.success, size = OdoTheme.iconSizes.small)
 * ```
 *
 * @param tint icon colour; defaults to the inherited content colour.
 * @param size the square box the icon is drawn in; one of [OdoTheme.iconSizes].
 */
@Composable
fun OdoIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = OdoTheme.iconSizes.large,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** [Painter] overload of [OdoIcon], for raster or resource-backed icons. */
@Composable
fun OdoIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = OdoTheme.iconSizes.large,
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@OdoThemePreviews
@Composable
private fun OdoIconPreview() = OdoPreview {
    // ColorPainter stands in for a real asset here — the module ships none.
    OdoIcon(ColorPainter(OdoTheme.colors.accent), contentDescription = "Placeholder")
}
