package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A compact, pill-shaped tappable chip — the primitive behind two recurring
 * patterns: **prompt chips** (tap a suggested Hinglish query like "Service due kab
 * hai?" to fill it in) and **filter chips** (a togglable facet in a filter row).
 * The [selected] flag switches it between the two looks; leave it `false` for a
 * plain prompt/assist chip.
 *
 * Unselected it's a neutral outlined pill; selected it fills with the accent and
 * shows a tick, so the choice reads at a glance in both themes.
 *
 * ```
 * // Prompt chip:
 * OdoChip("Service due kab hai?", onClick = { prefill(it) })
 *
 * // Filter chip:
 * OdoChip("Verified only", selected = onlyVerified, onClick = { onlyVerified = !onlyVerified })
 * ```
 *
 * @param selected renders the active/filled state and is exposed to accessibility.
 * @param leadingIcon optional glyph before the label; inherits the content colour.
 */
@Composable
fun OdoChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = OdoTheme.colors
    val spec = tween<Color>(OdoTheme.motion.baseMillis, easing = OdoTheme.motion.easeStandard)
    val contentColor by animateColorAsState(
        when {
            !enabled -> colors.textMuted
            selected -> colors.onAccent
            else -> colors.text
        },
        spec,
        label = "chipContent",
    )
    val containerColor by animateColorAsState(if (selected) colors.accent else colors.surface, spec, label = "chipFill")
    val borderColor by animateColorAsState(
        when {
            !enabled -> colors.border.copy(alpha = 0.5f)
            selected -> colors.accent
            else -> colors.border
        },
        spec,
        label = "chipBorder",
    )
    Surface(
        onClick = onClick,
        // Selected is a colour change and nothing else without this, so a screen reader
        // announces every chip identically and a test cannot tell which one is on.
        modifier = modifier.heightIn(min = 36.dp).semantics { this.selected = selected },
        enabled = enabled,
        shape = OdoTheme.shapes.pill,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = OdoTheme.spacing.md,
                vertical = OdoTheme.spacing.sm,
            ),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            } else {
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally() + fadeIn() + scaleIn(),
                    exit = shrinkHorizontally() + fadeOut() + scaleOut(),
                ) {
                    OdoIcon(IcCheck, contentDescription = null, size = OdoTheme.iconSizes.small)
                }
            }
            OdoText(label, style = OdoTheme.typography.label)
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoChipPreview() = OdoPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoChip("Service due kab hai?", onClick = {})
        OdoChip("Verified only", onClick = {}, selected = true)
    }
}
