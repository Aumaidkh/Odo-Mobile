package com.hopcape.odo.core.domain.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuotaTest {

    @Test
    fun noneGrantsNothing() {
        assertFalse(Quota.None.isGranted)
        assertFalse(Quota.None.allowsAnother(used = 0))
        assertNull(Quota.None.cap)
        assertEquals(0, Quota.None.remaining(used = 0))
    }

    @Test
    fun cappedPlanAllowsUpToTheCap() {
        val quota = Quota.UpTo(3)

        assertTrue(quota.isGranted)
        assertTrue(quota.allowsAnother(used = 0))
        assertTrue(quota.allowsAnother(used = 2))
        assertFalse(quota.allowsAnother(used = 3), "the third one fills the plan")
    }

    @Test
    fun cappedPlanStaysClosedIfSomehowOverTheCap() {
        assertFalse(Quota.UpTo(3).allowsAnother(used = 5))
        assertEquals(0, Quota.UpTo(3).remaining(used = 5), "remaining never goes negative")
    }

    @Test
    fun cappedPlanCountsDown() {
        assertEquals(3, Quota.UpTo(3).remaining(used = 0))
        assertEquals(1, Quota.UpTo(3).remaining(used = 2))
        assertEquals(3, Quota.UpTo(3).cap)
    }

    @Test
    fun unlimitedPlanAlwaysAllowsAndHasNothingToCountDown() {
        assertTrue(Quota.Unlimited.isGranted)
        assertTrue(Quota.Unlimited.allowsAnother(used = 500))
        assertNull(Quota.Unlimited.cap)
        assertNull(Quota.Unlimited.remaining(used = 500))
    }
}
