package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/** The distance unit an odometer reading is entered in — Km is the Indian default. */
enum class OdoDistanceUnit { KM, MILES }

/**
 * The odometer-reading input: a numeric [OdoInputField] with a tappable Km/Miles unit
 * toggle as its trailing content. The field stores the number; [unit] tracks how to
 * read it.
 *
 * Odometer is a first-class, mandatory field across Odo (it powers per-km cost, the
 * health score, and km-anomaly checks), so every screen that captures it — the manual
 * service-log form and the bill-scan review screen — enters it through this one
 * component instead of re-deriving the toggle.
 *
 * Copy-free like the rest of the design system: the caller supplies [kmLabel] /
 * [milesLabel] (and any [label] / [placeholder]) so localisation stays in the feature.
 */
@Composable
fun OdoOdometerField(
    value: String,
    onValueChange: (String) -> Unit,
    unit: OdoDistanceUnit,
    onUnitToggle: () -> Unit,
    kmLabel: String,
    milesLabel: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    OdoInputField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        errorText = errorText,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            UnitToggle(
                label = if (unit == OdoDistanceUnit.KM) kmLabel else milesLabel,
                onToggle = onUnitToggle,
            )
        },
    )
}

/** Trailing Km/Miles toggle — shows the current unit; a tap flips it. */
@Composable
private fun UnitToggle(label: String, onToggle: () -> Unit) {
    OdoText(
        text = label,
        style = OdoTheme.typography.label,
        color = OdoTheme.colors.accent,
        modifier = Modifier
            .clip(OdoTheme.shapes.pill)
            .clickable(onClick = onToggle)
            .padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
    )
}

@OdoThemePreviews
@Composable
private fun OdoOdometerFieldPreview() = OdoPreview {
    OdoOdometerField(
        value = "54000",
        onValueChange = {},
        unit = OdoDistanceUnit.KM,
        onUnitToggle = {},
        kmLabel = "Km",
        milesLabel = "Miles",
        label = "Odometer",
    )
}
