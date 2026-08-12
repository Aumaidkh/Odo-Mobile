package com.hopcape.odo.core.domain.servicelog.model

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class VerificationStatusTest {

    private fun entry(billId: BillId?) = ServiceLogEntry.reconstitute(
        id = ServiceLogId("log-1"),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = LocalDate(2026, 1, 1),
        odometerKm = 40_000,
        totalAmountPaise = 0,
        workshopName = null,
        notes = null,
        source = if (billId != null) LogSource.SCANNED else LogSource.MANUAL,
        billId = billId,
    )

    @Test
    fun withoutBill_isSelfReported() {
        assertEquals(VerificationStatus.SELF_REPORTED, entry(billId = null).verification)
    }

    @Test
    fun withBill_isVerified() {
        assertEquals(VerificationStatus.VERIFIED, entry(billId = BillId("bill-1")).verification)
    }

    @Test
    fun withAttachedPhoto_isVerified() {
        val entry = ServiceLogEntry.reconstitute(
            id = ServiceLogId("log-1"),
            carId = CarId("car-1"),
            ownerId = OwnerId("owner-1"),
            serviceDate = LocalDate(2026, 1, 1),
            odometerKm = 40_000,
            totalAmountPaise = 0,
            workshopName = null,
            notes = null,
            source = LogSource.MANUAL,
            billId = null,
            billPhotoRef = "owner/car/bill.jpg",
        )
        assertEquals(VerificationStatus.VERIFIED, entry.verification)
    }
}
