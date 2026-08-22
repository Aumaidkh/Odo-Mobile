package com.hopcape.odo.feature.profile.presentation.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.auth.PhoneVerificationOutcome
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.auth.VerifiedAccount
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.profile.domain.usecase.DeleteAccountUseCase
import com.hopcape.odo.feature.profile.presentation.ProfileTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * State holder for "Delete my account & data".
 *
 * Two shapes of the same intent, chosen by whether there is an account behind this device:
 *
 * - **Signed in** — confirm, prove the number again, then erase everywhere. The
 *   re-verification is not ceremony: the erase endpoint refuses proof older than ten
 *   minutes, so the session already in hand is not enough to authorise it.
 * - **Never signed in** — confirm, then wipe. There is no server row and no credential, so
 *   there is nothing to prove and nobody to ask.
 *
 * The number is read once, when the screen opens, so the confirmation can say where a code
 * would go before the owner commits to anything.
 */
internal class DeleteAccountViewModel(
    private val account: VerifiedAccount,
    private val verifier: PhoneVerifier,
    private val deleteAccount: DeleteAccountUseCase,
    private val telemetry: ProfileTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    private val _effects = Channel<DeleteAccountEffect>(Channel.BUFFERED)
    val effects: Flow<DeleteAccountEffect> = _effects.receiveAsFlow()

    /** Held between the code screen and the erase; never shown and never logged. */
    private var verifiedNumber: PhoneNumber? = null

    init {
        telemetry.settingsOpened(ProfileTelemetry.Screen.DELETE_ACCOUNT)
        viewModelScope.launch {
            val number = account.verifiedNumber()
            verifiedNumber = number
            _state.value = _state.value.copy(phoneNumber = number?.value?.masked())
        }
    }

    fun onEvent(event: DeleteAccountEvent) {
        when (event) {
            is DeleteAccountEvent.PhraseChanged -> _state.value =
                _state.value.copy(phrase = event.phrase, error = null)

            DeleteAccountEvent.Confirmed -> confirm()
            is DeleteAccountEvent.CodeChanged -> _state.value =
                _state.value.copy(code = event.code, error = null)
            DeleteAccountEvent.CodeSubmitted -> submitCode()
            DeleteAccountEvent.ResendRequested -> sendCode()
            DeleteAccountEvent.LocalWipeRetried -> retryLocalWipe()
        }
    }

    /**
     * The point of no return, or the start of proving the number.
     *
     * Guarded rather than trusting the button's enabled state. The screen already disables it
     * until the phrase matches, but this is the one action in the app that cannot be undone,
     * so it re-checks rather than assuming the UI got it right.
     */
    private fun confirm() {
        if (!_state.value.canDelete) return
        telemetry.accountDeletionStarted(hasAccount = _state.value.hasAccount)
        if (_state.value.hasAccount) sendCode() else eraseLocalOnly()
    }

    private fun sendCode() {
        val number = verifiedNumber ?: return eraseLocalOnly()
        working()
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.DELETE_ACCOUNT)) {
            verifier.startVerification(number).fold(
                ifLeft = { fail(it, back = DeleteAccountStep.Confirm) },
                ifRight = { outcome ->
                    when (outcome) {
                        // Clear any previous digits: a resend invalidates the code they were
                        // for, and leaving them on screen invites submitting one that can no
                        // longer work.
                        PhoneVerificationOutcome.CodeSent ->
                            _state.value = _state.value.copy(step = DeleteAccountStep.Verify, code = "")
                        // Already proved — there is no code to collect, so go straight to
                        // the erase this re-verification was for.
                        PhoneVerificationOutcome.AlreadyVerified -> completeAutoVerification()
                    }
                },
            )
        }
    }

    private suspend fun completeAutoVerification() {
        verifier.completeAutoVerification().fold(
            ifLeft = { fail(it, back = DeleteAccountStep.Confirm) },
            ifRight = { token -> eraseWithToken(token) },
        )
    }

    private fun submitCode() {
        val code = _state.value.code
        if (code.length != DeleteAccountUiState.CODE_LENGTH) return
        working()
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.DELETE_ACCOUNT)) {
            verifier.submitCode(code).fold(
                ifLeft = { fail(it, back = DeleteAccountStep.Verify) },
                ifRight = { token -> eraseWithToken(token) },
            )
        }
    }

    private suspend fun eraseWithToken(token: VerifiedPhoneToken) {
        telemetry.accountErase { deleteAccount(token) }.fold(
            ifLeft = ::handleEraseFailure,
            ifRight = { finish() },
        )
    }

    private fun eraseLocalOnly() {
        working()
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.DELETE_ACCOUNT)) {
            telemetry.accountErase { deleteAccount.localOnly() }.fold(
                ifLeft = ::handleEraseFailure,
                ifRight = { finish() },
            )
        }
    }

    private fun retryLocalWipe() {
        working()
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.DELETE_ACCOUNT)) {
            telemetry.accountErase { deleteAccount.localOnly() }.fold(
                ifLeft = { fail(it, back = DeleteAccountStep.LocalWipeFailed) },
                ifRight = { finish() },
            )
        }
    }

    /**
     * Route a failed erase to the step that offers the right next move.
     *
     * [DomainError.LocalDataSurvivedErase] is the one that does not go back where it came
     * from: the account is already gone, so returning to a confirmation about deleting it
     * would be false.
     */
    private fun handleEraseFailure(error: DomainError) {
        val back = when (error) {
            DomainError.LocalDataSurvivedErase -> DeleteAccountStep.LocalWipeFailed
            // Firebase or the server wants the number proved again — a fresh code, not a
            // retyped one.
            DomainError.ReVerificationRequired -> DeleteAccountStep.Confirm
            else -> if (_state.value.hasAccount) DeleteAccountStep.Verify else DeleteAccountStep.Confirm
        }
        fail(error, back)
    }

    private fun working() {
        _state.value = _state.value.copy(step = DeleteAccountStep.Working, error = null)
    }

    private fun fail(error: DomainError, back: DeleteAccountStep) {
        _state.value = _state.value.copy(step = back, error = error.toDeleteAccountMessage())
    }

    private suspend fun finish() {
        _effects.send(DeleteAccountEffect.Deleted)
    }
}

/**
 * The last two digits, and nothing else.
 *
 * Enough for the owner to recognise the number a code is going to, and not enough to be worth
 * anything on a shoulder-surfed screen. The whole number is never rendered — it is the one
 * identifier this flow handles and it is not needed on screen.
 */
private fun String.masked(): String =
    if (length <= VISIBLE_DIGITS) this else "•••• " + takeLast(VISIBLE_DIGITS)

private const val VISIBLE_DIGITS = 2
