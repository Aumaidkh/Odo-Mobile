package com.hopcape.odo.core.domain.servicelog.policy

import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * When a car's next service is due.
 *
 * A pure domain service — no repository, no clock. It comes in two forms: one that takes
 * the last service's date and reading directly, and one that takes the car's whole record
 * and works out which entry that is. The second exists because "which entry counts as the
 * last service" is part of the same question, and three surfaces asking it separately is
 * how a home card ends up saying a service is due above a score that says it isn't.
 *
 * The rule is **whichever comes first**, time or distance, which is how every Indian
 * service book is written. Neither number alone is enough: a car driven 25,000 km a year
 * needs a service long before six months are up, and a car that barely leaves the building
 * still needs its oil changed.
 *
 * Three surfaces read this. The health score turns it into maintenance points, Home's
 * attention card turns it into a prompt, and the reminder engine (M4) turns it into the
 * PRD's "service due in 800 km" push. All must agree on what "due" means, so the rule
 * lives here once — the same reason
 * [DocumentReminderPolicy][com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy]
 * owns the renewal window.
 */
object ServiceIntervalPolicy {

    /**
     * The service interval most manufacturers print for Indian petrol/diesel hatchbacks
     * and sedans. A per-model interval would be better and needs a data source the app
     * does not have yet; until then one honest default beats a made-up per-car number.
     */
    const val INTERVAL_MONTHS: Int = 6
    const val INTERVAL_KM: Int = 10_000

    /** How close to the interval counts as [ServiceDueStatus.DueSoon] — enough notice to book a slot. */
    const val DUE_SOON_DAYS: Int = 30
    const val DUE_SOON_KM: Int = 1_000

    /**
     * Where the car stands against its interval.
     *
     * @param lastServiceDate the day the car was last serviced, or `null` if it never was.
     * @param lastServiceOdometer the reading taken at that service.
     * @param currentOdometer the car's reading today. `null` (or a missing last reading)
     *   drops the distance half of the rule and leaves the answer on time alone, rather
     *   than guessing at kilometres nobody recorded.
     * @param today the day being read on — passed in, never read from a clock here, so the
     *   domain stays pure and the rule is trivially testable.
     */
    fun statusFor(
        lastServiceDate: LocalDate?,
        lastServiceOdometer: Distance?,
        currentOdometer: Distance?,
        today: LocalDate,
    ): ServiceDueStatus {
        if (lastServiceDate == null) return ServiceDueStatus.NeverServiced

        val daysLeft = today.daysUntil(lastServiceDate.plus(INTERVAL_MONTHS, DateTimeUnit.MONTH))
        val kmLeft = kmLeft(lastServiceOdometer, currentOdometer)

        val overdueOnTime = daysLeft < 0
        val overdueOnDistance = kmLeft != null && kmLeft < 0
        if (overdueOnTime || overdueOnDistance) {
            return ServiceDueStatus.Overdue(
                daysOverdue = (-daysLeft).coerceAtLeast(0),
                kmOverdue = kmLeft?.let { (-it).coerceAtLeast(0) },
            )
        }

        val dueSoon = daysLeft <= DUE_SOON_DAYS || (kmLeft != null && kmLeft <= DUE_SOON_KM)
        return if (dueSoon) {
            ServiceDueStatus.DueSoon(daysLeft = daysLeft, kmLeft = kmLeft)
        } else {
            ServiceDueStatus.NotDue(daysLeft = daysLeft, kmLeft = kmLeft)
        }
    }

    /**
     * Where the car stands, worked out from its whole record.
     *
     * The last service is the newest entry by date, ties going to the higher reading, and
     * the car's reading today is the newest of [readings] by the same rule — which is the
     * logs' readings plus the baseline onboarding took. A car with no entries has never
     * been serviced, whatever its odometer says.
     *
     * @param entries every non-deleted service log for the car.
     * @param readings every known odometer reading for the car.
     * @param today the day being read on.
     */
    fun statusFor(
        entries: List<ServiceLogEntry>,
        readings: List<OdometerReading>,
        today: LocalDate,
    ): ServiceDueStatus {
        val last = entries.maxWithOrNull(compareBy({ it.serviceDate }, { it.odometer.km }))
            ?: return ServiceDueStatus.NeverServiced
        return statusFor(
            lastServiceDate = last.serviceDate,
            lastServiceOdometer = last.odometer,
            currentOdometer = currentOdometer(readings),
            today = today,
        )
    }

    /** The car's reading today: the most recently *dated* one, ties going to the higher km. */
    private fun currentOdometer(readings: List<OdometerReading>): Distance? =
        readings.maxWithOrNull(compareBy({ it.date }, { it.odometer.km }))?.odometer

    /**
     * Kilometres left on the interval, or `null` when either reading is missing.
     *
     * A current reading below the last service's is not treated as distance already
     * driven — that is a broken timeline, and
     * [OdometerTimeline][com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline]
     * is what reports it. Here it simply means no distance has been proven since.
     */
    private fun kmLeft(lastServiceOdometer: Distance?, currentOdometer: Distance?): Int? {
        val last = lastServiceOdometer?.km ?: return null
        val current = currentOdometer?.km ?: return null
        val driven = (current - last).coerceAtLeast(0)
        return INTERVAL_KM - driven
    }
}

/**
 * How much service interval a car has left.
 *
 * [ServiceDueStatus.DueSoon.kmLeft] and friends are `null` when the car has no usable
 * odometer pair, so a surface can say "due in 12 days" without inventing a distance.
 */
sealed interface ServiceDueStatus {

    /** Nothing logged yet — there is no interval to be inside of. */
    data object NeverServiced : ServiceDueStatus

    /** Comfortably inside the interval. */
    data class NotDue(val daysLeft: Int, val kmLeft: Int?) : ServiceDueStatus

    /** Inside the notice window on time or distance — the state that earns a reminder. */
    data class DueSoon(val daysLeft: Int, val kmLeft: Int?) : ServiceDueStatus

    /**
     * The interval has passed. [daysOverdue] is `0` when only the distance ran out (and
     * [kmOverdue] `0` when only the time did), so each number means what it says rather
     * than going negative.
     */
    data class Overdue(val daysOverdue: Int, val kmOverdue: Int?) : ServiceDueStatus
}
