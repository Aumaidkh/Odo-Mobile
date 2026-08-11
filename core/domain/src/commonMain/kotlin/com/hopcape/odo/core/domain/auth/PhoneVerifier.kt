package com.hopcape.odo.core.domain.auth

import arrow.core.Either
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * Whoever proves a phone number belongs to the person holding the device.
 *
 * Split out from [AuthGateway] because the two answer different questions, and in Odo they
 * are answered by different vendors. This one proves the number; [AuthGateway] issues the
 * session. Keeping them apart is what lets Firebase send the SMS while Supabase still mints
 * the session that row-level security checks — neither adapter has to know the other exists.
 *
 * A port here rather than next to either adapter, for the usual reason: dependencies point
 * inward, so `:infrastructure:supabase` composing this must not mean importing Firebase.
 *
 * **Stateful, deliberately.** A verification is in flight between [startVerification] and
 * [submitCode], and the handle to it belongs to the implementation — Firebase gives back a
 * verification id that has to be paired with the typed code, and there is nowhere sensible
 * to keep it above this port. That is why [submitCode] takes no number.
 */
interface PhoneVerifier {

    /**
     * Ask for a code to be sent to [phone].
     *
     * Answers when the provider accepts the request, not when the SMS lands — nothing on the
     * device can observe delivery. Starting a second verification replaces the first; the
     * code from the earlier one stops being accepted.
     */
    suspend fun startVerification(phone: PhoneNumber): Either<DomainError, Unit>

    /**
     * Exchange the typed [code] for proof the number is real.
     *
     * [DomainError.OtpExpired] also covers "no verification is in flight" — after process
     * death mid-flow there is no handle left to pair the code with, and the answer the owner
     * needs is the same either way: resend, don't retype.
     */
    suspend fun submitCode(code: String): Either<DomainError, VerifiedPhoneToken>

    /**
     * Drop any verification state and whatever account the provider signed in locally.
     *
     * Called on sign-out. Without it the provider keeps its own signed-in user, and the next
     * sign-in on this device could skip the SMS for a number nobody re-proved.
     */
    suspend fun forget()
}

/**
 * Opaque proof that a number was verified, for whoever mints the session.
 *
 * Deliberately says nothing about what is inside. Today it holds a Firebase ID token; the
 * only thing that reads it is the call that trades it for a session, and everything between
 * here and there stays vendor-free.
 */
@JvmInline
value class VerifiedPhoneToken(val value: String) {

    /**
     * Never print this. It is a bearer credential for the duration of the exchange, and TDD
     * §12 puts it in the same bucket as OTPs and session tokens.
     */
    override fun toString(): String = "VerifiedPhoneToken(***)"
}
