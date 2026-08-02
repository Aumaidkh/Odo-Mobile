package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat

/** The distance unit an odometer reading is entered in — Km is the Indian default. */
enum class OdoDistanceUnit { KM, MILES }

/**
 * The odometer-reading input: a numeric [OdoInputField] labelled with the owner's distance
 * unit. The field carries the number; [unit] says what it means.
 *
 * Odometer is a first-class, mandatory field across Odo (it powers per-km cost, the
 * health score, and km-anomaly checks), so every screen that captures it — the manual
 * service-log form and the bill-scan review screen — enters it through this one
 * component.
 *
 * The unit is **not** switchable here. It is one setting for the whole app, changed on the
 * profile, so a second control on a form would be a second answer to the same question —
 * and the number in the field would silently change meaning under it.
 *
 * Copy-free like the rest of the design system: the caller supplies [kmLabel] /
 * [milesLabel] (and any [label] / [placeholder]) so localisation stays in the feature.
 */
@Composable
fun OdoOdometerField(
    value: String,
    onValueChange: (String) -> Unit,
    kmLabel: String,
    milesLabel: String,
    modifier: Modifier = Modifier,
    unit: OdoDistanceUnit = LocalOdoDistanceFormat.current.unit,
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
            OdoText(
                text = if (unit == OdoDistanceUnit.KM) kmLabel else milesLabel,
                style = OdoTheme.typography.label,
                color = OdoTheme.colors.textDim,
                modifier = Modifier.padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
            )
        },
    )
}

@OdoThemePreviews
@Composable
private fun OdoOdometerFieldPreview() = OdoPreview {
    OdoOdometerField(
        value = "54000",
        onValueChange = {},
        kmLabel = "Km",
        milesLabel = "Miles",
        label = "Odometer",
    )
}
