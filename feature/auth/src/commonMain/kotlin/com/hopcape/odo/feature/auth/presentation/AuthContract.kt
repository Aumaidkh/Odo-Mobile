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

    /** The provider proved the number instantly — no code was ever sent. Skip straight past it. */
    data object Verified : PhoneEffect

    /** Declined, or backed out — the same thing, and the same destination. */
    data object LeaveAuth : PhoneEffect
}

@Immutable
internal data class OtpUiState(
    /** The number the code went to, for the "Sent to …" line. Never the full number. */
    val maskedPhone: String = "",
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
    val canResend: Boolean get() = resendInSeconds == 0 && !resendExhausted && !submission.isInFlight

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
