package com.hopcape.odo.feature.auth.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoOtpField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.auth.resources.Res
import com.hopcape.odo.feature.auth.resources.au_cd_back
import com.hopcape.odo.feature.auth.resources.au_change
import com.hopcape.odo.feature.auth.resources.au_get_help
import com.hopcape.odo.feature.auth.resources.au_otp_sent
import com.hopcape.odo.feature.auth.resources.au_otp_title
import com.hopcape.odo.feature.auth.resources.au_resend
import com.hopcape.odo.feature.auth.resources.au_resend_exhausted
import com.hopcape.odo.feature.auth.resources.au_resend_in
import com.hopcape.odo.feature.auth.resources.au_skip
import com.hopcape.odo.feature.auth.resources.au_still_not
import org.jetbrains.compose.resources.stringResource

/**
 * OTP entry ([com.hopcape.odo.core.navigation.OdoDestination.Auth.Otp]) — covers both the
 * in-progress state (the [AutoReadSmsCard] + resend timer) and the [isError] state (wrong
 * code, with a resend + get-help path). Completing 6 digits hands off to verifying.
 *
 * Input is [OtpCodeField], so the platform keyboard drives it; the auto-read card above
 * reports whether the code is expected to arrive on its own.
 *
 * @param phone the already-formatted destination shown in the "Sent to …" line.
 * @param isError renders the error variant (sample: a pre-filled wrong code); input is
 *   inert here so the state stays put.
 */
@Composable
internal fun OtpScreen(
    state: OtpUiState,
    onEvent: (OtpEvent) -> Unit,
    autoReadStatus: AutoReadSmsStatus,
    onBack: () -> Unit,
    onGetHelp: () -> Unit,
) {
    val phone = state.maskedPhone
    val isError = state.isError
    val code = state.code

    OdoScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(OdoTheme.spacing.sm))
            OdoCircularIconButton(
                IcArrowLeft,
                contentDescription = stringResource(Res.string.au_cd_back),
                onClick = onBack,
                variant = OdoCircularIconButtonVariant.Raised,
            )
            Spacer(Modifier.height(OdoTheme.spacing.lg))
            OdoText(stringResource(Res.string.au_otp_title), style = OdoTheme.typography.title)
            Spacer(Modifier.height(OdoTheme.spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OdoText(
                    stringResource(Res.string.au_otp_sent, phone),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                )
                OdoText(
                    stringResource(Res.string.au_change),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.accent,
                    modifier = Modifier.clickable { onEvent(OtpEvent.ChangeNumberClicked) },
                )
            }

            Spacer(Modifier.height(OdoTheme.spacing.xl))
            OdoOtpField(
                value = code,
                onValueChange = { entered -> onEvent(OtpEvent.CodeChanged(entered)) },
                modifier = Modifier.testTag(AuthTestTags.OTP_FIELD),
                isError = isError,
                requestFocus = true,
                // Verification fires from the ViewModel on the last digit; nothing to confirm.
                onFilled = {},
            )
            Spacer(Modifier.height(OdoTheme.spacing.lg))

            if (isError) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.danger, size = OdoTheme.iconSizes.small)
                    // The ViewModel's own message, not the wrong-code line every time: an
                    // expired code and a mistyped one need different answers, and this slot
                    // used to give both the same one.
                    OdoText(
                        state.submission.error?.asString().orEmpty(),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.danger,
                    )
                }
                Spacer(Modifier.height(OdoTheme.spacing.lg))
                OdoButton(
                    stringResource(Res.string.au_resend),
                    onClick = { onEvent(OtpEvent.ResendClicked) },
                    variant = OdoButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(OdoTheme.spacing.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OdoText(
                        stringResource(Res.string.au_still_not),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                    OdoText(
                        stringResource(Res.string.au_get_help),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.accent,
                        modifier = Modifier.clickable(onClick = onGetHelp),
                    )
                }
            } else {
                AutoReadSmsCard(status = autoReadStatus)
                Spacer(Modifier.height(OdoTheme.spacing.md))
                when {
                    // Out of codes for this sitting. A countdown here would be a lie —
                    // waiting does not bring Resend back.
                    state.resendExhausted -> OdoText(
                        stringResource(Res.string.au_resend_exhausted),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.canResend -> OdoText(
                        stringResource(Res.string.au_resend),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(OtpEvent.ResendClicked) },
                    )

                    else -> OdoText(
                        stringResource(Res.string.au_resend_in, state.resendInSeconds.asCountdown()),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(OdoTheme.spacing.lg))
            OdoButton(
                stringResource(Res.string.au_skip),
                onClick = { onEvent(OtpEvent.SkipClicked) },
                variant = OdoButtonVariant.Tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(OdoTheme.spacing.xl))
        }
    }
}

/** Seconds as `mm:ss`, which is what the countdown copy expects. */
private fun Int.asCountdown(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
