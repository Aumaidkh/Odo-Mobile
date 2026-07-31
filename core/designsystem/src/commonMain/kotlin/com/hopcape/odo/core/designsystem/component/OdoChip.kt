package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
 * Unselected it's a neutral outlined pill; selected it fills with a low-alpha
 * accent wash and an accent outline, so an active filter reads at a glance.
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
    val contentColor = when {
        !enabled -> colors.textMuted
        selected -> colors.accent
        else -> colors.text
    }
    val containerColor = if (selected) colors.accent.copy(alpha = 0.16f) else colors.surface
    val borderColor = when {
        !enabled -> colors.border.copy(alpha = 0.5f)
        selected -> colors.accent
        else -> colors.border
    }
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
            leadingIcon?.invoke()
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
