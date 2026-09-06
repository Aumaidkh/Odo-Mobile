package com.hopcape.odo.feature.billcheck.domain

import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.WorkshopTier
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two rules the screen cannot restate without risking disagreeing with itself: what the
 * headline adds up to, and which findings carry their rupee figure.
 */
class BillCheckTest {

    /**
     * The headline is the flagged lines and nothing else. Derived rather than passed in, so a
     * reader that miscounts cannot put a figure on screen the rows below it contradict.
     */
    @Test
    fun `the headline is what the flagged lines add up to`() {
        val check = check(
            flagged = listOf(
                flagged("AC service", 2_400, Reason.AboveBand(rupees(1_400), rupees(1_800))),
                flagged("Injector cleaning", 3_100, Reason.ScheduledLater(40_000, 12_000)),
            ),
            fine = listOf(PricedLine("Engine oil + filter", rupees(5_800))),
        )

        assertEquals(rupees(5_500), check.worthAsking)
    }

    @Test
    fun `a bill with nothing flagged is worth nothing to ask about`() {
        val check = check(flagged = emptyList(), fine = listOf(PricedLine("Air filter", rupees(950))))

        assertEquals(Amount.ZERO, check.worthAsking)
    }

    /** "all 6 below" counts the whole bill — including what could not be checked. */
    @Test
    fun `the line count covers the whole bill`() {
        val check = check(
            flagged = listOf(flagged("AC service", 2_400, Reason.AboveBand(rupees(1_400), rupees(1_800)))),
            fine = listOf(
                PricedLine("Engine oil + filter", rupees(5_800)),
                PricedLine("Air filter", rupees(950)),
            ),
            unchecked = listOf(PricedLine("Throttle body", rupees(1_800))),
        )

        assertEquals(4, check.lineCount)
    }

    /**
     * An unchecked line is not worth asking about — nothing was found. Counting it would put
     * a rupee figure in the headline that no finding on the screen supports.
     */
    @Test
    fun `an unchecked line adds nothing to the headline`() {
        val check = check(
            flagged = listOf(flagged("AC service", 2_400, Reason.AboveBand(rupees(1_400), rupees(1_800)))),
            fine = emptyList(),
            unchecked = listOf(PricedLine("Throttle body", rupees(1_800))),
        )

        assertEquals(rupees(2_400), check.worthAsking)
    }

    /**
     * A rate or a repeat is a claim about money the table can defend, so the figure is part
     * of the finding. A schedule question is a claim about the *maker* — the app does not
     * know this car does not need the job (AI_ADVISORY_PLAN §2.8) — so its price is not.
     */
    @Test
    fun `only a claim about money carries its figure`() {
        val overpriced = flagged("AC service", 2_400, Reason.AboveBand(rupees(1_400), rupees(1_800)))
        val repeat = flagged("Throttle body", 1_800, Reason.DoneRecently(monthsAgo = 4, on = LocalDate(2026, 4, 12)))
        val schedule = flagged("Injector cleaning", 3_100, Reason.ScheduledLater(40_000, 12_000))

        assertTrue(overpriced.amountIsTheClaim)
        assertTrue(repeat.amountIsTheClaim)
        assertFalse(schedule.amountIsTheClaim, "the app is not claiming this price is wrong")

        // And the mirror of it: a rate claim needs no question, the other two are questions.
        assertFalse(overpriced.isQuestion, "the band beside it is already the argument")
        assertTrue(repeat.isQuestion)
        assertTrue(schedule.isQuestion)
    }

    /** The dots are the ranking, so they have to be ordered and distinct. */
    @Test
    fun `evidence is strongest from the owner's own record`() {
        assertEquals(3, Evidence.OwnRecord.strength)
        assertEquals(2, Evidence.RealBills(count = 14).strength)
        assertEquals(1, Evidence.CityRates.strength)
    }

    private fun check(
        flagged: List<FlaggedLine>,
        fine: List<PricedLine>,
        unchecked: List<PricedLine> = emptyList(),
    ) = BillCheck(
        car = "Swift VXi",
        city = "Srinagar",
        workshop = WorkshopTier.AUTHORISED,
        billTotal = rupees(18_400),
        flagged = flagged,
        fine = fine,
        unchecked = unchecked,
        canFlagRepeats = false,
    )

    private fun flagged(name: String, rupees: Int, reason: Reason) = FlaggedLine(
        name = name,
        amount = rupees(rupees),
        reason = reason,
        evidence = Evidence.CityRates,
    )

    private fun rupees(whole: Int) = Amount.of(whole * 100L).getOrNull() ?: Amount.ZERO
}
