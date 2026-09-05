package com.hopcape.odo.feature.billcheck.domain.usecase

import com.hopcape.odo.core.domain.advisory.matching.BillLineMatcher
import com.hopcape.odo.core.domain.advisory.matching.JobKind
import com.hopcape.odo.core.domain.schedule.ServiceInterval
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * When the owner's own record already shows this job.
 *
 * The strongest thing Odo can say about a bill, and the only claim here that rests on the
 * owner's own data rather than a table. It is still a question, not a verdict: there are good
 * reasons to redo a job early, and the app does not know whether one applies (AI_ADVISORY_PLAN
 * §2.8). It states when the job was done and hands the question over.
 *
 * **The rule, in one sentence:** a repeat is a job the record shows again *less than halfway*
 * to when it is next due.
 *
 * Halfway rather than "before it was due", because a job done at eleven months against a
 * twelve-month interval is not early, it is nearly due — and flagging it would put a question
 * in front of an owner that the advisor answers in one word. Where the maker's schedule says
 * nothing, due is taken as a year, which is the ordinary service cycle, so the window is six
 * months.
 */
internal class RepeatFinder(
    private val matcher: BillLineMatcher,
    private val intervals: Map<String, ServiceInterval>,
) {

    /**
     * The most recent earlier entry showing [kind], or null.
     *
     * [history] is every entry for the car; [on] is the date of the bill being checked, so an
     * entry from the same day or later is not a repeat of it.
     */
    fun previous(kind: JobKind, history: List<ServiceLogEntry>, on: LocalDate): Repeat? {
        val previous = history
            .filter { it.serviceDate < on && it.covers(kind) }
            .maxByOrNull { it.serviceDate }
            ?: return null

        val months = previous.serviceDate.monthsUntil(on)
        return if (months * 2 < dueInMonths(kind)) {
            Repeat(monthsAgo = months, on = previous.serviceDate)
        } else {
            null
        }
    }

    /** A job the record already shows. */
    data class Repeat(val monthsAgo: Int, val on: LocalDate)

    /**
     * When the schedule says this job comes round again.
     *
     * A year where it says nothing. That is a stated default rather than a guess dressed as
     * data: the alternative is either never asking about a job the tables do not carry, or
     * asking about every one of them.
     */
    private fun dueInMonths(kind: JobKind): Int =
        intervals[kind.slug]?.months ?: DEFAULT_DUE_MONTHS

    /**
     * Whether an entry included this job.
     *
     * Read from its line labels through the same matcher the bill is read with, so history and
     * the bill in hand are named the same way. An entry logged by hand with no lines cannot
     * answer, and says so by not matching rather than by guessing from its categories — the
     * nine coarse `ServiceCategory` tags cannot tell an oil filter from an air filter.
     *
     * It asks what a line *covers*, not what it is. "Engine oil + filter" is `Unknown` to
     * `match` on purpose — no band covers what that one line charged for — and it is still
     * proof the job was done. Asked the other way, a car whose oil change is always billed
     * on one line never had a repeat flagged against it.
     */
    private fun ServiceLogEntry.covers(kind: JobKind): Boolean =
        lineItems.any { item -> kind in matcher.covers(item.label ?: return@any false) }

    /** Whole months between two dates, rounded down. */
    private fun LocalDate.monthsUntil(other: LocalDate): Int =
        daysUntil(other) / DAYS_PER_MONTH

    private companion object {
        const val DEFAULT_DUE_MONTHS = 12
        const val DAYS_PER_MONTH = 30
    }
}
