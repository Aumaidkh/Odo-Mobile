package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.icons.IcCaretUp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/** Defaults for [OdoPhoneNumberField], shared with anything rendering a mobile number. */
object OdoPhoneNumberDefaults {
    /** Digits in an Indian mobile number, dialling code excluded. */
    const val MaxLength: Int = 10

    /** India — the only dialling code the MVP ships (PRD: India-only launch). */
    const val CountryCode: String = "+91"

    /** Digits before the display space: `98765 43210`. */
    internal const val GroupAt: Int = 5
}

/**
 * The mobile-number input: a dialling-code chip beside a wide numeric field that
 * groups what the owner types as they type (`9876543210` → `98765 43210`) without
 * ever putting that space in the value.
 *
 * Like [OdoRegistrationNumberField], the caller always receives the **normalized**
 * string — digits only, no spaces, capped at [maxLength] — so the value is safe to
 * hand straight to auth or the DB. The grouping is purely visual, and a pasted
 * `+91 98765 43210` normalizes to `9876543210` rather than being truncated.
 *
 * It uses the **platform keyboard** ([KeyboardType.Phone]), not an in-app keypad, so
 * the owner gets their own layout, haptics, and number autofill. Set [requestFocus]
 * to open that keyboard as the screen appears — the usual thing for a screen whose
 * only job is this one field.
 *
 * ```
 * OdoPhoneNumberField(
 *     value = state.phone,
 *     onValueChange = vm::onPhoneChange,
 *     requestFocus = true,
 *     errorText = state.phoneError,   // null until invalid
 * )
 * ```
 *
 * @param value the normalized number so far (digits only); the field renders it grouped.
 * @param onValueChange receives the normalized input — never the spaced form.
 * @param onCountryCodeClick when non-null the dialling-code chip becomes tappable and
 *   grows a caret; leave null while India is the only supported code.
 * @param errorText when non-null the field turns danger-coloured and this shows below
 *   it, replacing [helperText] (same rule as [OdoInputField]).
 * @param requestFocus focus the field — and raise the platform keyboard — on first
 *   composition.
 * @param maxLength digits accepted, dialling code excluded.
 */
@Composable
fun OdoPhoneNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    countryCode: String = OdoPhoneNumberDefaults.CountryCode,
    onCountryCodeClick: (() -> Unit)? = null,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    requestFocus: Boolean = false,
    maxLength: Int = OdoPhoneNumberDefaults.MaxLength,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Phone,
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
        label = "phoneBorder",
    )
    val shape = OdoTheme.shapes.field
    val textColor = if (enabled) OdoTheme.colors.text else OdoTheme.colors.textMuted

    // Raising the keyboard needs both halves: focus alone leaves the IME closed when the
    // field composes before the window is ready (the usual case on a fresh destination).
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (requestFocus) {
        LaunchedEffect(enabled) {
            if (enabled) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
    }

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
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountryCodeChip(
                code = countryCode,
                enabled = enabled,
                onClick = onCountryCodeClick,
            )
            BasicTextField(
                value = value,
                // Normalize on the way out so the caller's state is always digits-only;
                // the space is re-applied for display by the visual transformation.
                onValueChange = { onValueChange(it.asPhoneInput(maxLength)) },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                enabled = enabled,
                singleLine = true,
                textStyle = odoPhoneTextStyle().copy(color = textColor),
                cursorBrush = SolidColor(if (isError) OdoTheme.colors.danger else OdoTheme.colors.accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = PhoneNumberTransformation,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PhoneFieldHeight)
                            .clip(shape)
                            .background(OdoTheme.colors.surface)
                            .border(PhoneBorderWidth, borderColor, shape)
                            .padding(horizontal = OdoTheme.spacing.lg),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            OdoText(
                                text = placeholder,
                                style = odoPhoneTextStyle(),
                                color = OdoTheme.colors.textMuted,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
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

/**
 * The dialling-code affordance sitting left of the number. Static by default — India is
 * the only code the MVP supports, so a caret that opens nothing would be a lie.
 */
@Composable
private fun CountryCodeChip(code: String, enabled: Boolean, onClick: (() -> Unit)?) {
    val shape = OdoTheme.shapes.field
    Row(
        modifier = Modifier
            .height(PhoneFieldHeight)
            .clip(shape)
            .background(OdoTheme.colors.surface)
            .border(PhoneBorderWidth, OdoTheme.colors.border, shape)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = OdoTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(
            text = code,
            style = odoPhoneTextStyle(),
            color = if (enabled) OdoTheme.colors.text else OdoTheme.colors.textMuted,
            maxLines = 1,
        )
        if (onClick != null) {
            OdoIcon(
                IcCaretUp,
                contentDescription = null,
                tint = OdoTheme.colors.textDim,
                size = OdoTheme.iconSizes.small,
                modifier = Modifier.rotate(180f),
            )
        }
    }
}

/** Field metrics — tall enough to stay comfortably tappable above the raised keyboard. */
private val PhoneFieldHeight = 60.dp
private val PhoneBorderWidth = 1.5.dp

/**
 * The number lettering: tabular figures, lightly tracked, so digits sit on a fixed grid
 * and the group doesn't jitter as it fills. Reuse wherever a number is *displayed* so
 * typed and rendered numbers match.
 */
@Composable
internal fun odoPhoneTextStyle(): TextStyle = OdoTheme.typography.numeric.copy(
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.06.em,
)

/**
 * Normalizes raw input to the stored form: digits only, capped at [maxLength].
 *
 * A pasted number usually carries its dialling code (`+91 98765 43210` → 12 digits), so
 * a leading `91` is dropped when that is the only reason the input overflows — otherwise
 * pasting your own number would silently truncate to `9198765432`.
 */
internal fun String.asPhoneInput(maxLength: Int): String {
    val digits = filter(Char::isDigit)
    val local = if (digits.length > maxLength && digits.startsWith(IndiaDiallingDigits)) {
        digits.drop(IndiaDiallingDigits.length)
    } else {
        digits
    }
    return local.take(maxLength)
}

private const val IndiaDiallingDigits = "91"

/**
 * Groups a mobile number for display: `9876543210` → `98765 43210`.
 *
 * Input is normalized first, so this is safe to call on raw user text as well as on a
 * stored number.
 */
fun formatPhoneNumber(raw: String): String {
    val digits = raw.asPhoneInput(OdoPhoneNumberDefaults.MaxLength)
    return digits.grouped()
}

private fun String.grouped(): String =
    if (length > OdoPhoneNumberDefaults.GroupAt) {
        "${take(OdoPhoneNumberDefaults.GroupAt)} ${drop(OdoPhoneNumberDefaults.GroupAt)}"
    } else {
        this
    }

/**
 * Renders the single display space without it ever entering the value. The offsets shift
 * by one past the group boundary; below it the mapping is the identity, which also covers
 * the short-input case where no space is inserted at all.
 */
private val PhoneNumberTransformation = VisualTransformation { text ->
    TransformedText(
        AnnotatedString(text.text.grouped()),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= OdoPhoneNumberDefaults.GroupAt) offset else offset + 1

            override fun transformedToOriginal(offset: Int): Int =
                if (offset <= OdoPhoneNumberDefaults.GroupAt) offset else offset - 1
        },
    )
}

@OdoThemePreviews
@Composable
private fun OdoPhoneNumberFieldPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        OdoPhoneNumberField(
            value = "9876543210",
            onValueChange = {},
            label = "Mobile number",
        )
        OdoPhoneNumberField(
            value = "",
            onValueChange = {},
            placeholder = "98765 43210",
            helperText = "We'll text a 6-digit code.",
        )
        OdoPhoneNumberField(
            value = "98765",
            onValueChange = {},
            errorText = "Enter all 10 digits.",
        )
    }
}
