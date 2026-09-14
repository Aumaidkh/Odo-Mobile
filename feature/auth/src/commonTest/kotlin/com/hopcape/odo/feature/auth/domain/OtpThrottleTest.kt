package com.hopcape.odo.feature.auth.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** How often a code may be asked for, and what happens to a request that never went. */
class OtpThrottleTest {

    private var now = Instant.parse("2026-09-08T10:00:00Z")
    private val throttle = OtpThrottle(now = { now })

    @Test
    fun aCodeCannotBeAskedForAgainWithinAMinute() {
        throttle.recordRequest()
        assertEquals(1.minutes, throttle.remainingCooldown())

        now += 59.seconds
        assertFalse(throttle.canRequest())

        now += 1.seconds
        assertTrue(throttle.canRequest())
    }

    @Test
    fun aRequestThatSentNothingCostsNeitherTheWaitNorTheAllowance() {
        throttle.recordRequest()
        throttle.forgetLastRequest()

        assertTrue(throttle.canRequest())
        assertEquals(Duration.ZERO, throttle.remainingCooldown())
    }

    @Test
    fun forgettingARequestLeavesTheOneBeforeItStillCountingDown() {
        throttle.recordRequest()
        now += 10.seconds
        throttle.recordRequest()

        throttle.forgetLastRequest()

        // The first code did go out, so 50 seconds of its wait are still owed.
        assertEquals(50.seconds, throttle.remainingCooldown())
    }

    @Test
    fun forgettingARequestGivesTheAllowanceBack() {
        repeat(OtpThrottle.MAX_REQUESTS) { throttle.recordRequest() }
        assertTrue(throttle.isExhausted())

        throttle.forgetLastRequest()

        assertFalse(throttle.isExhausted())
    }
}
