package com.hopcape.odo.core.platform.review

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** What the device remembers about earlier review requests. */
data class ReviewHistory(
    val lastAskedAt: Instant? = null,
    val timesAsked: Int = 0,
    val usedTriggers: Set<ReviewTrigger> = emptySet(),
)

/** Whether to ask now, and if not, why not (the `reason` analytics value). */
sealed interface ReviewDecision {
    data object Ask : ReviewDecision
    data class Skip(val reason: String) : ReviewDecision
}

/** The limits on asking: each trigger once, 30 days apart, 3 per install, never after a paywall. */
object ReviewPromptPolicy {
    const val MAX_PER_INSTALL = 3
    val COOLDOWN = 30.days

    fun decide(history: ReviewHistory, trigger: ReviewTrigger, now: Instant, paywallSeen: Boolean): ReviewDecision = when {
        paywallSeen -> ReviewDecision.Skip("paywall")
        trigger in history.usedTriggers -> ReviewDecision.Skip("used")
        history.timesAsked >= MAX_PER_INSTALL -> ReviewDecision.Skip("cap")
        history.lastAskedAt != null && now - history.lastAskedAt < COOLDOWN -> ReviewDecision.Skip("cooldown")
        else -> ReviewDecision.Ask
    }

    fun afterAsking(history: ReviewHistory, trigger: ReviewTrigger, now: Instant): ReviewHistory =
        history.copy(lastAskedAt = now, timesAsked = history.timesAsked + 1, usedTriggers = history.usedTriggers + trigger)
}
