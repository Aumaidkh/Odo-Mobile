package com.hopcape.odo.core.domain.servicelog.policy

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.policy.ServiceReminder.Kind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceReminderPlanTest {

    private val today = LocalDate(2026, 10, 28)

    @Test
    fun neverServiced_plansNothing() = assertEquals(emptyList(), ServiceReminderPlan.from(emptyList(), today))

    @Test
    fun aRecentService_plansDueSoonAndOverdue() {
        val plan = ServiceReminderPlan.from(listOf(entry(LocalDate(2026, 9, 1))), today)
        assertEquals(
            listOf(
                ServiceReminder(Kind.DUE_SOON, LocalDate(2027, 1, 30), LocalDate(2027, 3, 1)),
                ServiceReminder(Kind.OVERDUE, LocalDate(2027, 3, 2), LocalDate(2027, 3, 1)),
            ),
            plan,
        )
    }

    @Test
    fun insideTheWindow_dropsThePastDueSoonDay() {
        val plan = ServiceReminderPlan.from(listOf(entry(LocalDate(2026, 5, 10))), today)
        assertEquals(listOf(Kind.OVERDUE), plan.map { it.kind })
    }

    @Test
    fun alreadyOverdue_plansNothing() =
        assertEquals(emptyList(), ServiceReminderPlan.from(listOf(entry(LocalDate(2026, 1, 1))), today))

    @Test
    fun theNewestServiceCounts() {
        val plan = ServiceReminderPlan.from(listOf(entry(LocalDate(2026, 1, 1)), entry(LocalDate(2026, 9, 1))), today)
        assertEquals(LocalDate(2027, 3, 1), plan.first().dueOn)
    }

    private fun entry(date: LocalDate) = ServiceLogEntry.create(
        id = ServiceLogId("log-$date"),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = date,
        odometerKm = 10_000,
        totalAmountPaise = 100_000,
        today = today,
    ).getOrElse { error("invalid fixture: $it") }
}
