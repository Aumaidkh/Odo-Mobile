package com.hopcape.analytics.internal.dispatch

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// ─────────────────────────────────────────────────────────────
// RetryPolicy — exponential backoff bounds for redelivery. Kept
// jitter-free for simplicity; add jitter in production to avoid a
// thundering herd across app instances reconnecting at once.
// ─────────────────────────────────────────────────────────────
internal class RetryPolicy(
    private val baseDelay: Duration = 1_000.milliseconds,
    private val maxDelay: Duration = 60_000.milliseconds,
    private val maxAttempts: Int = 6,
) {
    /** Backoff for a given (1-based) attempt, capped at [maxDelay]. */
    fun delayForAttempt(attempt: Int): Duration {
        val multiplier = 1L shl attempt.coerceIn(0, 10)
        return (baseDelay * multiplier.toInt()).coerceAtMost(maxDelay)
    }

    /** Whether an event has exhausted its retries and should be dead-lettered. */
    fun shouldGiveUp(attempt: Int): Boolean = attempt >= maxAttempts
}
