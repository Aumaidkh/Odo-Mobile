package com.hopcape.odo.core.domain.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementsTest {

    @Test
    fun freePlanHoldsThreeDocumentsAndThreeScans() {
        val free = Entitlements(Plan.FREE)

        assertTrue(free.has(ProFeature.DOCUMENTS))
        assertEquals(3, free.quotaFor(ProFeature.DOCUMENTS).cap)
        assertEquals(3, free.quotaFor(ProFeature.BILL_SCANS).cap)
    }

    @Test
    fun freePlanDoesNotGetTheBreakdownOrTheExport() {
        val free = Entitlements(Plan.FREE)

        assertFalse(free.has(ProFeature.HEALTH_BREAKDOWN))
        assertFalse(free.has(ProFeature.RECORD_EXPORT))
    }

    @Test
    fun proGetsEverything() {
        val pro = Entitlements(Plan.PRO)

        ProFeature.entries.forEach { assertTrue(pro.has(it), "pro should have $it") }
        assertEquals(null, pro.quotaFor(ProFeature.DOCUMENTS).cap, "pro has no cap to show")
    }

    @Test
    fun unknownIsFree() {
        assertEquals(Plan.FREE, Entitlements.Unknown.plan)
        assertFalse(
            Entitlements.Unknown.has(ProFeature.HEALTH_BREAKDOWN),
            "an entitlement the app cannot prove must not unlock Pro",
        )
    }
}
