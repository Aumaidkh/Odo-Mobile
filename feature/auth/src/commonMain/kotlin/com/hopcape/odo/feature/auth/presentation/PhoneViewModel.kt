package com.hopcape.odo.feature.auth.presentation

import com.hopcape.odo.feature.auth.resources.Res
import com.hopcape.odo.feature.auth.resources.au_error_phone_blank
import com.hopcape.odo.feature.auth.resources.au_error_phone_invalid
import com.hopcape.odo.feature.auth.resources.au_error_code_wrong
import com.hopcape.odo.feature.auth.resources.au_error_code_expired
import com.hopcape.odo.feature.auth.resources.au_error_too_many
import com.hopcape.odo.feature.auth.resources.au_error_send_failed
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.auth.AuthTelemetry
import com.hopcape.odo.feature.auth.domain.OdoSessionManager
import com.hopcape.odo.feature.auth.presentation.state.Submission
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Number entry: parse what was typed, ask for a code, move on.
 *
 * The screen is reached *after* car setup, so the owner already has something worth
 * protecting — which is why declining is a first-class action and not a hidden escape.
 * Skip and back are the same event for the same reason.
 */
internal class PhoneViewModel(
    private val sessions: OdoSessionManager,
    private val telemetry: AuthTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(PhoneUiState())
    val state: StateFlow<PhoneUiState> = _state.asStateFlow()

    private val _effects = Channel<PhoneEffect>(Channel.BUFFERED)
    val effects: Flow<PhoneEffect> = _effects.receiveAsFlow()

    /** The in-flight request, so a double tap cannot ask for two codes. */
    private var sendJob: Job? = null

    fun onEvent(event: PhoneEvent) = when (event) {
        is PhoneEvent.PhoneChanged -> _state.update {
            // Typing clears the last failure: the number on screen is no longer the one
            // that failed.
            it.copy(phone = event.value, submission = Submission.Idle)
        }

        PhoneEvent.SendCodeClicked -> sendCode()
        PhoneEvent.SkipClicked -> {
            // The session manager cannot see this: nothing was requested and nothing failed.
            viewModelScope.launch { telemetry.signInSkipped(AuthTelemetry.Step.PHONE) }
            emit(PhoneEffect.LeaveAuth)
        }
    }

    private fun sendCode() {
        if (!_state.value.canSubmit || sendJob?.isActive == true) return

        // Parsed here rather than per keystroke: this is the first moment the owner has
        // said they are finished typing.
        val parsed = PhoneNumber.of(_state.value.phone).fold(
            ifLeft = { error -> _state.update { it.copy(submission = Submission.Failed(error.toMessage())) }; null },
            ifRight = { it },
        ) ?: return

        _state.update { it.copy(submission = Submission.InFlight) }
        sendJob = viewModelScope.launch {
            sessions.requestOtp(parsed).fold(
                ifLeft = { error -> _state.update { it.copy(submission = Submission.Failed(error.toMessage())) } },
                ifRight = {
                    _state.update { it.copy(submission = Submission.Idle) }
                    // The parsed number travels, not what was typed — the next screen has
                    // to send the same thing the code was issued against.
                    emit(PhoneEffect.CodeSent(parsed.value))
                },
            )
        }
    }

    private fun emit(effect: PhoneEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}

/**
 * A failure the owner can act on.
 *
 * Every branch says what to do next, not what went wrong internally — "check the number" is
 * useful, "InvalidPhoneNumber" is not.
 */
internal fun DomainError.toMessage(): UiText = when (this) {
    DomainError.BlankPhoneNumber -> UiText(Res.string.au_error_phone_blank)
    DomainError.InvalidPhoneNumber -> UiText(Res.string.au_error_phone_invalid)
    DomainError.InvalidOtp -> UiText(Res.string.au_error_code_wrong)
    DomainError.OtpExpired -> UiText(Res.string.au_error_code_expired)
    is DomainError.TooManyOtpRequests -> UiText(Res.string.au_error_too_many)
    // Everything else reaching a sign-in screen means the code never left the server.
    else -> UiText(Res.string.au_error_send_failed)
}
