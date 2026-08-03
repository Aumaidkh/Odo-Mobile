package com.hopcape.odo.core.platform.sms

import kotlinx.coroutines.flow.Flow

/**
 * Reads a one-time code out of an incoming SMS, so the owner does not have to type it.
 *
 * Android can do this without the SMS-reading permission, through Play Services' SMS
 * Retriever: the system hands the app exactly one message, and only if that message carries a
 * hash of the app's own signature. Nothing else on the device becomes readable, which is the
 * only reason this is worth doing — the permission-based alternative asks to read every SMS
 * the owner has ever received in order to autofill six digits.
 *
 * iOS has no equivalent and needs none: the keyboard offers the code from the notification by
 * itself, so the implementation there reports [SmsCodeStatus.Unsupported] and the screen asks
 * the owner to type it, which is the normal path anyway.
 *
 * **The message has to contain the app's signature hash**, and the sending template lives on
 * the server. A build whose hash is missing from that template receives nothing — silently,
 * because from inside the app that is indistinguishable from an SMS that never arrived. See
 * [SmsAppSignature].
 */
fun interface SmsCodeReader {

    /**
     * Listen for a code until the flow is cancelled.
     *
     * Cold, and bounded by the platform: Android's retriever gives up on its own after five
     * minutes, which is longer than any code stays valid.
     */
    fun listen(): Flow<SmsCodeStatus>
}

/** What the reader can tell the screen. */
sealed interface SmsCodeStatus {

    /** Waiting for a message. */
    data object Listening : SmsCodeStatus

    /** A code arrived and was extracted. */
    data class Received(val code: String) : SmsCodeStatus

    /**
     * Nothing will arrive this way — no Play Services, or a platform without the capability.
     * The screen falls back to asking the owner to type the code.
     */
    data object Unsupported : SmsCodeStatus

    /** The window closed with no message. Not an error; SMS is often just slow. */
    data object TimedOut : SmsCodeStatus
}
