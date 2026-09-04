package com.hopcape.odo.feature.billcheck.presentation.result

import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.feature.billcheck.domain.BillCheck
import com.hopcape.odo.feature.billcheck.domain.Evidence
import com.hopcape.odo.feature.billcheck.domain.FlaggedLine
import com.hopcape.odo.feature.billcheck.domain.PricedLine
import com.hopcape.odo.feature.billcheck.domain.Reason

/**
 * The two scenes this screen has to win, as previews and as the stub's answers.
 *
 * Both are the same ₹18,400 bill: the difference is what Odo knows about the car. Day 1 has
 * only reference data and what onboarding asked; month 6 also has the owner's own record, and
 * the record is what turns a two-line answer into a three-line one
 * (AI_ADVISORY_PLAN §2, Scene 1).
 */
internal object BillCheckPreviewData {

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
            PricedLine("Labour + consumables", rupees(4_350)),
        ),
        canFlagRepeats = true,
    )

    /** The constructor is private — money is validated on the way in, even in a preview. */
    private fun rupees(whole: Int) =
        Amount.of(whole * PAISE_PER_RUPEE).getOrNull() ?: Amount.ZERO

    private const val PAISE_PER_RUPEE = 100L
}
