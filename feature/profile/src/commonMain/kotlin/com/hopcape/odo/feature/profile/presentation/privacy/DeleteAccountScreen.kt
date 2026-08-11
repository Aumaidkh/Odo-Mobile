package com.hopcape.odo.feature.profile.presentation.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoOtpField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.presentation.ProfileTestTags
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_back
import com.hopcape.odo.feature.profile.resources.pf_da_cancel
import com.hopcape.odo.feature.profile.resources.pf_da_confirm_action
import com.hopcape.odo.feature.profile.resources.pf_da_confirm_body
import com.hopcape.odo.feature.profile.resources.pf_da_confirm_code_note
import com.hopcape.odo.feature.profile.resources.pf_da_confirm_phrase_label
import com.hopcape.odo.feature.profile.resources.pf_da_confirm_heading
import com.hopcape.odo.feature.profile.resources.pf_da_confirm_local_note
import com.hopcape.odo.feature.profile.resources.pf_da_local_failed_action
import com.hopcape.odo.feature.profile.resources.pf_da_local_failed_body
import com.hopcape.odo.feature.profile.resources.pf_da_local_failed_heading
import com.hopcape.odo.feature.profile.resources.pf_da_resend
import com.hopcape.odo.feature.profile.resources.pf_da_title
import com.hopcape.odo.feature.profile.resources.pf_da_verify_action
import com.hopcape.odo.feature.profile.resources.pf_da_verify_body
import com.hopcape.odo.feature.profile.resources.pf_da_verify_heading
import com.hopcape.odo.feature.profile.resources.pf_da_working
import org.jetbrains.compose.resources.stringResource

/**
 * Erasing the account, confirm through to done.
 *
 * One screen with steps rather than a dialog: this takes an SMS round trip, it can fail in
 * ways the owner has to act on, and a dialog that grows a code field and three error states
 * is a screen that has not admitted what it is.
 *
 * There is no back button once the erase is under way. Everything up to
 * [DeleteAccountStep.Confirm] is cancellable and back is offered; from
 * [DeleteAccountStep.Working] on, leaving would abandon a half-finished deletion with no way
 * to find out how far it got.
 */
@Composable
internal fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    onEvent: (DeleteAccountEvent) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.pf_da_title),
        onBack = if (state.step == DeleteAccountStep.Confirm) onCancel else null,
        backContentDescription = stringResource(Res.string.pf_cd_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            state.error?.let { message ->
                OdoText(
                    message.asString(),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.danger,
                )
            }

            when (state.step) {
                DeleteAccountStep.Confirm -> ConfirmStep(state, onEvent, onCancel)
                DeleteAccountStep.Verify -> VerifyStep(state, onEvent)
                DeleteAccountStep.Working -> WorkingStep()
                DeleteAccountStep.LocalWipeFailed -> LocalWipeFailedStep(onEvent, onCancel)
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    state: DeleteAccountUiState,
    onEvent: (DeleteAccountEvent) -> Unit,
    onCancel: () -> Unit,
) {
    OdoCard {
        OdoText(
            stringResource(Res.string.pf_da_confirm_heading),
            style = OdoTheme.typography.title,
            color = OdoTheme.colors.danger,
        )
        OdoText(
            stringResource(Res.string.pf_da_confirm_body),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )
        OdoText(
            // Two different truths, and the owner is owed whichever applies: a code is
            // coming, or there was never anything on a server to begin with.
            if (state.phoneNumber != null) {
                stringResource(Res.string.pf_da_confirm_code_note, state.phoneNumber)
            } else {
                stringResource(Res.string.pf_da_confirm_local_note)
            },
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textMuted,
        )
    }

    // Typing the phrase, rather than a second "are you sure?". A confirmation an owner can
    // clear by tapping twice in the same place is one they can clear by accident; this one
    // cannot be reached without reading it. It is also the last chance to stop — everything
    // after this point is irreversible.
    OdoInputField(
        value = state.phrase,
        onValueChange = { onEvent(DeleteAccountEvent.PhraseChanged(it)) },
        label = stringResource(
            Res.string.pf_da_confirm_phrase_label,
            DeleteAccountUiState.CONFIRM_PHRASE,
        ),
        placeholder = DeleteAccountUiState.CONFIRM_PHRASE,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(ProfileTestTags.DELETE_ACCOUNT_PHRASE),
    )

    OdoButton(
        stringResource(Res.string.pf_da_confirm_action),
        onClick = { onEvent(DeleteAccountEvent.Confirmed) },
        enabled = state.canDelete,
        variant = OdoButtonVariant.Danger,
        modifier = Modifier.fillMaxWidth().testTag(ProfileTestTags.DELETE_ACCOUNT_CONFIRM),
    )
    OdoButton(
        stringResource(Res.string.pf_da_cancel),
        onClick = onCancel,
        variant = OdoButtonVariant.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VerifyStep(state: DeleteAccountUiState, onEvent: (DeleteAccountEvent) -> Unit) {
    OdoText(stringResource(Res.string.pf_da_verify_heading), style = OdoTheme.typography.title)
    OdoText(
        stringResource(Res.string.pf_da_verify_body, state.phoneNumber.orEmpty()),
        style = OdoTheme.typography.body,
        color = OdoTheme.colors.textDim,
    )

    OdoOtpField(
        value = state.code,
        onValueChange = { onEvent(DeleteAccountEvent.CodeChanged(it)) },
        length = DeleteAccountUiState.CODE_LENGTH,
        isError = state.error != null,
        requestFocus = true,
        // Deliberately not submitting on the sixth digit, unlike sign-in. Auto-submitting an
        // irreversible deletion the instant the last digit lands takes away the moment where
        // someone can still stop.
        onFilled = {},
    )

    OdoButton(
        stringResource(Res.string.pf_da_verify_action),
        onClick = { onEvent(DeleteAccountEvent.CodeSubmitted) },
        enabled = state.canSubmit,
        variant = OdoButtonVariant.Danger,
        modifier = Modifier.fillMaxWidth(),
    )
    OdoButton(
        stringResource(Res.string.pf_da_resend),
        onClick = { onEvent(DeleteAccountEvent.ResendRequested) },
        variant = OdoButtonVariant.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WorkingStep() {
    Box(Modifier.fillMaxWidth().padding(OdoTheme.spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoLoadingIndicator()
            OdoText(
                stringResource(Res.string.pf_da_working),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The account is gone; this phone kept a copy.
 *
 * Leads with the good news, because it is the part the owner asked for and the part they
 * cannot check for themselves. The only action offered is another attempt at the wipe —
 * retrying the erase would reach a server that has nothing left.
 */
@Composable
private fun LocalWipeFailedStep(onEvent: (DeleteAccountEvent) -> Unit, onCancel: () -> Unit) {
    OdoCard {
        OdoText(stringResource(Res.string.pf_da_local_failed_heading), style = OdoTheme.typography.title)
        OdoText(
            stringResource(Res.string.pf_da_local_failed_body),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )
    }
    OdoButton(
        stringResource(Res.string.pf_da_local_failed_action),
        onClick = { onEvent(DeleteAccountEvent.LocalWipeRetried) },
        modifier = Modifier.fillMaxWidth(),
    )
    OdoButton(
        stringResource(Res.string.pf_da_cancel),
        onClick = onCancel,
        variant = OdoButtonVariant.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}
