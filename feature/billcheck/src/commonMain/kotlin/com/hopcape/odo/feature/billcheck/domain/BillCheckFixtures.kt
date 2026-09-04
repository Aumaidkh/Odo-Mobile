package com.hopcape.odo.feature.billcheck.domain

import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.WorkshopTier

/**
 * The two scenes the bill check has to win — the previews' data and the stub's answers.
 *
 * In `domain` rather than beside the previews: the stub reads them, and a reader reaching up
 * into presentation would point the module's dependencies the wrong way.
 *
 * Both are the same ₹18,400 bill: the difference is what Odo knows about the car. Day 1 has
 * only reference data and what onboarding asked; month 6 also has the owner's own record, and
 * the record is what turns a two-line answer into a three-line one
 * (AI_ADVISORY_PLAN §2, Scene 1).
 */
internal object BillCheckFixtures {

    /** Day 1 — nothing but Tier 0 and Tier 1, and still two findings. */
    val dayOne = BillCheck(
        car = "Swift VXi",
        city = "Srinagar",
        workshop = WorkshopTier.AUTHORISED,
        billTotal = rupees(18_400),
        flagged = listOf(
            FlaggedLine(
                name = "AC service",
                amount = rupees(2_400),
                reason = Reason.AboveBand(low = rupees(1_400), high = rupees(1_800)),
                evidence = Evidence.CityRates,
                ask = null,
            ),
            FlaggedLine(
                name = "Injector cleaning",
                amount = rupees(3_100),
                reason = Reason.ScheduledLater(dueAtKm = 40_000, currentKm = 12_000),
                evidence = Evidence.CityRates,
                ask = "Why is injector cleaning needed now?",
            ),
        ),
        fine = listOf(
            PricedLine("Engine oil + filter", rupees(5_800)),
            PricedLine("Air filter", rupees(950)),
        ),
        // Both are on the mockups and neither is a category the server carries, which is
        // exactly why this bucket exists.
        unchecked = listOf(
            PricedLine("Throttle body", rupees(1_800)),
            PricedLine("Labour + consumables", rupees(4_350)),
        ),
        canFlagRepeats = false,
    )

    /**
     * Month 6 — the same bill with a record behind it.
     *
     * Throttle body moves from "priced fine" to the strongest finding on the screen, because
     * the owner's own record says it was done in April. Nothing about the price changed.
     */
    val monthSix = BillCheck(
        car = "Swift VXi",
        city = "Srinagar",
        workshop = WorkshopTier.AUTHORISED,
        billTotal = rupees(18_400),
        flagged = listOf(
            FlaggedLine(
                name = "Throttle body",
                amount = rupees(1_800),
                reason = Reason.DoneRecently(monthsAgo = 4, on = "12 April"),
                evidence = Evidence.OwnRecord,
                ask = "This was done 4 months ago — why again?",
            ),
            FlaggedLine(
                name = "AC service",
                amount = rupees(2_400),
                reason = Reason.AboveBand(low = rupees(1_500), high = rupees(1_700)),
                evidence = Evidence.RealBills(count = 14),
                ask = null,
            ),
            FlaggedLine(
                name = "Injector cleaning",
                amount = rupees(3_100),
                reason = Reason.ScheduledLater(dueAtKm = 40_000, currentKm = 12_000),
                evidence = Evidence.CityRates,
                ask = null,
            ),
        ),
        fine = listOf(
            PricedLine("Engine oil + filter", rupees(5_800)),
            PricedLine("Air filter", rupees(950)),
        ),
        unchecked = listOf(PricedLine("Labour + consumables", rupees(4_350))),
        canFlagRepeats = true,
    )

    /** The band behind the AC service line on the day-1 result. */
    val acServiceBasis = BandBasis(
        lineName = "AC service",
        low = rupees(1_400),
        high = rupees(1_800),
        city = "Srinagar",
        cityTier = 2,
        workshop = WorkshopTier.AUTHORISED,
        segment = "1.2L petrol hatchback",
        labourRatePerHour = rupees(520),
        labourHours = 1.5,
        rungs = listOf(
            Rung(BandScope.THIS_CAR_THIS_CENTRE, RungState.NO_DATA),
            Rung(BandScope.CITY_TIER_SEGMENT, RungState.USED),
            Rung(BandScope.NATIONAL, RungState.NOT_NEEDED),
        ),
    )

    /** The constructor is private — money is validated on the way in, even in a fixture. */
    private fun rupees(whole: Int) =
        Amount.of(whole * PAISE_PER_RUPEE).getOrNull() ?: Amount.ZERO

    private const val PAISE_PER_RUPEE = 100L
}
