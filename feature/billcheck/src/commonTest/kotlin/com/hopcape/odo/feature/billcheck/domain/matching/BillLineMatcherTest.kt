package com.hopcape.odo.feature.billcheck.domain.matching

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a bill line reads as.
 *
 * The traps are not hypothetical — they are how Indian workshops write. "Brake oil" is brake
 * fluid, "oil filter" is its own priced job, and both contain the word a naive matcher would
 * key engine oil on. Every one of those below is a wrong answer this table would have given
 * if the rules were checked in any other order.
 */
class BillLineMatcherTest {

    private val matcher = BillLineMatcher()

    /* ------------------------------ The traps ------------------------------ */

    /**
     * The one that matters most. Half of India calls brake fluid "brake oil", and pricing it
     * as engine oil is a ~7x error in the band — which the screen would then present as a
     * finding, with a rupee figure, at a counter.
     */
    @Test
    fun `brake oil is brake fluid and not engine oil`() {
        assertEquals(JobKind.BRAKE_FLUID, matcher.kindOf("Brake oil top up"))
    }

    @Test
    fun `an oil filter is its own job and not engine oil`() {
        assertEquals(JobKind.OIL_FILTER, matcher.kindOf("Oil filter"))
        assertEquals(JobKind.OIL_FILTER, matcher.kindOf("OIL FLTR - genuine"))
    }

    /** "Engine oil + filter" is one printed line and two priced jobs. It names one of them. */
    @Test
    fun `engine oil and filter names the filter it mentions`() {
        assertEquals(JobKind.OIL_FILTER, matcher.kindOf("Engine oil + filter"))
    }

    @Test
    fun `brake pads beat the bare brakes rule`() {
        assertEquals(JobKind.BRAKE_PADS, matcher.kindOf("Brake pad set - front"))
        assertEquals(JobKind.BRAKES, matcher.kindOf("Brake overhaul"))
    }

    /** A workshop writes ac three ways and means one thing. */
    @Test
    fun `every spelling of ac service is the same job`() {
        listOf("AC service", "A/C service", "A.C. Service", "AC gas top up", "Aircon service")
            .forEach { assertEquals(JobKind.AC_SERVICE, matcher.kindOf(it), it) }
    }

    /**
     * Checked before every job rule, because "labour charges for AC service" is a labour line.
     * Priced as an AC service it would put a whole job's band against a labour charge.
     */
    @Test
    fun `a labour line is not the job it names`() {
        assertEquals(LineMatch.NotAJob, matcher.match("Labour charges - AC service"))
        assertEquals(LineMatch.NotAJob, matcher.match("Consumables"))
        assertEquals(LineMatch.NotAJob, matcher.match("CGST 9%"))
    }

    /** Whole words only, or "oil" matches inside a part number and prices the wrong job. */
    @Test
    fun `a word inside another word is not a match`() {
        assertEquals(LineMatch.Unknown, matcher.match("Boiler gasket"))
    }

    /* ------------------------------ Coverage ------------------------------ */

    /**
     * A corpus of how the lines on a real Indian service bill are printed.
     *
     * Coverage is the number that decides whether this feature answers anything, so it is
     * asserted rather than eyeballed. The floor is deliberately below the current figure: this
     * is a regression guard, not a target, and a rule added for one bill must not quietly cost
     * another.
     */
    @Test
    fun `the corpus is mostly named`() {
        val named = CORPUS.count { matcher.match(it) !is LineMatch.Unknown }
        val coverage = named * 100 / CORPUS.size

        assertTrue(
            coverage >= FLOOR,
            "named $named of ${CORPUS.size} ($coverage%), floor $FLOOR% — " +
                "unnamed: ${CORPUS.filter { matcher.match(it) is LineMatch.Unknown }}",
        )
    }

    /**
     * The lines the rules deliberately cannot name.
     *
     * Both are on the mockups, and neither is a category the server has — they are the reason
     * the screen needs a bucket that is not "priced fine". When the catalogue grows, these
     * move into the corpus above.
     */
    @Test
    fun `a job the catalogue has no category for is left unknown`() {
        assertEquals(LineMatch.Unknown, matcher.match("Throttle body cleaning"))
        assertEquals(LineMatch.Unknown, matcher.match("Injector cleaning"))
    }

    private fun BillLineMatcher.kindOf(line: String): JobKind? =
        (match(line) as? LineMatch.Job)?.kind

    private companion object {
        const val FLOOR = 80

        val CORPUS = listOf(
            "Engine oil 5W-30",
            "Engine Oil + Filter",
            "Oil filter",
            "Air filter",
            "Air cleaner element",
            "AC service",
            "A/C gas top up",
            "Coolant top up",
            "Brake pads - front",
            "Brake shoe set rear",
            "Brake fluid",
            "Brake oil",
            "Brake disc turning",
            "Wheel alignment",
            "Wheel alignment & balancing",
            "Tyre rotation",
            "Wiper blades",
            "Battery replacement",
            "Clutch plate assembly",
            "Shock absorber - front RH",
            "Alternator repair",
            "Denting and painting - LH door",
            "Periodic service",
            "Paid service - 40000 km",
        )
    }
}
