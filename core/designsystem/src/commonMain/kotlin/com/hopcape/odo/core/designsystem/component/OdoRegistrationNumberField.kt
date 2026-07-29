package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/** Defaults for [OdoRegistrationNumberField], shared with anything rendering a plate. */
object OdoRegistrationNumberDefaults {
    /**
     * Longest Indian registration number in characters, spaces excluded — a
     * three-letter series plate (`MH 12 ABC 1234`). The common four-part plate is
     * 10 (`MH 12 AB 1234`) and BH-series is 10 (`22 BH 1234 AA`).
     */
    const val MaxLength: Int = 11
}

/**
 * The number-plate input: a wide, centred field styled like the plate on the car,
 * which formats what the owner types into readable groups as they type
 * (`mh12ab1234` → `MH 12 AB 1234`) without ever putting those spaces in the value.
 *
 * The caller always receives the **normalized** string — uppercase, alphanumeric
 * only, no spaces, capped at [maxLength] — which is exactly what
 * `RegistrationNumber.of()` and the DB store (DB_SCHEMA §9.3). So the grouping is
 * purely visual: the owner can type `mh 12 ab 1234`, `MH12AB1234`, or paste
 * `MH-12-AB-1234` and the value is `MH12AB1234` either way.
 *
 * ```
 * OdoRegistrationNumberField(
 *     value = form.registrationNumber,
 *     onValueChange = vm::onRegistrationNumber,
 *     label = "Registration number",
 *     placeholder = "MH 12 AB 1234",
 *     errorText = form.registrationError,   // null until invalid
 * )
 * ```
 *
 * @param value the normalized plate so far (no spaces); the field renders it grouped.
 * @param onValueChange receives the normalized input — never the spaced form.
 * @param errorText when non-null, the plate turns danger-coloured and this shows
 *   below it, replacing [helperText] (same rule as [OdoInputField]).
 * @param maxLength characters accepted, spaces excluded.
 */
@Composable
fun OdoRegistrationNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    maxLength: Int = OdoRegistrationNumberDefaults.MaxLength,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        // Caps + no auto-correct: a plate is not a word, and the value is uppercased
        // anyway — this just stops the keyboard fighting the user.
        capitalization = KeyboardCapitalization.Characters,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Done,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val isError = errorText != null
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> OdoTheme.colors.danger
            focused && enabled -> OdoTheme.colors.accent
            else -> OdoTheme.colors.border
        },
        animationSpec = tween(OdoTheme.motion.fastMillis, easing = OdoTheme.motion.easeStandard),
        label = "plateBorder",
    )
    val shape = OdoTheme.shapes.card
    val textColor = if (enabled) OdoTheme.colors.text else OdoTheme.colors.textMuted

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        if (label != null) {
            OdoText(
                text = label,
                style = OdoTheme.typography.label,
                color = if (isError) OdoTheme.colors.danger else OdoTheme.colors.textDim,
            )
        }
        BasicTextField(
            value = value,
            // Normalize on the way out so the caller's state is always plate-clean;
            // the spacing is re-applied for display by the visual transformation.
            onValueChange = { onValueChange(it.asRegistrationInput(maxLength)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            textStyle = odoPlateTextStyle().copy(color = textColor),
            cursorBrush = SolidColor(if (isError) OdoTheme.colors.danger else OdoTheme.colors.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = RegistrationPlateTransformation,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PlateHeight)
                        .clip(shape)
                        .background(OdoTheme.colors.surface)
                        .border(PlateBorderWidth, borderColor, shape)
                        .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        OdoText(
                            text = placeholder,
                            style = odoPlateTextStyle(),
                            color = OdoTheme.colors.textMuted,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        // Error replaces helper — never both, matching OdoInputField.
        val supporting = errorText ?: helperText
        if (supporting != null) {
            OdoText(
                text = supporting,
                style = OdoTheme.typography.bodySmall,
                color = if (isError) OdoTheme.colors.danger else OdoTheme.colors.textDim,
            )
        }
    }
}

/** Plate metrics — big enough to read at arm's length, like the real thing. */
private val PlateHeight = 64.dp
private val PlateBorderWidth = 1.5.dp

/**
 * The plate lettering: heavy, tracked, tabular-figure caps, centred — the look of
 * an embossed number plate. Reuse it wherever a plate is *displayed* (car card,
 * passport header) so typed and rendered plates match.
 */
@Composable
internal fun odoPlateTextStyle(): TextStyle = OdoTheme.typography.numeric.copy(
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.12.em,
    textAlign = TextAlign.Center,
)

/**
 * Groups a registration number for display: `MH12AB1234` → `MH 12 AB 1234`.
 *
 * Grouping is by runs of the same character class (letters vs digits) rather than
 * fixed widths, because plate parts vary in length — the state code is 2 letters,
 * the RTO code 1–2 digits, the series 0–3 letters, the number up to 4 digits, and
 * BH-series plates invert the order entirely (`22 BH 1234 AA`). Class runs get all
 * of those right without a format table. The one place it differs from the RTO's
 * own spacing is a lettered RTO code (`DL8CAF5031` reads as `DL 8 CAF 5031`) —
 * still grouped, still readable.
 *
 * Input is normalized first, so this is safe to call on raw user text as well as on
 * a stored `RegistrationNumber`.
 */
fun formatRegistrationNumber(raw: String): String {
    val normalized = raw.asRegistrationInput(maxLength = Int.MAX_VALUE)
    return buildString(normalized.length + 3) {
        normalized.forEachIndexed { i, c ->
            if (i > 0 && c.isDigit() != normalized[i - 1].isDigit()) append(' ')
            append(c)
        }
    }
}

/** Uppercase, drop everything that can't be on a plate, and cap the length. */
internal fun String.asRegistrationInput(maxLength: Int): String =
    uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(maxLength)

/**
 * Renders the stored (space-free) plate in its grouped form while keeping the
 * cursor honest: the offset mapping is derived from the formatted string itself,
 * so it stays correct however many groups [formatRegistrationNumber] produced.
 */
internal object RegistrationPlateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = formatRegistrationNumber(raw)
        // Spaces are the only insertions, so a single walk builds both directions:
        // originalToTransformed[k] = where the k-th raw char landed.
        val originalToTransformed = IntArray(raw.length + 1)
        val transformedToOriginal = IntArray(formatted.length + 1)
        var consumed = 0
        formatted.forEachIndexed { i, c ->
            transformedToOriginal[i] = consumed
            if (c != ' ') {
                if (consumed < raw.length) originalToTransformed[consumed] = i
                consumed++
            }
        }
        originalToTransformed[raw.length] = formatted.length
        transformedToOriginal[formatted.length] = raw.length

        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    originalToTransformed[offset.coerceIn(0, raw.length)]

                override fun transformedToOriginal(offset: Int): Int =
                    transformedToOriginal[offset.coerceIn(0, formatted.length)]
            },
        )
    }
}

@OdoThemePreviews
@Composable
private fun OdoRegistrationNumberFieldPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        var plate by remember { mutableStateOf("MH12AB1234") }
        OdoRegistrationNumberField(
            value = plate,
            onValueChange = { plate = it },
            label = "Registration number",
            helperText = "Gaadi ka number plate",
        )
        OdoRegistrationNumberField(
            value = "",
            onValueChange = {},
            placeholder = "MH 12 AB 1234",
        )
        OdoRegistrationNumberField(
            value = "22BH1234AA",
            onValueChange = {},
            label = "Registration number",
            errorText = "Yeh number pehle se added hai",
        )
    }
}
