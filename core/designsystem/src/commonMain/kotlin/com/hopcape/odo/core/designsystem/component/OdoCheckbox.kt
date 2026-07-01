package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The brand's checkbox — a themed Material [Checkbox] that fills with the accent
 * when ticked. Use it for independent, multi-select choices (accept terms, pick
 * several document types). For a labelled, fully-tappable row prefer
 * [OdoCheckboxRow].
 *
 * @param onCheckedChange pass `null` when a parent (e.g. [OdoCheckboxRow]) owns
 *   the toggle and the box only reflects state.
 */
@Composable
fun OdoCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = odoCheckboxColors(),
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}

/**
 * A full-width, tappable checkbox row: the box on the left, a [label] on the
 * right, and the whole row toggling the value with [Role.Checkbox] semantics.
 *
 * ```
 * OdoCheckboxRow(
 *     label = "Main confirm karta hoon ki details sahi hain",
 *     checked = confirmed,
 *     onCheckedChange = vm::setConfirmed,
 * )
 * ```
 */
@Composable
fun OdoCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoCheckbox(checked = checked, onCheckedChange = null, enabled = enabled)
        OdoText(
            label,
            style = OdoTheme.typography.body,
            color = if (enabled) OdoTheme.colors.text else OdoTheme.colors.textMuted,
        )
    }
}

/** Brand [CheckboxColors]: accent box when ticked, brand border/ink otherwise. */
@Composable
fun odoCheckboxColors(): CheckboxColors = CheckboxDefaults.colors(
    checkedColor = OdoTheme.colors.accent,
    uncheckedColor = OdoTheme.colors.border,
    checkmarkColor = OdoTheme.colors.onAccent,
    disabledCheckedColor = OdoTheme.colors.surfaceRaised,
    disabledUncheckedColor = OdoTheme.colors.border,
)

@OdoThemePreviews
@Composable
private fun OdoCheckboxRowPreview() = OdoPreview {
    OdoCheckboxRow(
        label = "Details sahi hain",
        checked = true,
        onCheckedChange = {},
    )
}
