package com.hopcape.odo.feature.billscanner.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.entitlement.DocumentLimit
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The confirm step's write, which both ways of adding a document now go through: a paper
 * photographed in the app, and a file the owner uploaded from the vault.
 */
class SaveScannedDocumentUseCaseTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")
    private val documents = FakeDocuments()
    private val reminders = CountingReminderScheduler()

    private fun useCase(limit: DocumentLimit = DocumentLimit.UpTo(3)) = SaveScannedDocumentUseCase(
        documents = documents,
        reminders = reminders,
        allowance = DocumentAllowance { limit },
        ids = { "doc-1" },
        clock = StoppedClock(Instant.parse("2026-08-04T09:00:00Z")),
        timeZone = TimeZone.UTC,
    )

    private fun command(origin: CaptureOrigin) = SaveScannedDocumentCommand(
        type = DocumentType.INSURANCE,
        photoStorageKey = "documents/car-1/doc-1.pdf",
        origin = origin,
        expiresOn = LocalDate(2027, 3, 14),
    )

    @Test
    fun aPhotographedPaperIsFiledAsScanned() = runTest {
        val saved = useCase()(command(CaptureOrigin.Scanned), carId, ownerId).getOrNull()!!

        assertEquals(DocumentSource.SCANNED, saved.source)
        assertEquals(LocalDate(2027, 3, 14), saved.expiresOn)
    }

    /** An uploaded file is the owner's own copy too, so it is recorded as one — never verified. */
    @Test
    fun anUploadedFileIsFiledAsUploadedAndIsNotVerified() = runTest {
        val saved = useCase()(command(CaptureOrigin.Uploaded), carId, ownerId).getOrNull()!!

        assertEquals(DocumentSource.UPLOADED, saved.source)
        assertTrue(!saved.source.isVerified)
    }

    /** The point of confirming the dates: the expiry becomes notifications on the device. */
    @Test
    fun aFiledDocumentRebuildsTheReminderSchedule() = runTest {
        useCase()(command(CaptureOrigin.Uploaded), carId, ownerId)

        assertEquals(1, reminders.refreshes)
    }

    @Test
    fun aFullPlanRefusesAndSchedulesNothing() = runTest {
        repeat(3) { documents.add(document("held-$it")) }

        val error = useCase(DocumentLimit.UpTo(3))(command(CaptureOrigin.Scanned), carId, ownerId)
            .leftOrNull()!!
            .head

        assertIs<DomainError.DocumentLimitReached>(error)
        assertEquals(0, reminders.refreshes)
    }

    private fun document(id: String): Document = Document.create(
        id = DocumentId(id),
        ownerId = ownerId,
        carId = carId,
        type = DocumentType.OTHER,
        storagePath = "documents/car-1/$id.pdf",
        source = DocumentSource.UPLOADED,
        today = LocalDate(2026, 8, 4),
    ).getOrNull()!!
}

private class FakeDocuments : DocumentRepository {
    private val rows = MutableStateFlow<List<Document>>(emptyList())

    override fun observe(carId: CarId): Flow<List<Document>> = rows

    override fun observe(id: DocumentId): Flow<Document?> = rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun add(document: Document): Either<DomainError, Document> {
        rows.value = rows.value + document
        return document.right()
    }

    override suspend fun update(document: Document): Either<DomainError, Document> = document.right()

    override suspend fun softDelete(id: DocumentId): Either<DomainError, Unit> = Unit.right()

    override suspend fun countForOwner(ownerId: OwnerId): Int = rows.value.size
}

private class CountingReminderScheduler : DocumentReminderScheduler {
    var refreshes: Int = 0
        private set

    override suspend fun refresh() {
        refreshes++
    }
}

/** Its own clock: the neighbouring test file's is file-private. */
private class StoppedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private fun <A, B> Either<A, B>.leftOrNull(): A? = fold({ it }, { null })
