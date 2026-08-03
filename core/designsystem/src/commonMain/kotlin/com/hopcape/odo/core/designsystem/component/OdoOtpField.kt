package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/** Defaults for [OdoOtpField]. */
object OdoOtpFieldDefaults {
    /** Digits in a verification code. */
    const val Length: Int = 6

    /** Height of a single digit box. */
    val BoxHeight: Dp = 60.dp
}

/**
 * The one-time-code input: a row of digit boxes the owner fills from the **platform
 * keyboard**.
 *
 * The boxes are decoration only — the real control is a transparent [BasicTextField] laid
 * over the whole row, so a tap anywhere raises the keyboard, and the owner keeps their own
 * layout, haptics, and (once a caller wires an SMS retriever) one-tap code fill. There is
 * deliberately no in-app keypad.
 *
 * Like the other `Odo*Field`s the caller always receives the **normalized** value — digits
 * only, capped at [length] — so it is safe to compare or send as-is.
 *
 * ```
 * OdoOtpField(
 *     value = state.code,
 *     onValueChange = vm::onCodeChange,
 *     isError = state.wrongCode,
 *     requestFocus = true,
 * )
 * ```
 *
 * @param value digits entered so far; anything longer than [length] is trimmed.
 * @param isError renders the danger variant **and stops accepting input**, so a wrong-code
 *   state stays put until the caller resets it.
 * @param requestFocus focus the field — and raise the keyboard — on first composition.
 * @param onFilled invoked once [value] reaches [length]; the usual "submit automatically"
 *   hook, so callers don't re-derive it.
 */
@Composable
fun OdoOtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = OdoOtpFieldDefaults.Length,
    isError: Boolean = false,
    enabled: Boolean = true,
    requestFocus: Boolean = false,
    onFilled: (String) -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.NumberPassword,
        imeAction = ImeAction.Done,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    // Raising the keyboard needs both halves: focus alone leaves the IME closed when the
    // field composes before the window is ready (the usual case on a fresh destination).
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // `isError` colours the boxes; it does not lock them. A field that stops accepting
    // input after a refusal leaves the owner with a message counting down attempts they
    // have no way to spend.
    val acceptsInput = enabled
    if (requestFocus) {
        LaunchedEffect(acceptsInput) {
            if (acceptsInput) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
    }

    Box(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            repeat(length) { index ->
                OtpBox(
                    digit = value.getOrNull(index),
                    focused = !isError && index == value.length,
                    isError = isError,
                )
            }
        }
        // The real field sits on top so a tap anywhere on the boxes opens the keyboard. It
        // paints nothing — the boxes already show the digits, the caret, and the error
        // state — but it must stay in the tree for the IME session to exist.
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                if (!acceptsInput) return@BasicTextField
                val digits = raw.filter(Char::isDigit).take(length)
                onValueChange(digits)
                if (digits.length == length) onFilled(digits)
            },
            modifier = Modifier.matchParentSize().alpha(0f).focusRequester(focusRequester),
            enabled = acceptsInput,
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
    }
}

@Composable
private fun RowScope.OtpBox(digit: Char?, focused: Boolean, isError: Boolean) {
    val colors = OdoTheme.colors
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> colors.danger
            focused -> colors.accent
            else -> colors.border
        },
        animationSpec = tween(OdoTheme.motion.fastMillis, easing = OdoTheme.motion.easeStandard),
        label = "otpBoxBorder",
    )
    val borderWidth = if (focused || isError) 2.dp else 1.dp
    Box(
        modifier = Modifier
            .weight(1f)
            .height(OdoOtpFieldDefaults.BoxHeight)
            .clip(OdoTheme.shapes.field)
            .background(if (isError) colors.danger.copy(alpha = 0.08f) else colors.surface)
            .border(BorderStroke(borderWidth, borderColor), OdoTheme.shapes.field),
        contentAlignment = Alignment.Center,
    ) {
        when {
            digit != null -> OdoText(
                digit.toString(),
                style = OdoTheme.typography.title,
                color = if (isError) colors.danger else colors.text,
            )
            focused -> Box(Modifier.size(width = 2.dp, height = 24.dp).background(colors.accent))
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoOtpFieldPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        OdoOtpField(value = "472", onValueChange = {})
        OdoOtpField(value = "472915", onValueChange = {})
        OdoOtpField(value = "472915", onValueChange = {}, isError = true)
    }
}
