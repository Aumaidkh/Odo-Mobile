package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/** The disc treatment behind an [OdoCircularIconButton]. */
enum class OdoCircularIconButtonVariant {
    /** Flat surface disc, no outline — the default back affordance on a screen. */
    Surface,

    /** Raised surface disc — for use *on top of* a card or already-surfaced area. */
    Raised,

    /** Surface disc with a hairline outline — for a bare background needing definition. */
    Outlined,
}

/** Defaults for [OdoCircularIconButton]. */
object OdoCircularIconButtonDefaults {
    /** Drawn diameter. The touch target is expanded to 48dp regardless. */
    val Size: Dp = 40.dp
}

/**
 * The circular icon affordance — a soft disc with an icon centred in it, used for back
 * arrows, close buttons, and the round actions in a screen's header.
 *
 * **Use this instead of hand-rolling a `Box` + `CircleShape` + `clickable`.** Every
 * screen that needs a round back button takes it from here, so size, fill, ripple, and
 * touch target stay identical across features.
 *
 * This is what [OdoTopBar] renders for back, and what its `actions` slot should be given,
 * so a header reads as one row of round controls. Distinct from [OdoIconButton], the
 * *bare* icon control for inline row actions — that one has no disc behind it.
 *
 * ```
 * OdoCircularIconButton(IcArrowLeft, contentDescription = "Back", onClick = onBack)
 * ```
 *
 * The drawn diameter defaults to 40dp, but the tappable area is always expanded to the
 * 48dp accessibility floor via [minimumInteractiveComponentSize] — so a small disc never
 * costs a small target, which the per-feature copies this replaces all got wrong.
 *
 * @param variant the disc treatment; see [OdoCircularIconButtonVariant].
 * @param size the drawn diameter — not the touch target.
 * @param iconSize the glyph size inside the disc; one of [OdoTheme.iconSizes].
 */
@Composable
fun OdoCircularIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: OdoCircularIconButtonVariant = OdoCircularIconButtonVariant.Surface,
    size: Dp = OdoCircularIconButtonDefaults.Size,
    iconSize: Dp = OdoTheme.iconSizes.medium,
) {
    val containerColor = when (variant) {
        OdoCircularIconButtonVariant.Surface, OdoCircularIconButtonVariant.Outlined -> OdoTheme.colors.surface
        OdoCircularIconButtonVariant.Raised -> OdoTheme.colors.surfaceRaised
    }
    val tint = if (enabled) OdoTheme.colors.text else OdoTheme.colors.textMuted

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (variant == OdoCircularIconButtonVariant.Outlined) {
                    Modifier.border(OutlineWidth, OdoTheme.colors.border, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            size = iconSize,
        )
    }
}

private val OutlineWidth = 1.dp

@OdoThemePreviews
@Composable
private fun OdoCircularIconButtonPreview() = OdoPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoCircularIconButton(IcArrowLeft, contentDescription = "Back", onClick = {})
        OdoCircularIconButton(
            IcInfo,
            contentDescription = "Info",
            onClick = {},
            variant = OdoCircularIconButtonVariant.Outlined,
        )
        OdoCircularIconButton(
            IcClose,
            contentDescription = "Close",
            onClick = {},
            variant = OdoCircularIconButtonVariant.Raised,
        )
        OdoCircularIconButton(
            IcArrowLeft,
            contentDescription = "Back",
            onClick = {},
            enabled = false,
        )
    }
}
