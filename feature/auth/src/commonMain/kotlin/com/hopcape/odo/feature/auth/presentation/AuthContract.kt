package com.hopcape.odo.feature.auth.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.platform.sms.SmsCodeStatus
import com.hopcape.odo.feature.auth.presentation.state.Submission

/**
 * What the two sign-in screens show, what they can be told, and what they ask for in return.
 *
 * Two states rather than one, because they are two destinations with two ViewModels — the
 * number screen can be left and returned to while a code is outstanding, and sharing one
 * state would make "which screen is this failure on" a question.
 */
@Immutable
internal data class PhoneUiState(
    /** Exactly what has been typed. Validation happens on submit, not per keystroke. */
    val phone: String = "",
    val submission: Submission = Submission.Idle,
) {
    /**
     * Whether the button does anything.
     *
     * A length check, not a full parse: the field is a ten-digit keypad, and telling someone
     * their number is wrong while they are still typing it is noise.
     */
    val canSubmit: Boolean get() = !submission.isInFlight && phone.length >= MIN_TYPED_LENGTH

    private companion object {
        const val MIN_TYPED_LENGTH = 10
    }
}

internal sealed interface PhoneEvent {
    data class PhoneChanged(val value: String) : PhoneEvent
    data object SendCodeClicked : PhoneEvent
    data object SkipClicked : PhoneEvent
}

internal sealed interface PhoneEffect {
    /** The code is on its way; move to the screen that collects it. */
    data class CodeSent(val phone: String) : PhoneEffect

    /** Declined, or backed out — the same thing, and the same destination. */
    data object LeaveAuth : PhoneEffect
}

@Immutable
internal data class OtpUiState(
    /** The number the code went to, for the "Sent to …" line. Never the full number. */
    val maskedPhone: String = "",
    /**
     * Where the request that opened this screen got to.
     *
     * Its own field rather than a reading of [submission], which also covers verifying a
     * typed code — the two overlap, and a screen that said "Verifying code" while nothing
     * had been sent or typed was the first thing that went wrong when the request moved
     * here. Claiming a code was sent before the provider says so is the other.
     */
    val request: CodeRequest = CodeRequest.SENDING,
    val code: String = "",
    val submission: Submission = Submission.Idle,
    /** Seconds until Resend becomes available; zero means it already is. */
    val resendInSeconds: Int = 0,
    /** No codes left for this sitting — the countdown will not bring Resend back. */
    val resendExhausted: Boolean = false,
    /** Wrong-code attempts left before the screen stops offering a retry. */
    val triesLeft: Int = MAX_ATTEMPTS,
    /** What the SMS reader is doing, so the card can stop claiming to listen when it isn't. */
    val autoRead: SmsCodeStatus = SmsCodeStatus.Listening,
) {
    /** No code has gone out yet, so there is nothing to send again. */
    val canResend: Boolean
        get() = request != CodeRequest.SENDING &&
            resendInSeconds == 0 &&
            !resendExhausted &&
            !submission.isInFlight

    /** True only while a typed code is being checked — never while the first one is on its way. */
    val isVerifying: Boolean get() = submission.isInFlight && request != CodeRequest.SENDING

    val isError: Boolean get() = submission is Submission.Failed

    internal companion object {
        /**
         * Wrong codes tolerated before the screen sends the owner back to the number.
         *
         * Not a security control — the server counts too — but after three misses the
         * likeliest explanation is the wrong number, not the wrong code.
         */
        const val MAX_ATTEMPTS = 3
    }
}

/**
 * What the code screen may say about the code it is waiting for.
 *
 * [FAILED] shows the number with no claim attached: the error row says what happened, and
 * either "Sending" or "Sent" would be untrue once the provider has refused.
 */
internal enum class CodeRequest { SENDING, SENT, FAILED }

internal sealed interface OtpEvent {
    data class CodeChanged(val value: String) : OtpEvent
    data object ResendClicked : OtpEvent
    data object ChangeNumberClicked : OtpEvent
    data object SkipClicked : OtpEvent
}

internal sealed interface OtpEffect {
    /** Signed in. Leave auth for wherever the flow was headed. */
    data object Verified : OtpEffect

    /** Declined, or out of attempts. */
    data object LeaveAuth : OtpEffect

    /** Back to the number screen to correct it. */
    data object ChangeNumber : OtpEffect
}
