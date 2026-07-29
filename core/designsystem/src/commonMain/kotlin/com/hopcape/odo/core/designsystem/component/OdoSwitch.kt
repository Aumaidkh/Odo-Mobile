package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The brand's on/off toggle — a themed Material [Switch] whose "on" track is the
 * odometer-orange accent. Use it for boolean settings (a per-type reminder,
 * push notifications). For a labelled setting row, prefer [OdoSwitchRow], which
 * makes the whole row tappable.
 *
 * @param onCheckedChange pass `null` to make the switch read-only / parent-driven
 *   (e.g. inside [OdoSwitchRow], where the row owns the click).
 */
@Composable
fun OdoSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = odoSwitchColors(),
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}

/**
 * A full-width settings row: a [label] (plus optional [supporting] helper line) on
 * the left and an [OdoSwitch] on the right, with the **entire row** toggling the
 * value (larger, more forgiving touch target). The row carries the
 * [Role.Switch] semantics, so the inner switch is not separately focusable.
 *
 * ```
 * OdoSwitchRow(
 *     label = "Insurance reminders",
 *     supporting = "30, 7 aur 1 din pehle",
 *     checked = remindInsurance,
 *     onCheckedChange = vm::setInsuranceReminders,
 * )
 * ```
 */
@Composable
fun OdoSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = OdoTheme.spacing.md)
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            OdoText(
                label,
                style = OdoTheme.typography.body,
                color = if (enabled) OdoTheme.colors.text else OdoTheme.colors.textMuted,
            )
            if (supporting != null) {
                OdoText(
                    modifier = Modifier.padding(top = OdoTheme.spacing.xs, bottom = OdoTheme.spacing.sm),
                    text = supporting,
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
        // Row owns the toggle; the switch just reflects state (null callback).
        OdoSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Brand [SwitchColors]: accent track when on, muted tokens when off/disabled. */
@Composable
fun odoSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = OdoTheme.colors.onAccent,
    checkedTrackColor = OdoTheme.colors.accent,
    checkedBorderColor = OdoTheme.colors.accent,
    uncheckedThumbColor = OdoTheme.colors.textDim,
    uncheckedTrackColor = OdoTheme.colors.surfaceRaised,
    uncheckedBorderColor = OdoTheme.colors.border,
    disabledCheckedTrackColor = OdoTheme.colors.surfaceRaised,
    disabledUncheckedTrackColor = OdoTheme.colors.surfaceRaised,
)

@OdoThemePreviews
@Composable
private fun OdoSwitchRowPreview() = OdoPreview {
    OdoSwitchRow(
        label = "Insurance reminders",
        supporting = "30, 7 aur 1 din pehle",
        checked = true,
        onCheckedChange = {},
    )
}
