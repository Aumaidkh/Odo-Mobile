package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Odo's themed single-line/multi-line text input — an [OutlinedTextField] skinned
 * with the brand's field shape, colours, and type. Use it for every form field
 * (odometer reading, workshop name, cost) so focus/error styling and the label +
 * helper/error copy layout stay identical across features.
 *
 * Validation is surfaced through [errorText]: pass a non-null message and the
 * field flips to its danger colours and shows that message underneath; leave it
 * null and [helperText] (if any) is shown in dimmed ink instead. The two never
 * show at once — an error replaces the helper.
 *
 * ```
 * OdoInputField(
 *     value = odometer,
 *     onValueChange = vm::onOdometer,
 *     label = "Odometer reading",
 *     placeholder = "e.g. 42,500",
 *     helperText = "Har entry me zaroori hai",
 *     errorText = odometerError,   // null until invalid
 *     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
 * )
 * ```
 *
 * @param errorText when non-null, puts the field in its error state and shows this
 *   below it (takes precedence over [helperText]).
 * @param leadingIcon / [trailingIcon] optional decorations inside the field.
 * @param singleLine keeps the field one line; set false and raise [maxLines] for
 *   multi-line input (e.g. notes).
 */
@Composable
fun OdoInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    colors: TextFieldColors = odoTextFieldColors(),
) {
    val isError = errorText != null
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = OdoTheme.typography.body,
            label = label?.let { { OdoText(it, style = OdoTheme.typography.label) } },
            placeholder = placeholder?.let {
                { OdoText(it, style = OdoTheme.typography.body, color = OdoTheme.colors.textMuted) }
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            shape = OdoTheme.shapes.field,
            colors = colors,
        )
        // Error replaces helper — never both. Nothing is drawn when both are null.
        val supporting = errorText ?: helperText
        if (supporting != null) {
            OdoText(
                text = supporting,
                style = OdoTheme.typography.bodySmall,
                color = if (isError) OdoTheme.colors.danger else OdoTheme.colors.textDim,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.lg),
            )
        }
    }
}

/**
 * Brand colours for [OdoInputField], mapping the `--odo-*` tokens onto every
 * [OutlinedTextField] slot (container, outline, label, cursor, error). Focused
 * outline + cursor use the odometer-orange accent; the error state uses `danger`.
 */
@Composable
fun odoTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OdoTheme.colors.text,
    unfocusedTextColor = OdoTheme.colors.text,
    disabledTextColor = OdoTheme.colors.textMuted,
    errorTextColor = OdoTheme.colors.text,
    focusedContainerColor = OdoTheme.colors.surface,
    unfocusedContainerColor = OdoTheme.colors.surface,
    disabledContainerColor = OdoTheme.colors.surface,
    errorContainerColor = OdoTheme.colors.surface,
    cursorColor = OdoTheme.colors.accent,
    errorCursorColor = OdoTheme.colors.danger,
    focusedBorderColor = OdoTheme.colors.accent,
    unfocusedBorderColor = OdoTheme.colors.border,
    disabledBorderColor = OdoTheme.colors.border,
    errorBorderColor = OdoTheme.colors.danger,
    focusedLabelColor = OdoTheme.colors.accent,
    unfocusedLabelColor = OdoTheme.colors.textDim,
    disabledLabelColor = OdoTheme.colors.textMuted,
    errorLabelColor = OdoTheme.colors.danger,
    focusedLeadingIconColor = OdoTheme.colors.textDim,
    unfocusedLeadingIconColor = OdoTheme.colors.textDim,
    focusedTrailingIconColor = OdoTheme.colors.textDim,
    unfocusedTrailingIconColor = OdoTheme.colors.textDim,
)

@OdoThemePreviews
@Composable
private fun OdoInputFieldPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        val text = remember { mutableStateOf("42,500") }
        OdoInputField(
            value = text.value,
            onValueChange = { text.value = it },
            label = "Odometer reading",
            placeholder = "e.g. 42,500",
            helperText = "Har entry me zaroori hai",
        )
        OdoInputField(
            value = "",
            onValueChange = {},
            label = "Workshop",
            placeholder = "Kaunsa garage?",
            errorText = "Workshop ka naam daalein",
        )
    }
}
