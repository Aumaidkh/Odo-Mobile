package com.hopcape.odo.core.domain.servicelog.model

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.Notes
import com.hopcape.odo.core.domain.shared.WorkshopName
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceLogEntryCreateTest {

    private val today = LocalDate(2026, 7, 3)
    private val id = ServiceLogId("log-1")
    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    private fun create(
        serviceDate: LocalDate? = LocalDate(2026, 6, 1),
        odometerKm: Int? = 45_000,
        totalAmountPaise: Long? = 280_000,
        workshopName: String? = "Sharma Motors",
        notes: String? = "Oil change",
    ) = ServiceLogEntry.create(
        id = id,
        carId = carId,
        ownerId = ownerId,
        serviceDate = serviceDate,
        odometerKm = odometerKm,
        totalAmountPaise = totalAmountPaise,
        today = today,
        workshopName = workshopName,
        notes = notes,
    )

    @Test
    fun validInput_buildsEntry_defaultsToManualWithNoBill() {
        val result = create()

        assertTrue(result.isRight(), "expected Right but was $result")
        val entry = result.getOrNull()!!
        assertEquals(45_000, entry.odometer.km)
        assertEquals(280_000L, entry.totalAmount.paise)
        assertEquals("Sharma Motors", entry.workshopName?.value)
        assertEquals("Oil change", entry.notes?.value)
        assertEquals(LogSource.MANUAL, entry.source)
        assertNull(entry.billId)
    }

    @Test
    fun missingServiceDate_isRejected() {
        assertTrue(create(serviceDate = null).leftOrNull()!!.contains(DomainError.MissingServiceDate))
    }

    @Test
    fun futureServiceDate_isRejected() {
        val future = LocalDate(2026, 7, 4)
        assertTrue(create(serviceDate = future).leftOrNull()!!.contains(DomainError.ServiceDateInFuture))
    }

    @Test
    fun todayServiceDate_isAccepted() {
        assertTrue(create(serviceDate = today).isRight())
    }

    @Test
    fun missingOdometer_isRejected() {
        assertTrue(create(odometerKm = null).leftOrNull()!!.contains(DomainError.MissingOdometer))
    }

    @Test
    fun negativeOdometer_isRejected() {
        assertTrue(create(odometerKm = -1).leftOrNull()!!.contains(DomainError.NegativeOdometer))
    }

    @Test
    fun negativeAmount_isRejected() {
        assertTrue(create(totalAmountPaise = -5).leftOrNull()!!.contains(DomainError.NegativeAmount))
    }

    @Test
    fun nullAmount_defaultsToZero() {
        assertEquals(0L, create(totalAmountPaise = null).getOrNull()?.totalAmount?.paise)
    }

    @Test
    fun blankWorkshopAndNotes_normalizeToNull() {
        val entry = create(workshopName = "   ", notes = "").getOrNull()!!
        assertNull(entry.workshopName)
        assertNull(entry.notes)
    }

    @Test
    fun tooLongWorkshopName_isRejected() {
        val errors = create(workshopName = "a".repeat(WorkshopName.MAX_LENGTH + 1)).leftOrNull()!!
        assertTrue(errors.any { it is DomainError.WorkshopNameTooLong })
    }

    @Test
    fun tooLongNotes_isRejected() {
        val errors = create(notes = "a".repeat(Notes.MAX_LENGTH + 1)).leftOrNull()!!
        assertTrue(errors.any { it is DomainError.NotesTooLong })
    }

    @Test
    fun multipleFailures_areAccumulated() {
        val errors = create(serviceDate = null, odometerKm = -1, totalAmountPaise = -1).leftOrNull()!!
        assertEquals(3, errors.size)
        assertTrue(errors.contains(DomainError.MissingServiceDate))
        assertTrue(errors.contains(DomainError.NegativeOdometer))
        assertTrue(errors.contains(DomainError.NegativeAmount))
    }
}
