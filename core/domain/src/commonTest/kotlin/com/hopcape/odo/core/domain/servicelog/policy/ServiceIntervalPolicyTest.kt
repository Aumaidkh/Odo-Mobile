package com.hopcape.odo.core.domain.servicelog.policy

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ServiceIntervalPolicyTest {

    private val today = LocalDate(2026, 8, 1)

    private fun km(value: Int) = Distance.of(value).getOrElse { error("test fixture km=$value") }

    private fun statusFor(
        lastServiceDate: LocalDate? = LocalDate(2026, 7, 1),
        lastServiceKm: Int? = 40_000,
        currentKm: Int? = 41_000,
    ) = ServiceIntervalPolicy.statusFor(
        lastServiceDate = lastServiceDate,
        lastServiceOdometer = lastServiceKm?.let(::km),
        currentOdometer = currentKm?.let(::km),
        today = today,
    )

    @Test
    fun carThatWasNeverServiced_hasNoInterval() {
        assertEquals(ServiceDueStatus.NeverServiced, statusFor(lastServiceDate = null))
    }

    @Test
    fun freshServiceWithFewKilometres_isNotDue() {
        val status = assertIs<ServiceDueStatus.NotDue>(statusFor())

        // 1 Jul + 6 months = 1 Jan 2027, which is 153 days after 1 Aug 2026.
        assertEquals(153, status.daysLeft)
        assertEquals(9_000, status.kmLeft)
    }

    @Test
    fun withinThirtyDaysOfTheInterval_isDueSoon() {
        val status = assertIs<ServiceDueStatus.DueSoon>(
            statusFor(lastServiceDate = LocalDate(2026, 2, 15)),
        )

        assertEquals(14, status.daysLeft)
    }

    @Test
    fun withinAThousandKilometresOfTheInterval_isDueSoon() {
        // Serviced only a month ago, but the car has already covered 9,200 km.
        val status = assertIs<ServiceDueStatus.DueSoon>(statusFor(currentKm = 49_200))

        assertEquals(800, status.kmLeft)
    }

    @Test
    fun pastSixMonths_isOverdue() {
        val status = assertIs<ServiceDueStatus.Overdue>(
            statusFor(lastServiceDate = LocalDate(2025, 12, 1)),
        )

        // 1 Dec 2025 + 6 months = 1 Jun 2026, 61 days before 1 Aug 2026.
        assertEquals(61, status.daysOverdue)
        assertEquals(0, status.kmOverdue, "time ran out, not distance")
    }

    @Test
    fun pastTenThousandKilometres_isOverdue() {
        val status = assertIs<ServiceDueStatus.Overdue>(statusFor(currentKm = 52_500))

        assertEquals(0, status.daysOverdue, "distance ran out, not time")
        assertEquals(2_500, status.kmOverdue)
    }

    @Test
    fun withoutACurrentReading_theRuleFallsBackToTime() {
        val status = assertIs<ServiceDueStatus.NotDue>(statusFor(currentKm = null))

        assertEquals(153, status.daysLeft)
        assertNull(status.kmLeft, "no reading means no distance to quote")
    }

    @Test
    fun aReadingBelowTheLastService_countsAsNoDistanceDriven() {
        // A broken timeline is OdometerTimeline's business; here it must not read as
        // negative distance and turn a fresh service overdue.
        val status = assertIs<ServiceDueStatus.NotDue>(statusFor(currentKm = 30_000))

        assertEquals(ServiceIntervalPolicy.INTERVAL_KM, status.kmLeft)
    }

    @Test
    fun theDayTheIntervalRunsOut_isStillDueSoonRatherThanOverdue() {
        val status = assertIs<ServiceDueStatus.DueSoon>(
            statusFor(lastServiceDate = LocalDate(2026, 2, 1)),
        )

        assertEquals(0, status.daysLeft)
    }
}
