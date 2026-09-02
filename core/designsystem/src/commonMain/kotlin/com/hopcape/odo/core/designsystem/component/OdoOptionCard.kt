package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcCurrencyDollar
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A full-width, tappable choice card: an icon tile, a label, and an accent wash plus a check
 * when picked. Use it wherever a screen offers a short list of whole-outcome choices rather
 * than a compact radio list.
 *
 * ```
 * OdoOptionCard(
 *     label = "Stop overpaying",
 *     icon = IcCurrencyDollar,
 *     selected = goal == Goal.TRACK_COSTS,
 *     onClick = { onEvent(GoalSelected(Goal.TRACK_COSTS)) },
 * )
 * ```
 *
 * @param multiSelect `true` when the card is one of several the owner may pick, which changes
 *   only the accessibility role — a screen reader announces a checkbox rather than a radio.
 * @param selectedContentDescription read out for the check mark. Pass null in a list where
 *   selection is already announced by the role.
 */
@Composable
fun OdoOptionCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    enabled: Boolean = true,
    selectedContentDescription: String? = null,
) {
    val colors = OdoTheme.colors
    val shape = OdoTheme.shapes.card
    val animation = tween<androidx.compose.ui.graphics.Color>(
        durationMillis = OdoTheme.motion.baseMillis,
        easing = OdoTheme.motion.easeStandard,
    )
    val border by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.border,
        animationSpec = animation,
        label = "optionCardBorder",
    )
    val container by animateColorAsState(
        targetValue = if (selected) colors.accent.copy(alpha = SELECTED_WASH) else colors.surface,
        animationSpec = animation,
        label = "optionCardContainer",
    )

    // toggleable and selectable differ only in the role they announce, but Compose has no one
    // modifier that takes the role as data, so the branch is here rather than at every caller.
    val selection = if (multiSelect) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() }
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = OdoOptionCardDefaults.MinHeight)
            .clip(shape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .then(selection)
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIconTile(icon = icon, tint = if (selected) colors.accent else colors.textDim)
        OdoText(
            text = label,
            style = OdoTheme.typography.heading,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            OdoIcon(
                IcCheck,
                contentDescription = selectedContentDescription,
                tint = colors.accent,
                size = OdoTheme.iconSizes.large,
            )
        }
    }
}

object OdoOptionCardDefaults {
    /** Comfortably past the 48dp touch-target floor, and tall enough for a two-line label. */
    val MinHeight: Dp = 72.dp
}

private const val SELECTED_WASH = 0.10f

@OdoThemePreviews
@Composable
private fun OdoOptionCardPreview() = OdoPreview {
    OdoOptionCard(
        label = "Stop overpaying",
        icon = IcCurrencyDollar,
        selected = true,
        onClick = {},
    )
}

@OdoThemePreviews
@Composable
private fun OdoOptionCardUnselectedPreview() = OdoPreview {
    OdoOptionCard(
        label = "Keep the car healthy",
        icon = IcSpeedometer,
        selected = false,
        onClick = {},
    )
}
