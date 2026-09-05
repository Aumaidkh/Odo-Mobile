package com.hopcape.odo.feature.billcheck.domain

import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.core.domain.shared.sum

/**
 * One bill, read line by line, with the lines worth asking about pulled to the top.
 *
 * Not an overcharge verdict. The app states what it can defend and hands back the question —
 * with no history it cannot know whether a job was skipped last time or an advisor spotted a
 * real symptom (AI_ADVISORY_PLAN §2.8).
 */
internal data class BillCheck(
    /** The car as the header names it, e.g. "Swift VXi". */
    val car: String,
    val city: String,
    /** The kind of workshop. Worded by the screen — the header shouts it, a sentence does not. */
    val workshop: WorkshopTier,
    val billTotal: Amount,
    /** Sorted strongest first — the owner's own record before a city estimate. */
    val flagged: List<FlaggedLine>,
    /** Checked against a band, and priced fine. Shown, never hidden. */
    val fine: List<PricedLine>,
    /**
     * Lines the check could not read.
     *
     * A third bucket, not a fold into [fine]. `fine` is drawn with a tick and says "we
     * checked this and the price is fair" — a line Odo could not name, or one whose job the
     * price tables do not carry, is not that, and ticking it would be the app claiming
     * something it did not do. Saying so also shows the owner where the coverage ends.
     */
    val unchecked: List<PricedLine>,
    /**
     * Whether repeats could be looked for at all. False until the owner has a record, and
     * the screen then says what adding one would buy rather than showing nothing.
     */
    val canFlagRepeats: Boolean,
) {
    /** What the flagged lines add up to. Derived, so the headline can never disagree. */
    val worthAsking: Amount = flagged.map { it.amount }.sum()

    val lineCount: Int = flagged.size + fine.size + unchecked.size
}

/** A line the check has something to say about. */
internal data class FlaggedLine(
    val name: String,
    val amount: Amount,
    val reason: Reason,
    /**
     * How much the finding rests on, or null where the question is not about price at all.
     *
     * A schedule claim has none: it is the maker's published interval against the odometer,
     * and dots that rank price evidence would be answering a question nobody asked.
     */
    val evidence: Evidence?,
) {
    /**
     * Whether this finding hands the owner a question to put to the advisor.
     *
     * A rate claim does not: the band beside it is already the whole argument. A repeat and a
     * schedule claim do — both are facts about the record or the maker, and the app cannot
     * say whether they apply to this car (AI_ADVISORY_PLAN §2.8). The wording is the screen's,
     * because it is copy.
     */
    val isQuestion: Boolean = reason !is Reason.AboveBand

    /**
     * Whether the rupee figure itself is part of the claim.
     *
     * A rate or a repeat is a claim about money — the table can defend it, so the amount is
     * drawn as part of the finding. A schedule question is a claim about the *maker*, not
     * about this car or its price, so its amount stays as plain as any other line's.
     */
    val amountIsTheClaim: Boolean = reason !is Reason.ScheduledLater
}

/** A line with nothing to ask about. Priced, listed, and left alone. */
internal data class PricedLine(val name: String, val amount: Amount)

/** Why a line was flagged. One per kind of claim the app is willing to make. */
internal sealed interface Reason {

    /** The owner's own record shows this job recently. The strongest thing Odo can say. */
    data class DoneRecently(val monthsAgo: Int, val on: LocalDate) : Reason

    /** Charged above what this car, in this city, at this kind of workshop normally is. */
    data class AboveBand(val low: Amount, val high: Amount) : Reason

    /**
     * The maker's schedule puts this later. Deliberately not "you do not need this" — that
     * is a verdict about the car, and this is a fact about the manufacturer.
     */
    data class ScheduledLater(val dueAtKm: Int, val currentKm: Int) : Reason
}

/**
 * How much the finding rests on.
 *
 * Three rungs rather than the benchmark ladder's six (FAIRNESS_SYSTEM_DESIGN §5.4): the owner
 * is deciding whether to argue at a counter, and "which of six SQL filters answered" is not
 * what helps them do it. The ladder's own scope is shown in full on the "How we know" sheet.
 */
internal sealed interface Evidence {

    /** Dots filled, out of three. */
    val strength: Int

    /** From the owner's own service record. Nothing beats it. */
    data object OwnRecord : Evidence {
        override val strength: Int = 3
    }

    /** From bills other owners actually paid, in this bucket. */
    data class RealBills(val count: Int) : Evidence {
        override val strength: Int = 2
    }

    /** Computed from parts and city labour rates, because no bills have collected yet. */
    data object CityRates : Evidence {
        override val strength: Int = 1
    }
}
