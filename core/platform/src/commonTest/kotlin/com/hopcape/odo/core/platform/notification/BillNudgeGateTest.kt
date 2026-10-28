package com.hopcape.odo.core.platform.notification

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BillNudgeGateTest {

    private val car = CarId("car-1")

    @Test
    fun noEntries_posts() = runTest {
        assertEquals(BillNudgeCheck.POST, gate(entries = emptyList()).check())
    }

    @Test
    fun aDeclaredServiceWithoutABill_stillPosts() = runTest {
        assertEquals(BillNudgeCheck.POST, gate(entries = listOf(entry(source = LogSource.DECLARED))).check())
    }

    @Test
    fun aScannedBill_skips() = runTest {
        val scanned = entry(source = LogSource.SCANNED, billPhotoRef = "photo-1")
        assertEquals(BillNudgeCheck.HAS_BILL, gate(entries = listOf(scanned)).check())
    }

    @Test
    fun alreadySent_skipsWithoutReadingTheLog() = runTest {
        val store = MemoryStore().apply { markSent(EngagementNudge.FIRST_SCORE) }
        assertEquals(BillNudgeCheck.ALREADY_SENT, gate(entries = emptyList(), store = store).check())
    }

    @Test
    fun noActiveCar_posts() = runTest {
        assertEquals(BillNudgeCheck.POST, gate(entries = emptyList(), carId = null).check())
    }

    private fun gate(
        entries: List<ServiceLogEntry>,
        store: NudgeSentStore = MemoryStore(),
        carId: CarId? = car,
    ) = BillNudgeGate(
        serviceLogs = Logs(entries),
        activeCar = object : ActiveCarProvider {
            override val activeCarId: StateFlow<CarId?> = MutableStateFlow(carId)
        },
        sent = store,
        carTimeoutMillis = 10,
    )

    private fun entry(source: LogSource, billPhotoRef: String? = null, billId: BillId? = null) =
        ServiceLogEntry.create(
            id = ServiceLogId("log-1"),
            carId = car,
            ownerId = OwnerId("owner-1"),
            serviceDate = LocalDate(2026, 10, 1),
            odometerKm = 10_000,
            totalAmountPaise = 250_000,
            today = LocalDate(2026, 10, 28),
            source = source,
            billId = billId,
            billPhotoRef = billPhotoRef,
        ).getOrElse { error("invalid fixture: $it") }

    private class MemoryStore : NudgeSentStore {
        private val sent = mutableSetOf<EngagementNudge>()
        override suspend fun isSent(nudge: EngagementNudge) = nudge in sent
        override suspend fun markSent(nudge: EngagementNudge) {
            sent += nudge
        }
    }

    private class Logs(private val entries: List<ServiceLogEntry>) : ServiceLogRepository {
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(entries)
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = emptyList()
        override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = flowOf(emptyList())
    }
}
