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
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Orchestration only — error mapping, telemetry, sync scheduling, and the count's
 * fail-safe-to-zero policy. The SQL behaviour these used to exercise through a real
 * database now lives in [SqlDelightDocumentLocalDataSourceTest]; this suite drives
 * [DocumentRepositoryImpl] against a [FakeDocumentLocalDataSource] instead.
 */
class DocumentRepositoryImplTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

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

    /** Records what the repository asked the scheduler for. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    private class FakeDocumentLocalDataSource(
        private val insertThrows: Throwable? = null,
        private val updateResult: Boolean = true,
        private val updateThrows: Throwable? = null,
        private val softDeleteThrows: Throwable? = null,
        private val byCar: Flow<List<Document>> = flowOf(emptyList()),
        private val byId: Flow<Document?> = flowOf(null),
        private val countResult: Int = 0,
        private val countThrows: Throwable? = null,
    ) : DocumentLocalDataSource {
        var inserted: Document? = null
            private set
        var updated: Document? = null
            private set
        var softDeleted: DocumentId? = null
            private set

        override suspend fun insert(document: Document) {
            insertThrows?.let { throw it }
            inserted = document
        }

        override suspend fun update(document: Document): Boolean {
            updateThrows?.let { throw it }
            updated = document
            return updateResult
        }

        override suspend fun softDelete(id: DocumentId) {
            softDeleteThrows?.let { throw it }
            softDeleted = id
        }

        override fun observeByCar(carId: CarId): Flow<List<Document>> = byCar
        override fun observeById(id: DocumentId): Flow<Document?> = byId

        override suspend fun countLiveForOwner(ownerId: OwnerId): Int {
            countThrows?.let { throw it }
            return countResult
        }
    }

    /**
     * A fresh, unexercised sync stack — [DocumentRepositoryImpl] still takes a [SyncRunner]
     * to construct, but nothing in this suite calls `syncWith`, so a throwaway in-memory DB
     * is all it needs.
     */
    private fun repo(
        local: DocumentLocalDataSource,
        crash: CrashRecorder = RecordingCrash(),
        scheduler: SyncScheduler = RecordingScheduler(),
    ): DocumentRepositoryImpl {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        val db = OdoDatabase(driver)
        return DocumentRepositoryImpl(
            local = local,
            telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = crash),
            scheduler = scheduler,
            runner = SyncRunner(
                entity = SyncEntity.DOCUMENTS,
                table = DocumentSyncTable(
                    database = db,
                    remote = FakeDocumentRemoteDataSource(),
                    blobs = noopBlobUploader(),
                    carId = { null },
                ),
                database = db,
                telemetry = silentSyncTelemetry(),
            ),
        )
    }

    private fun document(id: String = "doc-1") = Document.reconstitute(
        id = DocumentId(id),
        ownerId = ownerId,
        carId = carId,
        type = DocumentType.INSURANCE,
        storagePath = "documents/$carId/$id.pdf",
        source = DocumentSource.UPLOADED,
        title = "SafeDrive comprehensive",
        issuedOn = LocalDate(2026, 4, 1),
        expiresOn = LocalDate(2027, 3, 31),
        addedOn = null,
    )

    /* ------------------------- writes ------------------------- */

    @Test
    fun add_success_writesThroughLocalAndAsksForASync() = runTest {
        val local = FakeDocumentLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler = scheduler).add(document())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("doc-1", local.inserted?.id?.value)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun add_localThrows_isPersistenceFailure() = runTest {
        val local = FakeDocumentLocalDataSource(insertThrows = RuntimeException("disk full"))

        val result = repo(local).add(document())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun update_localAnswersFalse_isDocumentNotFound() = runTest {
        val local = FakeDocumentLocalDataSource(updateResult = false)

        val result = repo(local).update(document())

        assertIs<DomainError.DocumentNotFound>(result.leftOrNull())
    }

    @Test
    fun update_localAnswersTrue_asksForASync() = runTest {
        val local = FakeDocumentLocalDataSource(updateResult = true)
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler = scheduler).update(document())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun softDelete_success_asksForASync() = runTest {
        val local = FakeDocumentLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler = scheduler).softDelete(DocumentId("doc-1"))

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(DocumentId("doc-1"), local.softDeleted)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun everyWrite_asksForASync() = runTest {
        val local = FakeDocumentLocalDataSource()
        val scheduler = RecordingScheduler()
        val repo = repo(local, scheduler = scheduler)

        repo.add(document())
        repo.update(document())
        repo.softDelete(DocumentId("doc-1"))

        assertEquals(List(3) { SyncReason.LocalWrite }, scheduler.requested)
    }

    @Test
    fun aFailedUpdate_asksForNothing() = runTest {
        val local = FakeDocumentLocalDataSource(updateResult = false)
        val scheduler = RecordingScheduler()

        repo(local, scheduler = scheduler).update(document())

        assertEquals(emptyList(), scheduler.requested)
    }

    /* ------------------------- the free-tier count ------------------------- */

    @Test
    fun countForOwner_passesThroughTheLocalCount() = runTest {
        val local = FakeDocumentLocalDataSource(countResult = 2)

        assertEquals(2, repo(local).countForOwner(ownerId))
    }

    /**
     * Zero, not "too many": a broken count must not reject a legitimate add with "limit
     * reached", a lie about why it failed. Answering zero lets the add proceed to the
     * write, which fails on the same broken store and says so honestly.
     */
    @Test
    fun countForOwner_localThrows_readsAsZero() = runTest {
        val local = FakeDocumentLocalDataSource(countThrows = RuntimeException("disk error"))

        assertEquals(0, repo(local).countForOwner(ownerId))
    }

    /* ------------------------- observe flows ------------------------- */

    @Test
    fun observe_byCar_passesThroughTheLocalStream() = runTest {
        val expected = listOf(document())
        val local = FakeDocumentLocalDataSource(byCar = flowOf(expected))

        assertEquals(expected, repo(local).observe(carId).first())
    }

    @Test
    fun observe_byCar_localThrows_emitsEmptyListInstead() = runTest {
        val local = FakeDocumentLocalDataSource(byCar = flow { throw RuntimeException("read failed") })
        val crash = RecordingCrash()

        val emitted = repo(local, crash = crash).observe(carId).first()

        assertEquals(emptyList(), emitted)
        assertEquals(1, crash.nonFatals.size, "a swallowed exception must still reach the dashboard")
    }

    @Test
    fun observe_byId_passesThroughTheLocalStream() = runTest {
        val expected = document()
        val local = FakeDocumentLocalDataSource(byId = flowOf(expected))

        assertEquals(expected, repo(local).observe(DocumentId("doc-1")).first())
    }

    @Test
    fun observe_byId_localThrows_emitsNullInstead() = runTest {
        val local = FakeDocumentLocalDataSource(byId = flow { throw RuntimeException("read failed") })

        assertNull(repo(local).observe(DocumentId("doc-1")).first())
    }
}
