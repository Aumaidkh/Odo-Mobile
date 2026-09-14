package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** How a choice card shows that it is the chosen one. */
enum class OdoOptionCardStyle {
    /** An icon tile, and a wash of the accent behind the label when picked. */
    Tile,

    /**
     * No tile, and the whole card turns accent when picked. For a list where the options
     * are sentences rather than outcomes and the icons would all have to be invented.
     */
    Filled,
}

/**
 * A full-width, tappable choice card: a label, an optional second line, and a check when
 * picked. Use it wherever a screen offers a short list of whole choices rather than a
 * compact radio list.
 *
 * ```
 * OdoOptionCard(
 *     label = "Stop overpaying",
 *     selected = goal == Goal.TRACK_COSTS,
 *     onClick = { onEvent(GoalSelected(Goal.TRACK_COSTS)) },
 *     icon = IcCurrencyDollar,
 * )
 * ```
 *
 * @param icon shown as a tile before the label in [OdoOptionCardStyle.Tile]. Null draws none.
 * @param description a second line under the label, for a choice that needs an example to be
 *   unambiguous ("Maruti Arena, Hyundai, Tata — authorised").
 * @param multiSelect `true` when the card is one of several the owner may pick, which changes
 *   only the accessibility role — a screen reader announces a checkbox rather than a radio.
 * @param selectedContentDescription read out for the check mark. Pass null in a list where
 *   selection is already announced by the role.
 */
@Composable
fun OdoOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    style: OdoOptionCardStyle = OdoOptionCardStyle.Tile,
    multiSelect: Boolean = false,
    enabled: Boolean = true,
    selectedContentDescription: String? = null,
) {
    val colors = OdoTheme.colors
    val shape = OdoTheme.shapes.card
    val filled = style == OdoOptionCardStyle.Filled
    val animation = tween<Color>(
        durationMillis = OdoTheme.motion.baseMillis,
        easing = OdoTheme.motion.easeStandard,
    )
    val targetContainer = when {
        selected && filled -> colors.accent
        selected -> colors.accent.copy(alpha = SELECTED_WASH)
        filled -> colors.surfaceRaised
        else -> colors.surface
    }
    val border by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.border,
        animationSpec = animation,
        label = "optionCardBorder",
    )
    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = animation,
        label = "optionCardContainer",
    )
    // The filled style inverts on selection, so every mark on it has to invert too.
    val onContainer = if (selected && filled) colors.onAccent else colors.text
    val onContainerDim = if (selected && filled) colors.onAccent.copy(alpha = DIM) else colors.textDim

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
        if (icon != null && !filled) {
            OdoIconTile(icon = icon, tint = if (selected) colors.accent else colors.textDim)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        ) {
            OdoText(text = label, style = OdoTheme.typography.heading, color = onContainer)
            if (description != null) {
                OdoText(
                    text = description,
                    style = OdoTheme.typography.bodySmall,
                    color = onContainerDim,
                )
            }
        }
        when {
            selected -> OdoIcon(
                IcCheck,
                contentDescription = selectedContentDescription,
                tint = if (filled) colors.onAccent else colors.accent,
                size = OdoTheme.iconSizes.large,
            )
            // An empty ring only where the card is otherwise unmarked: without it a filled
            // list reads as one card and two paragraphs rather than three choices.
            filled -> Box(
                modifier = Modifier
                    .size(OdoTheme.iconSizes.large)
                    .border(1.5.dp, colors.textMuted, CircleShape),
            )
        }
    }
}

object OdoOptionCardDefaults {
    /** Comfortably past the 48dp touch-target floor, and tall enough for a two-line label. */
    val MinHeight: Dp = 72.dp
}

private const val SELECTED_WASH = 0.10f

/** The second line against an inverted card — readable, but clearly the lesser of the two. */
private const val DIM = 0.65f

@OdoThemePreviews
@Composable
private fun OdoOptionCardPreview() = OdoPreview {
    OdoOptionCard(
        label = "Stop overpaying",
        selected = true,
        onClick = {},
        icon = IcCurrencyDollar,
    )
}

@OdoThemePreviews
@Composable
private fun OdoOptionCardUnselectedPreview() = OdoPreview {
    OdoOptionCard(
        label = "Keep the car healthy",
        selected = false,
        onClick = {},
        icon = IcSpeedometer,
    )
}

@OdoThemePreviews
@Composable
private fun OdoOptionCardFilledPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoOptionCard(
            label = "Company service centre",
            selected = true,
            onClick = {},
            description = "Maruti Arena, Hyundai, Tata — authorised",
            style = OdoOptionCardStyle.Filled,
        )
        OdoOptionCard(
            label = "Local garage",
            selected = false,
            onClick = {},
            description = "Neighbourhood mechanic or multi-brand",
            style = OdoOptionCardStyle.Filled,
        )
    }
}
