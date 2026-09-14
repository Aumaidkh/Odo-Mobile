package com.hopcape.odo.feature.advisory.domain.checklist

import com.hopcape.odo.core.domain.advisory.matching.BillLineMatcher
import com.hopcape.odo.core.domain.schedule.ServiceInterval
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus

/**
 * Splits the maker's schedule into what this service should cover and what it should not.
 *
 * Pure — schedule rows in, two lists out. Everything it needs about the owner is already in
 * [ServiceLogEntry]; there is no repository here and no clock, so the whole screen is one
 * assertion away in a test.
 *
 * **Due is whichever comes first**, time or distance, the same rule
 * [ServiceIntervalPolicy][com.hopcape.odo.core.domain.servicelog.policy.ServiceIntervalPolicy]
 * applies to the car as a whole. A job the record has never seen is due once the car has
 * driven one interval, so a five-minute-old install still gets a list.
 */
internal class PreServiceChecklistBuilder(
    private val matcher: BillLineMatcher,
) {

    /**
     * @param schedule the maker's rows, keyed by slug — already resolved for this make.
     * @param history every non-deleted service log for the car.
     * @param currentKm the car's reading today.
     * @param carAddedOn the day the car entered Odo. It anchors a time-based job the record
     *   has never seen: without it there is no date to count months from, and counting from
     *   the car's model year would call a two-year-old brake fluid change overdue on a car
     *   bought last month.
     * @param today the day being read on.
     */
    fun build(
        schedule: Map<String, ServiceInterval>,
        history: List<ServiceLogEntry>,
        currentKm: Int,
        carAddedOn: LocalDate?,
        today: LocalDate,
        /**
         * How far this car goes in a month, for comparing a distance deadline against a time
         * one. A default rather than a measurement: the comparison only decides which of two
         * true numbers to print, so being roughly right is enough, and reading the car's real
         * pace here would make the whole builder depend on the fill history.
         */
        kmPerMonth: Int = TYPICAL_KM_PER_MONTH,
    ): PreServiceChecklist {
        val done = lastDoneBySlug(history)
        val due = mutableListOf<ChecklistItem>()
        val notYet = mutableListOf<ChecklistItem>()

        schedule.values.sortedBy { it.slug }.forEach { interval ->
            val last = done[interval.slug]
            val item = interval.assess(last, currentKm, carAddedOn, today, history.size, kmPerMonth)
            if (item.reason.isDue) due += item else notYet += item
        }

        return PreServiceChecklist(due = due, notYet = notYet + upsells(schedule))
    }

    /** Where each job stands: due, or with something still to run. */
    private fun ServiceInterval.assess(
        last: LastDone?,
        currentKm: Int,
        carAddedOn: LocalDate?,
        today: LocalDate,
        historySize: Int,
        kmPerMonth: Int,
    ): ChecklistItem {
        val kmToGo = km?.let { (last?.km ?: 0) + it - currentKm }
        val since = last?.date ?: carAddedOn
        val monthsToGo = months?.let { m -> since?.let { it.plus(m, DateTimeUnit.MONTH).let(today::monthsUntil) } }

        val reason = when {
            kmToGo != null && kmToGo <= 0 ->
                last?.let { ChecklistReason.LastDoneKmAgo(currentKm - it.km) } ?: ChecklistReason.NeverRecorded

            monthsToGo != null && monthsToGo <= 0 ->
                last?.let { ChecklistReason.LastDoneMonthsAgo(it.date.monthsUntil(today)) }
                    ?: ChecklistReason.NeverRecorded

            // Not due on either axis, and a maker row often carries both ("every 10,000 km
            // or 12 months"). The nearer deadline is the one worth quoting — quoting the
            // distance on a job that runs out of months first tells the owner they have
            // 39,000 km when they have four weeks. Compared on the car's own usual pace;
            // distance wins a tie, because it is what an owner can check on the dashboard.
            kmToGo != null && monthsToGo != null ->
                if (kmToGo <= monthsToGo * kmPerMonth) ChecklistReason.KmToGo(kmToGo)
                else ChecklistReason.MonthsToGo(monthsToGo)

            kmToGo != null -> ChecklistReason.KmToGo(kmToGo)
            monthsToGo != null -> ChecklistReason.MonthsToGo(monthsToGo)

            // A months-only row with nothing to count from. The schedule does list it, so it
            // must not be labelled "not in the schedule" — that section is what the owner is
            // being told to refuse. Nothing proves it was done, which is what this says.
            else -> ChecklistReason.NeverRecorded
        }

        return ChecklistItem(
            slug = slug,
            label = ItemLabel.FromSchedule(displayName),
            reason = reason,
            servicesSince = last?.let { historySize - it.entriesBefore } ?: 0,
        )
    }

    /** The four counter upsells, minus any the schedule actually asks for. */
    private fun upsells(schedule: Map<String, ServiceInterval>): List<ChecklistItem> =
        CounterUpsell.entries
            .filterNot { it.slug in schedule }
            .take(MAX_UPSELLS)
            .map {
                ChecklistItem(
                    slug = it.slug,
                    label = ItemLabel.Upsell(it),
                    reason = ChecklistReason.NotInSchedule,
                )
            }

    /**
     * The last time each job appears in the record.
     *
     * Read through the same matcher the bill check uses, asking what a line *covers* rather
     * than what it is. A combined line has no single job to price and still proves both were
     * done, and history only ever needs the proof.
     */
    private fun lastDoneBySlug(history: List<ServiceLogEntry>): Map<String, LastDone> {
        val ordered = history.sortedWith(compareBy({ it.serviceDate }, { it.odometer.km }))
        val done = mutableMapOf<String, LastDone>()
        ordered.forEachIndexed { index, entry ->
            entry.lineItems.asSequence()
                .mapNotNull { it.label }
                .flatMap { matcher.covers(it) }
                .map { it.slug }
                .forEach { slug ->
                    done[slug] = LastDone(entry.odometer.km, entry.serviceDate, entriesBefore = index + 1)
                }
        }
        return done
    }

    /** The reading, the day, and how far down the record that entry sat. */
    private data class LastDone(val km: Int, val date: LocalDate, val entriesBefore: Int)

    private companion object {
        /** Three fills the section without turning it into a list of everything Odo distrusts. */
        const val MAX_UPSELLS = 3

        /** ~12,000 km a year, the figure the PRD's 10,000 km / 6 month interval assumes. */
        const val TYPICAL_KM_PER_MONTH = 1_000
    }
}

/** Whether this reason puts the job in "should cover" rather than "not needed yet". */
internal val ChecklistReason.isDue: Boolean
    get() = when (this) {
        is ChecklistReason.LastDoneKmAgo,
        is ChecklistReason.LastDoneMonthsAgo,
        ChecklistReason.NeverRecorded,
        -> true

        is ChecklistReason.KmToGo,
        is ChecklistReason.MonthsToGo,
        ChecklistReason.NotInSchedule,
        -> false
    }
