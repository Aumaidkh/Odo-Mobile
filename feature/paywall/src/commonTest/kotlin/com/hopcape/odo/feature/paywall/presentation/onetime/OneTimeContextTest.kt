package com.hopcape.odo.feature.paywall.presentation.onetime

import com.hopcape.odo.feature.paywall.presentation.PaywallTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which sheet an owner gets, and what it puts in front of them.
 *
 * Getting this wrong is not a crash — it is someone who came for a PDF being sold bill
 * checks, which is the one thing framing the sheet was meant to stop.
 */
class OneTimeContextTest {

    @Test
    fun `running out of bill checks offers bill checks and not the PDF`() {
        val context = OneTimeContext.forTrigger(PaywallTrigger.SCANS_EXHAUSTED)

        assertEquals(OneTimeContext.BILL_CHECK, context)
        assertTrue(OneTimeOffer.RECORD_EXPORT !in context.offers)
    }

    @Test
    fun `the export wall offers the PDF and not bill checks`() {
        val context = OneTimeContext.forTrigger(PaywallTrigger.RECORD_EXPORT)

        assertEquals(OneTimeContext.EXPORT, context)
        assertEquals(listOf(OneTimeOffer.RECORD_EXPORT), context.offers)
    }

    /** Someone reading about the plan in general is not shopping for one thing yet. */
    @Test
    fun `every other trigger shows everything`() {
        listOf(PaywallTrigger.GENERIC, PaywallTrigger.SAVINGS, PaywallTrigger.SMART_REFUEL)
            .forEach { assertEquals(OneTimeContext.GENERIC, OneTimeContext.forTrigger(it)) }

        assertEquals(OneTimeOffer.entries, OneTimeContext.GENERIC.offers)
    }

    /**
     * The name travels on a navigation key, which a saved back stack can outlive a release.
     * An unrecognised one sells the same things rather than crashing.
     */
    @Test
    fun `a name from an older build falls back to the general sheet`() {
        assertEquals(OneTimeContext.GENERIC, OneTimeContext.of("SOMETHING_LATER"))
        assertEquals(OneTimeContext.EXPORT, OneTimeContext.of("EXPORT"))
    }

    /** A recommended offer that is not on the sheet would be drawn nowhere. */
    @Test
    fun `a recommended offer is always one of the offers shown`() {
        OneTimeContext.entries.forEach { context ->
            context.recommended?.let { assertTrue(it in context.offers, "${context.name}") }
        }
    }
}
