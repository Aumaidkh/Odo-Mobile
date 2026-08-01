package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
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
    private val carId = CarId("car-1")

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

        val result = useCase(logs)(logId, carId, "content://picked/photo.jpg")

        val entry = assertNotNull(result.getOrNull(), "expected Right but was $result")
        assertEquals(VerificationStatus.VERIFIED, entry.verification)
        // The stored ref is the app's own copy, never the picker's borrowed handle.
        assertEquals("bills/car-1/log-1.jpg", entry.billPhotoRef)
    }

    @Test
    fun theAttachmentIsPersisted() = runTest {
        val logs = FakeServiceLogRepository(listOf(selfReportedEntry()))

        useCase(logs)(logId, carId, "content://picked/photo.jpg")

        assertEquals("bills/car-1/log-1.jpg", logs.entries.value.single().billPhotoRef)
    }

    @Test
    fun attachingDoesNotJudgeThePrice() = runTest {
        // The verdict is RecordEntryFairnessUseCase's job — a benchmark lookup must never
        // stand between the owner and the photo they just took.
        val logs = FakeServiceLogRepository(listOf(selfReportedEntry()))

        val entry = useCase(logs)(logId, carId, "content://picked/photo.jpg").getOrNull()

        assertNull(assertNotNull(entry).fairness)
    }

    @Test
    fun unknownEntry_isServiceLogNotFound() = runTest {
        val logs = FakeServiceLogRepository(emptyList())

        val result = useCase(logs)(ServiceLogId("missing"), carId, "content://picked/photo.jpg")

        assertTrue(result.isLeft())
        assertEquals(DomainError.ServiceLogNotFound, result.leftOrNull())
    }

    @Test
    fun aFileThatCannotBeCopied_leavesTheEntryUntouched() = runTest {
        // The picker's handle can lapse before the copy runs. Storing a key for bytes that
        // were never written would be an entry pointing at nothing.
        val logs = FakeServiceLogRepository(listOf(selfReportedEntry()))

        val result = AttachBillPhotoUseCase(logs, RefusingFileStore)(logId, carId, "content://gone")

        assertTrue(result.isLeft())
        assertNull(logs.entries.value.single().billPhotoRef)
        assertEquals(VerificationStatus.SELF_REPORTED, logs.entries.value.single().verification)
    }

    private fun useCase(logs: FakeServiceLogRepository) = AttachBillPhotoUseCase(logs, CopyingFileStore)

    /** Copies nothing, but answers with the key the real store would have written to. */
    private object CopyingFileStore : PlatformFileStore {
        override suspend fun save(pickedRef: String, directory: String, fileName: String) =
            "$directory/$fileName.jpg".right()

        override suspend fun delete(storageKey: String) = Unit
        override suspend fun exists(storageKey: String) = true
    }

    private object RefusingFileStore : PlatformFileStore {
        override suspend fun save(pickedRef: String, directory: String, fileName: String) =
            DomainError.PersistenceFailure("no bytes").left()

        override suspend fun delete(storageKey: String) = Unit
        override suspend fun exists(storageKey: String) = false
    }
}
