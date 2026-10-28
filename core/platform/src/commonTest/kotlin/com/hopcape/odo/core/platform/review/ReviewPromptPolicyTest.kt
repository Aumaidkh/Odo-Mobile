package com.hopcape.odo.core.platform.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ReviewPromptPolicyTest {

    private val now = Instant.parse("2026-10-28T10:00:00Z")

    private fun decide(history: ReviewHistory = ReviewHistory(), trigger: ReviewTrigger = ReviewTrigger.FIRST_BILL, paywall: Boolean = false) =
        ReviewPromptPolicy.decide(history, trigger, now, paywall)

    @Test
    fun aFreshInstall_asks() = assertEquals(ReviewDecision.Ask, decide())

    @Test
    fun withinThirtyDays_skipsForCooldown() =
        assertEquals(ReviewDecision.Skip("cooldown"), decide(ReviewHistory(lastAskedAt = now - 10.days, timesAsked = 1)))

    @Test
    fun afterThirtyDays_asksAgain() =
        assertEquals(ReviewDecision.Ask, decide(ReviewHistory(lastAskedAt = now - 31.days, timesAsked = 1), ReviewTrigger.TRIP_DAY_7))

    @Test
    fun afterThreeAsks_skipsForTheCap() =
        assertEquals(ReviewDecision.Skip("cap"), decide(ReviewHistory(lastAskedAt = now - 90.days, timesAsked = 3)))

    @Test
    fun aUsedTrigger_neverAsksTwice() =
        assertEquals(ReviewDecision.Skip("used"), decide(ReviewHistory(usedTriggers = setOf(ReviewTrigger.FIRST_BILL))))

    @Test
    fun aPaywallInTheSession_skips() = assertEquals(ReviewDecision.Skip("paywall"), decide(paywall = true))

    @Test
    fun askingRecordsTheTriggerAndTheTime() {
        val after = ReviewPromptPolicy.afterAsking(ReviewHistory(), ReviewTrigger.FIRST_REMINDER, now)
        assertEquals(ReviewHistory(now, 1, setOf(ReviewTrigger.FIRST_REMINDER)), after)
    }
}
