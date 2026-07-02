package com.hopcape.analytics.internal.dispatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryPolicyTest {

    @Test
    fun shouldGiveUp_onlyAtOrAboveMaxAttempts() {
        val policy = RetryPolicy(maxAttempts = 3)
        assertFalse(policy.shouldGiveUp(1))
        assertFalse(policy.shouldGiveUp(2))
        assertTrue(policy.shouldGiveUp(3))
        assertTrue(policy.shouldGiveUp(4))
    }

    @Test
    fun delayForAttempt_growsExponentially_thenCapsAtMax() {
        val policy = RetryPolicy(maxAttempts = 100)
        val d1 = policy.delayForAttempt(1)
        val d2 = policy.delayForAttempt(2)

        assertTrue(d2 > d1, "backoff should increase with attempts")
        // Far-future attempts must not exceed the configured ceiling.
        assertEquals(policy.delayForAttempt(20), policy.delayForAttempt(30))
    }
}
