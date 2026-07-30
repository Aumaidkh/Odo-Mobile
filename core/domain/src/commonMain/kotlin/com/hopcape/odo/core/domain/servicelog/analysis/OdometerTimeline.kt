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
}
