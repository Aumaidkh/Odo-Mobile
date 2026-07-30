package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachBillPhotoUseCaseTest {

    private val logId = ServiceLogId("log-1")

    private fun selfReportedEntry() = ServiceLogEntry.reconstitute(
        id = logId,
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = LocalDate(2026, 6, 1),
        odometerKm = 40_000,
        totalAmountPaise = 330_000,
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = null,
    )

    @Test
    fun attachingABill_verifiesTheEntry() = runTest {
        val logs = FakeServiceLogRepository(listOf(selfReportedEntry()))

        val result = AttachBillPhotoUseCase(logs)(logId, "bill-photos/owner/car/log-1.jpg")

        val entry = assertNotNull(result.getOrNull(), "expected Right but was $result")
        assertEquals(VerificationStatus.VERIFIED, entry.verification)
        assertEquals("bill-photos/owner/car/log-1.jpg", entry.billPhotoRef)
    }

    @Test
    fun theAttachmentIsPersisted() = runTest {
        val logs = FakeServiceLogRepository(listOf(selfReportedEntry()))

        AttachBillPhotoUseCase(logs)(logId, "bill.jpg")

        assertEquals("bill.jpg", logs.entries.value.single().billPhotoRef)
    }

    @Test
    fun attachingDoesNotJudgeThePrice() = runTest {
        // The verdict is RecordEntryFairnessUseCase's job — a benchmark lookup must never
        // stand between the owner and the photo they just took.
        val logs = FakeServiceLogRepository(listOf(selfReportedEntry()))

        val entry = AttachBillPhotoUseCase(logs)(logId, "bill.jpg").getOrNull()

        assertNull(assertNotNull(entry).fairness)
    }

    @Test
    fun unknownEntry_isServiceLogNotFound() = runTest {
        val logs = FakeServiceLogRepository(emptyList())

        val result = AttachBillPhotoUseCase(logs)(ServiceLogId("missing"), "bill.jpg")

        assertTrue(result.isLeft())
        assertEquals(DomainError.ServiceLogNotFound, result.leftOrNull())
    }
}
