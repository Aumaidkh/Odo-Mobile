package com.hopcape.odo.feature.billcheck.domain.usecase

import com.hopcape.odo.core.domain.advisory.matching.BillLineMatcher
import com.hopcape.odo.core.domain.advisory.matching.JobKind
import com.hopcape.odo.core.domain.schedule.ServiceInterval
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry

/**
 * When the maker's schedule puts a job further down the road.
 *
 * **This is a claim about the manufacturer, not about the car** (AI_ADVISORY_PLAN §2.8). Odo
 * does not know whether this engine burns oil, ran on bad fuel, or has a symptom the advisor
 * spotted — so it never says the job is unnecessary. It states two facts, the schedule's
 * number and the odometer's, and hands over the question.
 *
 * The rule is [RepeatFinder]'s, measured in kilometres instead of months: flagged when the car
 * is **less than halfway** from the last time the job was done to the next time it is due. At
 * 39,000 km against a 40,000 km interval the answer is "nearly", and asking is not worth the
 * owner's turn at the counter.
 */
internal class ScheduleChecker(
    private val matcher: BillLineMatcher,
    private val intervals: Map<String, ServiceInterval>,
) {

    /**
     * The job is not due yet, or null.
     *
     * [history] gives the last odometer this job was done at; with none, the first due is the
     * interval itself — which is the day-1 case, and the one the screens were drawn for.
     */
    fun notDueYet(kind: JobKind, currentKm: Int, history: List<ServiceLogEntry>): NotDue? {
        val intervalKm = intervals[kind.slug]?.km ?: return null
        // Only readings at or below the bill's own. An entry logged after it would put the
        // last service in the bill's future and compute a due point from it.
        val lastKm = history
            .filter { it.covers(kind) && it.odometer.km <= currentKm }
            .maxOfOrNull { it.odometer.km }
            ?: 0
        val dueAtKm = lastKm + intervalKm
        val travelled = currentKm - lastKm
        return if (travelled >= 0 && travelled * 2 < intervalKm) {
            NotDue(dueAtKm = dueAtKm, currentKm = currentKm)
        } else {
            null
        }
    }

    /** Two facts: when the maker says it comes round, and where the car is. */
    data class NotDue(val dueAtKm: Int, val currentKm: Int)

    /**
     * Asks what a line *covers*, not what it is.
     *
     * "Engine oil + filter" is `Unknown` to `match` on purpose — no band covers what that one
     * line charged for — and it is still proof that both jobs were done. Read through `match`,
     * a car whose every oil change is billed that way looked like a car that had never had one.
     */
    private fun ServiceLogEntry.covers(kind: JobKind): Boolean =
        lineItems.any { item -> kind in matcher.covers(item.label ?: return@any false) }
}
