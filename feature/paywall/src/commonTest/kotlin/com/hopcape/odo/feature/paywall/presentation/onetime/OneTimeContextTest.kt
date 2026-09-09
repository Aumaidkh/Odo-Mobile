package com.hopcape.odo.feature.paywall.presentation.onetime

import com.hopcape.odo.feature.paywall.presentation.PaywallTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

        assertEquals(OneTimeOffer.entries.toSet(), OneTimeContext.GENERIC.offers.toSet())
    }

    /**
     * A way out is drawn where declining leads somewhere.
     *
     * The export wall has no such place: the record is still there and the sheet is over it,
     * so the scrim and the back gesture are the way out.
     *
     * The bill check does. Dismissing it landed on the masked check, which re-read and
     * offered the same wall — a loop whose only exit was the back gesture used twice. Naming
     * the decision and taking the owner out of the errand is what closes it.
     */
    @Test
    fun `a way out is drawn where declining leads somewhere`() {
        assertNotNull(OneTimeContext.GENERIC.close)
        assertNotNull(OneTimeContext.BILL_CHECK.close)
        assertNull(OneTimeContext.EXPORT.close)
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

    /**
     * The answer goes at the top. Drawn in the middle of a list it reads as one row among
     * equals, which is the thing recommending an offer is meant to avoid.
     */
    @Test
    fun `every sheet puts its recommended offer first`() {
        OneTimeContext.entries.forEach { context ->
            assertEquals(context.recommended, context.offers.first(), context.name)
        }
    }
}
