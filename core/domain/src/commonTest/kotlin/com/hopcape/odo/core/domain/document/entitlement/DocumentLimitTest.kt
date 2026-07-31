package com.hopcape.odo.core.domain.document.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentLimitTest {

    @Test
    fun cappedPlan_allowsUpToTheCap() {
        val limit = DocumentLimit.UpTo(3)

        assertTrue(limit.allows(currentCount = 0))
        assertTrue(limit.allows(currentCount = 2))
        assertFalse(limit.allows(currentCount = 3), "the third document fills the plan")
    }

    @Test
    fun cappedPlan_staysClosedIfSomehowOverTheCap() {
        assertFalse(DocumentLimit.UpTo(3).allows(currentCount = 5))
    }

    @Test
    fun unlimitedPlan_alwaysAllows() {
        assertTrue(DocumentLimit.Unlimited.allows(currentCount = 500))
    }

    @Test
    fun capIsReadableForMessaging() {
        assertEquals(3, DocumentLimit.UpTo(3).cap)
        assertNull(DocumentLimit.Unlimited.cap)
    }
}
