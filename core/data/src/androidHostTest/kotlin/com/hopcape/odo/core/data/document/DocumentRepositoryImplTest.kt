package com.hopcape.odo.core.data.document

import com.hopcape.odo.core.data.sync.noopBlobUploader
import com.hopcape.odo.core.data.sync.silentSyncTelemetry
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class DocumentRepositoryImplTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    /* ------------------------- fixtures ------------------------- */

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit
        override fun flush() = Unit
    }

    private class FakeSpan(
        override val spanId: String,
        override val traceId: String,
        override val parentSpanId: String?,
        override val name: String,
    ) : Span {
        override fun setAttribute(key: String, value: Any?): Span = this
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            FakeSpan("span", traceId, parentSpanId, name)
        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private class RecordingCrash : CrashRecorder {
        val nonFatals = mutableListOf<Throwable>()
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {
            nonFatals += throwable
        }
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    /** Records what the repository asked the scheduler for. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun repo(
        db: OdoDatabase,
        crash: CrashRecorder = RecordingCrash(),
        now: String = "2026-07-30T10:00:00Z",
        scheduler: SyncScheduler = RecordingScheduler(),
    ) = DocumentRepositoryImpl(
        syncTelemetry = silentSyncTelemetry(),
        blobs = noopBlobUploader(),
        carId = { null },
        database = db,
        telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = crash),
        scheduler = scheduler,
        remote = FakeDocumentRemoteDataSource(),
        clock = FixedClock(Instant.parse(now)),
        dispatcher = Dispatchers.Unconfined,
    )

    private fun document(
        id: String = "doc-1",
        car: CarId = carId,
        owner: OwnerId = ownerId,
        type: DocumentType = DocumentType.INSURANCE,
        source: DocumentSource = DocumentSource.UPLOADED,
        title: String? = "SafeDrive comprehensive",
        storagePath: String = "documents/${car.value}/$id.pdf",
        expiresOn: LocalDate? = LocalDate(2027, 3, 31),
    ) = Document.reconstitute(
        id = DocumentId(id),
        ownerId = owner,
        carId = car,
        type = type,
        storagePath = storagePath,
        source = source,
        title = title,
        issuedOn = LocalDate(2026, 4, 1),
        expiresOn = expiresOn,
        // The date the row carries, not one this fixture chooses: the repository writes
        // `created_at` itself, and every read of this document comes back with that day.
        addedOn = null,
    )

    /* ------------------------- reads & writes ------------------------- */

    @Test
    fun add_thenObserveByCar_returnsTheDocument() = runTest {
        val db = newDb()
        val sut = repo(db)

        sut.add(document())

        val stored = sut.observe(carId).first()
        assertEquals(1, stored.size)
        with(stored.single()) {
            assertEquals("doc-1", id.value)
            assertEquals(DocumentType.INSURANCE, type)
            assertEquals("SafeDrive comprehensive", title?.value)
            assertEquals("documents/car-1/doc-1.pdf", storagePath)
            assertEquals(DocumentSource.UPLOADED, source)
            assertEquals(LocalDate(2026, 4, 1), issuedOn)
            assertEquals(LocalDate(2027, 3, 31), expiresOn)
        }
    }

    @Test
    fun observeById_emitsNullForAnUnknownDocument() = runTest {
        val sut = repo(newDb())

        assertNull(sut.observe(DocumentId("nope")).first())
    }

    @Test
    fun add_leavesTheRowPendingForTheSyncEngine() = runTest {
        val db = newDb()
        repo(db).add(document())

        val row = db.documentQueries.selectById("doc-1").executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertEquals("2026-07-30T10:00:00Z", row.created_at)
        assertEquals("2026-07-30T10:00:00Z", row.updated_at)
        assertNull(row.remote_version)
    }

    @Test
    fun observeByCar_onlySeesThatCarsDocuments() = runTest {
        val db = newDb()
        val sut = repo(db)

        sut.add(document(id = "doc-1"))
        sut.add(document(id = "doc-2", car = CarId("car-2")))

        assertEquals(listOf("doc-1"), sut.observe(carId).first().map { it.id.value })
    }

    /* ------------------------- update ------------------------- */

    @Test
    fun update_rewritesTheEditableFieldsAndKeepsTheRest() = runTest {
        val db = newDb()
        val sut = repo(db)
        sut.add(document())

        val edited = repo(db, now = "2026-08-05T08:00:00Z").update(
            document(type = DocumentType.PUC, title = "Renewed PUC", expiresOn = LocalDate(2027, 9, 30)),
        )

        assertTrue(edited.isRight())
        val row = db.documentQueries.selectById("doc-1").executeAsOne()
        assertEquals(DocumentType.PUC.name, row.doc_type)
        assertEquals("Renewed PUC", row.title)
        assertEquals("2027-09-30", row.expiry_date)
        // Ownership and creation time belong to the first write, not to an edit.
        assertEquals(carId.value, row.car_id)
        assertEquals(ownerId.value, row.owner_id)
        assertEquals("2026-07-30T10:00:00Z", row.created_at)
        assertEquals("2026-08-05T08:00:00Z", row.updated_at)
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
    }

    @Test
    fun update_swapsTheFileAndItsSourceTogether() = runTest {
        val db = newDb()
        val sut = repo(db)
        sut.add(document(source = DocumentSource.DIGILOCKER))

        // "Replace file": a phone photo over a DigiLocker copy has to lose the badge.
        sut.update(document(source = DocumentSource.SCANNED, storagePath = "documents/car-1/doc-1.jpg"))

        val stored = assertNotNull(sut.observe(DocumentId("doc-1")).first())
        assertEquals("documents/car-1/doc-1.jpg", stored.storagePath)
        assertEquals(DocumentSource.SCANNED, stored.source)
        assertEquals(false, stored.source.isVerified)
    }

    @Test
    fun update_reportsNotFoundForAnUnknownDocument() = runTest {
        val result = repo(newDb()).update(document(id = "ghost"))

        assertIs<DomainError.DocumentNotFound>(result.leftOrNull())
    }

    @Test
    fun update_reportsNotFoundForADeletedDocument() = runTest {
        val db = newDb()
        val sut = repo(db)
        sut.add(document())
        sut.softDelete(DocumentId("doc-1"))

        assertIs<DomainError.DocumentNotFound>(sut.update(document()).leftOrNull())
    }

    /* ------------------------- soft delete ------------------------- */

    @Test
    fun softDelete_hidesTheDocumentButKeepsTheTombstone() = runTest {
        val db = newDb()
        val sut = repo(db)
        sut.add(document())

        assertTrue(sut.softDelete(DocumentId("doc-1")).isRight())

        assertEquals(emptyList(), sut.observe(carId).first())
        assertNull(sut.observe(DocumentId("doc-1")).first())
        // The row survives so the deletion itself can reach the server.
        val row = db.documentQueries.selectByCar(carId.value).executeAsList()
        assertEquals(0, row.size)
        val tombstone = db.documentQueries.selectLiveId("doc-1").executeAsOneOrNull()
        assertNull(tombstone)
    }

    /* ------------------------- the free-tier count ------------------------- */

    @Test
    fun countForOwner_countsLiveDocumentsAcrossEveryCar() = runTest {
        val db = newDb()
        val sut = repo(db)
        sut.add(document(id = "doc-1"))
        sut.add(document(id = "doc-2", car = CarId("car-2")))
        sut.add(document(id = "doc-3", owner = OwnerId("owner-2")))
        sut.add(document(id = "doc-4"))
        sut.softDelete(DocumentId("doc-4"))

        // Two live documents for this owner: a second car does not multiply the allowance,
        // another owner's document is not theirs, and a deleted one no longer counts.
        assertEquals(2, sut.countForOwner(ownerId))
    }

    /* ------------------------- failures stay visible ------------------------- */

    @Test
    fun observeByCar_survivesACorruptRowAndReportsIt() = runTest {
        val db = newDb()
        val crash = RecordingCrash()
        // A row no domain write could produce: storage_path is required, and reading it
        // back throws. The stream must not take the vault screen down with it.
        db.documentQueries.insertDocument(
            id = "doc-broken",
            carId = carId.value,
            ownerId = ownerId.value,
            docType = DocumentType.RC.name,
            title = null,
            storagePath = "",
            docSource = DocumentSource.UPLOADED.name,
            issuedDate = null,
            expiryDate = null,
            now = "2026-07-30T10:00:00Z",
            syncStatus = SyncStatus.PENDING.name,
        )

        val emitted = repo(db, crash = crash).observe(carId).first()

        assertEquals(emptyList(), emitted)
        assertEquals(1, crash.nonFatals.size)
    }

    /* ------------------------- sync scheduling ------------------------- */

    @Test
    fun everyWriteAsksForASync() = runTest {
        val db = newDb()
        val scheduler = RecordingScheduler()
        val sut = repo(db, scheduler = scheduler)

        sut.add(document())
        sut.update(document(title = "Renewed"))
        sut.softDelete(DocumentId("doc-1"))

        assertEquals(List(3) { SyncReason.LocalWrite }, scheduler.requested)
    }

    @Test
    fun aFailedUpdateAsksForNothing() = runTest {
        val scheduler = RecordingScheduler()

        repo(newDb(), scheduler = scheduler).update(document(id = "ghost"))

        assertEquals(emptyList(), scheduler.requested)
    }
}
