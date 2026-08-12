package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The brand's radio button — a themed Material [RadioButton] that fills with the
 * accent when selected. Use it for mutually-exclusive choices (fuel type, an
 * onboarding goal). For a labelled, fully-tappable option prefer [OdoRadioRow];
 * group several rows under one `selectableGroup()` so a screen reader announces
 * "1 of N".
 *
 * @param onClick pass `null` when a parent (e.g. [OdoRadioRow]) owns the selection.
 */
@Composable
fun OdoRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = odoRadioButtonColors(),
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}

/**
 * A full-width, tappable radio option: the control on the left, a [label] (plus
 * optional [supporting] line) on the right, and the whole row selecting the value
 * with [Role.RadioButton] semantics.
 *
 * ```
 * Column(Modifier.selectableGroup()) {
 *     OdoRadioRow("Petrol", selected = fuel == PETROL, onClick = { fuel = PETROL })
 *     OdoRadioRow("Diesel", selected = fuel == DIESEL, onClick = { fuel = DIESEL })
 * }
 * ```
 */
@Composable
fun OdoRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoRadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            OdoText(
                label,
                style = OdoTheme.typography.body,
                color = if (enabled) OdoTheme.colors.text else OdoTheme.colors.textMuted,
            )
            if (supporting != null) {
                OdoText(
                    supporting,
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

/** Brand [RadioButtonColors]: accent when selected, brand border when not. */
@Composable
fun odoRadioButtonColors(): RadioButtonColors = RadioButtonDefaults.colors(
    selectedColor = OdoTheme.colors.accent,
    unselectedColor = OdoTheme.colors.border,
    disabledSelectedColor = OdoTheme.colors.surfaceRaised,
    disabledUnselectedColor = OdoTheme.colors.border,
)

@OdoThemePreviews
@Composable
private fun OdoRadioRowPreview() = OdoPreview {
    Column {
        OdoRadioRow("Petrol", selected = true, onClick = {})
        OdoRadioRow("Diesel", selected = false, onClick = {})
    }
}
