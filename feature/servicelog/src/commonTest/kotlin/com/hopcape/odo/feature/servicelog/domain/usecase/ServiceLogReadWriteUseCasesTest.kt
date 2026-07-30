package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The thin read/delete use cases the detail screen drives. */
class ServiceLogReadWriteUseCasesTest {

    private val logId = ServiceLogId("log-1")

    private fun entry(id: ServiceLogId = logId) = ServiceLogEntry.reconstitute(
        id = id,
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = LocalDate(2026, 6, 1),
        odometerKm = 40_000,
        totalAmountPaise = 280_000,
        workshopName = "Sharma Motors",
        notes = null,
        source = LogSource.MANUAL,
        billId = null,
    )

    @Test
    fun getServiceLog_streamsTheEntry() = runTest {
        val logs = FakeServiceLogRepository(listOf(entry()))

        val found = GetServiceLogUseCase(logs)(logId).first()

        assertEquals(logId, found?.id)
    }

    @Test
    fun getServiceLog_emitsNullForAnUnknownId() = runTest {
        val logs = FakeServiceLogRepository(listOf(entry()))

        assertNull(GetServiceLogUseCase(logs)(ServiceLogId("missing")).first())
    }

    @Test
    fun deleteServiceLog_removesItFromTheStream() = runTest {
        val logs = FakeServiceLogRepository(listOf(entry()))

        val result = DeleteServiceLogUseCase(logs)(logId)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(1, logs.deleteCount)
        assertNull(GetServiceLogUseCase(logs)(logId).first())
    }
}
