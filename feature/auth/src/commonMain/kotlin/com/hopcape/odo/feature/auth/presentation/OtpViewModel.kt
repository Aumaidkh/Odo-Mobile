package com.hopcape.odo.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.auth.AuthTelemetry
import com.hopcape.odo.feature.auth.domain.OdoSessionManager
import com.hopcape.odo.core.platform.sms.SmsAppSignature
import com.hopcape.odo.core.platform.sms.SmsCodeReader
import com.hopcape.odo.core.platform.sms.SmsCodeStatus
import com.hopcape.odo.feature.auth.domain.OtpThrottle
import com.hopcape.odo.feature.auth.presentation.state.Submission
import com.hopcape.odo.feature.auth.resources.Res
import com.hopcape.odo.feature.auth.resources.au_otp_error
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Code entry: verify, resend, or go back and fix the number.
 *
 * Verification fires on the last digit rather than behind a button — the code is a fixed
 * length, so there is nothing to confirm. Wrong codes are counted, and after
 * [OtpUiState.MAX_ATTEMPTS] the owner is sent back to the number, because by then the
 * likeliest explanation is that the number is wrong rather than the code.
 */
internal class OtpViewModel(
    private val phone: PhoneNumber,
    private val sessions: OdoSessionManager,
    private val telemetry: AuthTelemetry,
    private val smsCodes: SmsCodeReader,
    private val smsSignature: SmsAppSignature,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val throttle = OtpThrottle(now = clock::now)

    private val _state = MutableStateFlow(OtpUiState(maskedPhone = phone.lastFourDigits))
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    private val _effects = Channel<OtpEffect>(Channel.BUFFERED)
    val effects: Flow<OtpEffect> = _effects.receiveAsFlow()

    private var verifyJob: Job? = null
    private var countdownJob: Job? = null

    init {
        // The code that brought us here has already been sent, so the cooldown starts now
        // rather than on the first Resend — otherwise Resend is live the instant the screen
        // opens, which is exactly when it is least useful.
        throttle.recordRequest()
        startCountdown()
        listenForCode()
    }

    /**
     * Fill the field from an incoming SMS, which then verifies like anything else typed.
     *
     * Routed through the same [onCodeChanged] rather than verifying directly, so the code
     * appears in the field before it is submitted — an auto-fill the owner cannot see looks
     * like the screen acting on its own.
     *
     * Unsupported and timed out both mean "the owner types it", which is the ordinary path,
     * so neither is surfaced as a failure.
     *
     * The signature hash is logged alongside, because "auto-read does nothing" and "the
     * template is missing this build's hash" look identical from here and the hash is
     * otherwise unreadable.
     */
    private fun listenForCode() {
        viewModelScope.launch {
            smsSignature.current()
                .takeIf { it.isNotEmpty() }
                ?.let { telemetry.smsSignature(it) }
        }
        viewModelScope.launch {
            smsCodes.listen().collect { status ->
                _state.update { it.copy(autoRead = status) }
                if (status is SmsCodeStatus.Received) onCodeChanged(status.code)
            }
        }
    }

    fun onEvent(event: OtpEvent) = when (event) {
        is OtpEvent.CodeChanged -> onCodeChanged(event.value)
        OtpEvent.ResendClicked -> resend()
        OtpEvent.ChangeNumberClicked -> emit(OtpEffect.ChangeNumber)
        OtpEvent.SkipClicked -> {
            viewModelScope.launch { telemetry.signInSkipped(AuthTelemetry.Step.OTP) }
            emit(OtpEffect.LeaveAuth)
        }
    }

    private fun onCodeChanged(value: String) {
        _state.update { it.copy(code = value, submission = Submission.Idle) }
        if (value.length == CODE_LENGTH) verify(value)
    }

    private fun verify(code: String) {
        if (verifyJob?.isActive == true) return
        _state.update { it.copy(submission = Submission.InFlight) }

        verifyJob = viewModelScope.launch {
            sessions.verifyOtp(phone, code).fold(
                ifLeft = ::onRejected,
                ifRight = { emit(OtpEffect.Verified) },
            )
        }
    }

    private fun onRejected(error: DomainError) {
        // An expired code is not a wrong one: the owner did nothing incorrect, so it does
        // not cost an attempt — it costs a resend.
        val spent = error !is DomainError.OtpExpired
        val left = if (spent) _state.value.triesLeft - 1 else _state.value.triesLeft

        if (left <= 0) {
            // A dead end reached by someone who was actually trying — worth telling apart
            // from a skip, because the fix is a wrong number or codes not arriving.
            viewModelScope.launch { telemetry.otpAttemptsExhausted() }
            emit(OtpEffect.ChangeNumber)
            return
        }
        _state.update {
            it.copy(
                // Cleared, so the next attempt can be typed straight in. The field caps at
                // six digits, so leaving the refused code there would swallow every
                // keystroke and make the "tries left" the line below promises unreachable.
                code = "",
                submission = Submission.Failed(messageFor(error, left)),
                triesLeft = left,
            )
        }
    }

    /**
     * What the owner is told about a refusal.
     *
     * A wrong code gets the line that counts the attempts down; anything else gets its own
     * message. Built here rather than on the screen because the screen has one failure slot
     * and cannot tell an expired code from a mistyped one — it used to show the wrong-code
     * line for both, which blamed the owner for a code that had simply timed out.
     */
    private fun messageFor(error: DomainError, triesLeft: Int): UiText =
        if (error is DomainError.InvalidOtp) {
            UiText(Res.string.au_otp_error, listOf(triesLeft))
        } else {
            error.toMessage()
        }

    private fun resend() {
        if (!_state.value.canResend) return
        if (!throttle.canRequest()) {
            _state.update { it.copy(resendExhausted = throttle.isExhausted()) }
            return
        }

        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch {
            sessions.requestOtp(phone).fold(
                ifLeft = { error -> _state.update { it.copy(submission = Submission.Failed(error.toMessage())) } },
                ifRight = {
                    throttle.recordRequest()
                    // A fresh code invalidates the old one, so whatever is typed is stale.
                    _state.update {
                        it.copy(
                            submission = Submission.Idle,
                            code = "",
                            resendExhausted = throttle.isExhausted(),
                        )
                    }
                    startCountdown()
                },
            )
        }
    }

    /**
     * Ticks the Resend countdown down to zero, restarting if a new code goes out.
     *
     * Counts down from a value captured once rather than re-reading the clock each tick.
     * Re-reading looks more correct but is not: it makes the loop's end depend on the clock
     * moving, so a stopped clock spins forever. This is display only — whether a resend is
     * actually allowed is still [OtpThrottle]'s decision, against the real clock.
     *
     * Two details keep the display and that decision agreeing:
     *
     * **Rounded up, not truncated.** A cooldown read immediately after the request is
     * 29.999 seconds, and `inWholeSeconds` on that is 29 — so the countdown used to finish a
     * second before the throttle would allow anything, and the first tap on Resend was
     * silently refused.
     *
     * **The first value is set before the coroutine starts.** The first composition happens
     * before a launched coroutine gets to run, and a state still saying zero offers a Resend
     * in exactly the window the throttle will refuse.
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        val seconds = throttle.remainingCooldown().ceilWholeSeconds()
        _state.update { it.copy(resendInSeconds = seconds) }
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1.seconds)
                remaining--
                _state.update { it.copy(resendInSeconds = remaining) }
            }
        }
    }

    private fun emit(effect: OtpEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        /** Supabase issues six-digit codes. */
        const val CODE_LENGTH = 6
    }
}

/**
 * Whole seconds, rounded up. A countdown that rounds down finishes before the thing it is
 * counting towards.
 */
private fun Duration.ceilWholeSeconds(): Int =
    ((inWholeMilliseconds + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

private const val MILLIS_PER_SECOND = 1_000L
