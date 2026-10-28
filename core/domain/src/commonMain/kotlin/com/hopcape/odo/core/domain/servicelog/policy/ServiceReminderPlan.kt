package com.hopcape.odo.core.domain.servicelog.policy

import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A service-due notification and the day it is posted on. */
data class ServiceReminder(val kind: Kind, val on: LocalDate, val dueOn: LocalDate) {
    enum class Kind { DUE_SOON, OVERDUE }
}

/**
 * The time-based service reminders for a car, from [ServiceIntervalPolicy]'s interval. Days
 * before [today] are dropped, so a rebuilt schedule never posts a stale reminder.
 */
object ServiceReminderPlan {

    fun from(entries: List<ServiceLogEntry>, today: LocalDate): List<ServiceReminder> {
        val last = entries.maxOfOrNull { it.serviceDate } ?: return emptyList()
        val dueOn = last.plus(ServiceIntervalPolicy.INTERVAL_MONTHS, DateTimeUnit.MONTH)
        return listOf(
            ServiceReminder(ServiceReminder.Kind.DUE_SOON, dueOn.minus(ServiceIntervalPolicy.DUE_SOON_DAYS, DateTimeUnit.DAY), dueOn),
            ServiceReminder(ServiceReminder.Kind.OVERDUE, dueOn.plus(1, DateTimeUnit.DAY), dueOn),
        ).filter { it.on >= today }
    }
}
