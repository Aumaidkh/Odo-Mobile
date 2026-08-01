package com.hopcape.odo.core.domain.servicelog.analysis

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * The odometer-ordering rule: an odometer only ever counts up, so a reading must sit
 * between the readings around it **in date order**.
 *
 * A pure domain service — no repository, no clock, no side effects. It is a service rather
 * than a method on [ServiceLogEntry][com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry]
 * because the rule is *cross-entity*: it compares one reading against every other reading
 * the car has, which no single entry can see. The caller (a use case) fetches the readings
 * and hands them here; the arithmetic stays testable without a database.
 *
 * **Date-relative, not "highest wins".** Logging history backwards is a first-class flow —
 * a car onboarded today at 45,000 km can still have its 2024 service (30,000 km) added
 * afterwards, and that is the resale-proof the PRD is built around. So the reading is
 * checked against its *neighbours*: at least the **highest** reading dated before it, at
 * most the **lowest** reading dated after it — the tightest bound on each side, which on a
 * consistent timeline is simply the adjacent entry.
 *
 * **Same-day readings do not constrain each other.** A service date is a date, with no time
 * of day, so two services on one day have no knowable order — constraining them would
 * reject the honest case (two garages, one day) to catch nothing.
 */
object OdometerTimeline {

    /**
     * Check [candidate] against the car's other [known] readings.
     *
     * The candidate excludes itself by [OdometerReading.logId], which is what makes an
     * **edit** work: correcting the newest entry's reading downwards is a typo fix, not a
     * regression, and comparing it against its own stored value would reject every such fix.
     *
     * Returns the candidate on success so callers can `bind()` it into a larger `either {}`.
     */
    fun validate(
        candidate: OdometerReading,
        known: List<OdometerReading>,
    ): Either<DomainError, OdometerReading> {
        val others = known.filterNot { candidate.logId != null && it.logId == candidate.logId }
        val km = candidate.odometer.km

        val previous = others.filter { it.date < candidate.date }.maxByOrNull { it.odometer.km }
        if (previous != null && km < previous.odometer.km) {
            return DomainError.OdometerRegression(previousKm = previous.odometer.km, attemptedKm = km).left()
        }

        val next = others.filter { it.date > candidate.date }.minByOrNull { it.odometer.km }
        if (next != null && km > next.odometer.km) {
            return DomainError.OdometerAheadOfLaterEntry(nextKm = next.odometer.km, attemptedKm = km).left()
        }

        return candidate.right()
    }

    /**
     * Every place the car's readings go backwards, oldest first.
     *
     * [validate] answers "may this one reading be saved"; this answers "does the history
     * already on file hold together". The health score is what asks — a timeline with a
     * rollback in it is exactly the thing a buyer is trying to spot, and the score cannot
     * ask about one reading at a time.
     *
     * Rows that predate the app can still be inconsistent, because history is logged
     * backwards and each entry was only ever checked against the readings that existed at
     * the time. So this rescans the whole list rather than trusting that every write passed
     * [validate].
     *
     * Same rules as [validate]: a reading is measured against the **highest** reading dated
     * strictly before it, and readings sharing a date do not constrain each other.
     */
    fun anomalies(readings: List<OdometerReading>): List<OdometerAnomaly> {
        val found = mutableListOf<OdometerAnomaly>()
        var peak: OdometerReading? = null

        readings.sortedBy { it.date }.groupBy { it.date }.values.forEach { sameDay ->
            val before = peak
            if (before != null) {
                sameDay.filter { it.odometer.km < before.odometer.km }
                    .forEach { found += OdometerAnomaly(earlier = before, later = it) }
            }
            val highest = sameDay.maxBy { it.odometer.km }
            if (before == null || highest.odometer.km > before.odometer.km) peak = highest
        }

        return found
    }
}

/**
 * One reading that sits below a reading taken before it — an odometer that counted down.
 *
 * Carries both readings rather than just a count, so whatever reports it can name the two
 * entries involved instead of telling the owner something is wrong somewhere.
 */
data class OdometerAnomaly(
    val earlier: OdometerReading,
    val later: OdometerReading,
) {
    /** How far the reading fell, in kilometres. Always positive. */
    val droppedByKm: Int get() = earlier.odometer.km - later.odometer.km
}
