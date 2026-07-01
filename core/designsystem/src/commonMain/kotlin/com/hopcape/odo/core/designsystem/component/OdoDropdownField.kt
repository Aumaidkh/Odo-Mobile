package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A themed **select / dropdown** field — the picker counterpart to [OdoInputField].
 * It reuses [OdoInputField] in read-only mode (so the field's shape, colours, label,
 * and error styling stay pixel-identical to a typed input) and drops a menu of
 * [options] beneath it, with a chevron that flips while it's open.
 *
 * Generic over the option type: pass any list and an [optionLabel] to render each
 * one. The selected value shows as the field's text; [placeholder] fills the gap
 * until one is chosen. Validation is surfaced exactly like [OdoInputField] — a
 * non-null [errorText] flips the field to its danger state.
 *
 * ```
 * OdoDropdownField(
 *     selected = form.make,
 *     options = state.makes,               // e.g. List<String>
 *     onSelect = { onEvent(MakeChanged(it)) },
 *     label = "Make",
 *     placeholder = "Choose",
 * )
 * OdoDropdownField(
 *     selected = form.year,
 *     options = state.years,               // e.g. List<Int>
 *     onSelect = { onEvent(YearChanged(it)) },
 *     optionLabel = { it.toString() },
 *     label = "Year",
 * )
 * ```
 *
 * @param optionLabel renders each option (and the current selection) as display text.
 * @param errorText when non-null, puts the field in its error state and shows below.
 */
@Composable
fun <T> OdoDropdownField(
    selected: T?,
    options: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    label: String? = null,
    placeholder: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OdoInputField(
            value = selected?.let(optionLabel).orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = label,
            placeholder = placeholder,
            errorText = errorText,
            enabled = enabled,
            readOnly = true,
            trailingIcon = { DropdownChevron(expanded) },
        )
        // A read-only text field swallows taps for focus, not for selection, so a
        // transparent overlay owns the "open the menu" gesture over the whole field.
        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { expanded = true },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { OdoText(optionLabel(option), style = OdoTheme.typography.body) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Down-chevron drawn in-house (no Material-icons dep, matching [OdoTopBar]); flips 180° when open. */
@Composable
private fun DropdownChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "dropdownChevron")
    val color = OdoTheme.colors.textDim
    Canvas(Modifier.size(OdoTheme.iconSizes.medium).rotate(rotation)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(w * 0.32f, h * 0.42f), Offset(w * 0.5f, h * 0.60f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.60f), Offset(w * 0.68f, h * 0.42f), stroke, cap = StrokeCap.Round)
    }
}

@OdoThemePreviews
@Composable
private fun OdoDropdownFieldPreview() = OdoPreview {
    var make by remember { mutableStateOf<String?>("Maruti Suzuki") }
    OdoDropdownField(
        selected = make,
        options = listOf("Maruti Suzuki", "Hyundai", "Tata"),
        onSelect = { make = it },
        label = "Make",
        placeholder = "Choose",
    )
}
