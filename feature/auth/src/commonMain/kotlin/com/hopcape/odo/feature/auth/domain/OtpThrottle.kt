package com.hopcape.odo.feature.auth.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * How often a code may be asked for, decided on the device.
 *
 * The server enforces its own limit and answers 429, but by then the owner has already
 * tapped Resend and waited for a round trip to be told off. Deciding locally means the
 * button is simply unavailable, with a countdown saying when it will not be.
 *
 * Two separate limits, because they stop different things:
 *
 *  - [COOLDOWN] is the gap between one code and the next. It exists because SMS is slow —
 *    most people tap Resend before the first one has arrived, and a second code invalidates
 *    the first, which makes it worse rather than better.
 *  - [MAX_REQUESTS] is the ceiling for one sitting. Each SMS costs money, and a number that
 *    has been sent five codes without a successful sign-in is not going to receive the sixth.
 *
 * Deliberately in-memory and per-flow: this is a courtesy to the owner and a guard against
 * fat fingers, not a security control. Anything that actually needs enforcing is enforced
 * by the server, which is the only side an attacker cannot restart.
 */
internal class OtpThrottle(private val now: () -> Instant) {

    private var lastRequestedAt: Instant? = null
    private var requests: Int = 0

    /** Whether another code may be asked for right now. */
    fun canRequest(): Boolean = requests < MAX_REQUESTS && remainingCooldown() == Duration.ZERO

    /** How long until the cooldown lapses; zero when it already has. */
    fun remainingCooldown(): Duration {
        val last = lastRequestedAt ?: return Duration.ZERO
        val elapsed = now() - last
        return if (elapsed >= COOLDOWN) Duration.ZERO else COOLDOWN - elapsed
    }

    /** True once this sitting has used up its allowance; the countdown will not help. */
    fun isExhausted(): Boolean = requests >= MAX_REQUESTS

    /** Record that a code was asked for. Called only when the request was accepted. */
    fun recordRequest() {
        lastRequestedAt = now()
        requests++
    }

    internal companion object {
        /** Long enough for an SMS to actually arrive on an Indian network. */
        val COOLDOWN = 30.seconds

        /** Codes per sitting. Beyond this the number is not receiving them. */
        const val MAX_REQUESTS = 5
    }
}
