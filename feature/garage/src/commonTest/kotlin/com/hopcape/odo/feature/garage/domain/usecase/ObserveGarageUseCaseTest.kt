package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.garage.domain.model.GarageDocument
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ObserveGarageUseCaseTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    /** The day every emission is resolved against. */
    private val today = LocalDate(2026, 7, 28)

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private class FakeCarRepository(car: Car?) : CarRepository {
        val cars = MutableStateFlow(car)
        override suspend fun add(car: Car): Either<DomainError, Car> = car.right()
        override suspend fun update(car: Car): Either<DomainError, Car> = car.right()
        override fun observePrimaryCar(): Flow<Car?> = cars
        override fun observe(id: CarId): Flow<Car?> = cars
        override suspend fun softDelete(id: CarId): Either<DomainError, Unit> = Unit.right()
    }

    private class FakeDocumentRepository(documents: List<Document>) : DocumentRepository {
        val documents = MutableStateFlow(documents)
        override fun observe(carId: CarId): Flow<List<Document>> = documents
        override fun observe(id: DocumentId): Flow<Document?> = flowOf(null)
        override suspend fun add(document: Document): Either<DomainError, Document> = document.right()
        override suspend fun update(document: Document): Either<DomainError, Document> = document.right()
        override suspend fun softDelete(id: DocumentId): Either<DomainError, Unit> = Unit.right()
        override suspend fun countForOwner(ownerId: OwnerId): Int = documents.value.size
    }

    private class FakeServiceLogRepository(entries: List<ServiceLogEntry>) : ServiceLogRepository {
        val entries = MutableStateFlow(entries)
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = entries
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? = emptyList()
        override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = flowOf(emptyList())
    }

    private fun car(odometerKm: Int = 48_500): Car = Car.reconstitute(
        id = carId,
        ownerId = ownerId,
        make = "Maruti Suzuki",
        model = "Swift",
        variant = "VXI",
        year = 2020,
        fuelType = FuelType.PETROL,
        registrationNumber = "MH12AB1234",
        odometerKm = odometerKm,
        purchaseYear = 2020,
        nickname = null,
        isPrimary = true,
        addedOn = LocalDate(2026, 1, 5),
    )

    private fun document(id: String, type: DocumentType, expiresOn: LocalDate?): Document =
        Document.reconstitute(
            id = DocumentId(id),
            ownerId = ownerId,
            carId = carId,
            type = type,
            storagePath = "documents/$id.pdf",
            source = DocumentSource.UPLOADED,
            title = null,
            issuedOn = null,
            expiresOn = expiresOn,
            addedOn = LocalDate(2026, 6, 1),
        )

    private fun entry(
        id: String,
        servicedOn: LocalDate,
        amountPaise: Long = 480_000,
        categories: Set<ServiceCategory> = setOf(ServiceCategory.GENERAL_SERVICE),
        billPhotoRef: String? = null,
        workshopName: String? = "Sharma Motors",
    ): ServiceLogEntry = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = carId,
        ownerId = ownerId,
        serviceDate = servicedOn,
        odometerKm = 44_000,
        totalAmountPaise = amountPaise,
        workshopName = workshopName,
        notes = null,
        source = LogSource.MANUAL,
        billId = null,
        categories = categories,
        billPhotoRef = billPhotoRef,
    )

    private fun useCase(
        cars: CarRepository,
        documents: DocumentRepository,
        logs: ServiceLogRepository,
    ) = ObserveGarageUseCase(
        cars = cars,
        documents = documents,
        logs = logs,
        // Midday UTC on the day under test, read in UTC so the date cannot drift.
        clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
        timeZone = TimeZone.UTC,
    )

    private suspend fun snapshotOf(
        car: Car? = car(),
        documents: List<Document> = emptyList(),
        entries: List<ServiceLogEntry> = emptyList(),
    ): GarageSnapshot = useCase(
        cars = FakeCarRepository(car),
        documents = FakeDocumentRepository(documents),
        logs = FakeServiceLogRepository(entries),
    ).invoke(carId).first()

    @Test
    fun readsTheCarTheDocumentsAndTheHistoryTogether() = runTest {
        val snapshot = snapshotOf(
            documents = listOf(document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3))),
            entries = listOf(entry("s1", LocalDate(2026, 3, 2))),
        )

        assertEquals(carId, snapshot.car?.id)
        assertEquals(1, snapshot.history.size)
        assertEquals(today, snapshot.today)
        // One row per tracked paper, whether or not the car has it.
        assertEquals(GarageDocument.TRACKED.size, snapshot.documents.size)
    }

    @Test
    fun noCar_stillEmitsASnapshot() = runTest {
        val snapshot = snapshotOf(car = null)

        assertNull(snapshot.car)
        assertTrue(snapshot.history.isEmpty())
    }

    @Test
    fun documentRows_resolveAgainstTheSameDay() = runTest {
        val snapshot = snapshotOf(
            documents = listOf(
                document("d1", DocumentType.INSURANCE, LocalDate(2027, 7, 3)),
                // Two weeks out — inside the renewal window.
                document("d2", DocumentType.PUC, LocalDate(2026, 8, 11)),
            ),
        )

        val insurance = snapshot.documents.first { it.type == DocumentType.INSURANCE }
        assertIs<DocumentValidity.Valid>(assertIs<GarageDocument.OnFile>(insurance).validity)

        val puc = snapshot.documents.first { it.type == DocumentType.PUC }
        assertIs<DocumentValidity.ExpiringSoon>(assertIs<GarageDocument.OnFile>(puc).validity)

        // Nothing was uploaded for the RC, so its row offers to add one.
        assertIs<GarageDocument.Missing>(snapshot.documents.first { it.type == DocumentType.RC })
        assertEquals(listOf(puc), snapshot.documentsNeedingAttention)
        assertEquals(1, snapshot.missingDocuments.size)
    }

    @Test
    fun historyEntry_carriesTheFactsARowShows() = runTest {
        val snapshot = snapshotOf(
            entries = listOf(
                entry(
                    "s1",
                    LocalDate(2026, 3, 2),
                    amountPaise = 480_000,
                    categories = setOf(ServiceCategory.BRAKES),
                    billPhotoRef = "bills/s1.jpg",
                ),
            ),
        )

        val row = snapshot.history.single()
        assertEquals(ServiceLogId("s1"), row.id)
        assertEquals(LocalDate(2026, 3, 2), row.servicedOn)
        assertEquals(480_000, row.amount.paise)
        assertEquals(setOf(ServiceCategory.BRAKES), row.categories)
        assertEquals("Sharma Motors", row.workshopName?.value)
        // A bill photo is what earns the Verified badge.
        assertEquals(VerificationStatus.VERIFIED, row.verification)
    }

    @Test
    fun entryWithoutABill_isSelfReported() = runTest {
        val snapshot = snapshotOf(entries = listOf(entry("s1", LocalDate(2026, 3, 2))))

        assertEquals(VerificationStatus.SELF_REPORTED, snapshot.history.single().verification)
        assertEquals(0, snapshot.verifiedCount)
    }

    @Test
    fun filtering_narrowsTheRowsAndTheirTotal() = runTest {
        val snapshot = snapshotOf(
            entries = listOf(
                entry("s1", LocalDate(2026, 3, 2), amountPaise = 480_000, categories = setOf(ServiceCategory.BRAKES)),
                entry("s2", LocalDate(2025, 5, 11), amountPaise = 280_000, categories = setOf(ServiceCategory.TYRES)),
            ),
        )

        assertEquals(2, snapshot.historyMatching(ServiceFacet.ALL).size)
        assertEquals(760_000, snapshot.totalMatching(ServiceFacet.ALL).paise)

        val tyres = snapshot.historyMatching(ServiceFacet.TYRES)
        assertEquals(listOf(ServiceLogId("s2")), tyres.map { it.id })
        assertEquals(280_000, snapshot.totalMatching(ServiceFacet.TYRES).paise)
    }

    @Test
    fun emptyGarage_totalsZero() = runTest {
        val snapshot = snapshotOf()

        assertEquals(0, snapshot.totalMatching(ServiceFacet.ALL).paise)
        assertEquals(0, snapshot.verifiedCount)
    }
}
