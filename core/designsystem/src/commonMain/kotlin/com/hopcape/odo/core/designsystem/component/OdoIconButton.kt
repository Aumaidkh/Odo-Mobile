package com.hopcape.odo.core.designsystem.component

import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A themed, tappable icon affordance — the standard control for top-bar actions,
 * inline row actions, and any "icon-only" tap target. It wraps Material's
 * [IconButton] (so ripple, focus, and the 48dp touch target come for free) and
 * skins it with brand ink, dimming to [OdoTheme.colors] `textMuted` when disabled.
 *
 * The common case takes an icon + its [contentDescription] directly:
 *
 * ```
 * OdoIconButton(vectorSettings, contentDescription = "Settings", onClick = vm::openSettings)
 * ```
 *
 * For anything richer (a badged icon, a custom glyph), use the content-slot
 * overload and put an [OdoIcon] inside.
 *
 * @param tint icon colour when enabled; defaults to the button's content colour.
 * @param size the drawn icon size; one of [OdoTheme.iconSizes].
 */
@Composable
fun OdoIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    size: Dp = OdoTheme.iconSizes.large,
) {
    OdoIconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        OdoIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint.takeOrElse { LocalContentColor.current },
            size = size,
        )
    }
}

/** [Painter] overload of the convenience [OdoIconButton]. */
@Composable
fun OdoIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    size: Dp = OdoTheme.iconSizes.large,
) {
    OdoIconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        OdoIcon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint.takeOrElse { LocalContentColor.current },
            size = size,
        )
    }
}

/**
 * Content-slot [OdoIconButton] — the primitive the convenience overloads build on.
 * Put an [OdoIcon] (or a small custom composable) inside; the slot inherits the
 * themed content colour so a plain [OdoIcon] tints correctly.
 */
@Composable
fun OdoIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = odoIconButtonColors(),
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        content = content,
    )
}

/** Brand colours for [OdoIconButton]: primary ink, dimming to `textMuted` disabled. */
@Composable
fun odoIconButtonColors(): IconButtonColors = IconButtonDefaults.iconButtonColors(
    contentColor = OdoTheme.colors.text,
    disabledContentColor = OdoTheme.colors.textMuted,
)

@OdoThemePreviews
@Composable
private fun OdoIconButtonPreview() = OdoPreview {
    // ColorPainter stands in for a real asset here — the module ships none.
    OdoIconButton(
        painter = ColorPainter(OdoTheme.colors.text),
        contentDescription = "Placeholder",
        onClick = {},
    )
}
