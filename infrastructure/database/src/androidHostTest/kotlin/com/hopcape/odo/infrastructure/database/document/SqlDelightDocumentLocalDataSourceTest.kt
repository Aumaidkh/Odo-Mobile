package com.hopcape.odo.infrastructure.database.document

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQL behaviour for [SqlDelightDocumentLocalDataSource]. Error mapping and sync scheduling
 * live in [DocumentRepositoryImplTest] instead, against a fake port.
 */
class SqlDelightDocumentLocalDataSourceTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun local(db: OdoDatabase, now: String = "2026-07-30T10:00:00Z") =
        SqlDelightDocumentLocalDataSource(
            database = db,
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
        // The date the row carries, not one this fixture chooses: the local data source
        // writes `created_at` itself, and every read of this document comes back with
        // that day.
        addedOn = null,
    )

    /* ------------------------- reads & writes ------------------------- */

    @Test
    fun insert_thenObserveByCar_returnsTheDocument() = runTest {
        val db = newDb()
        local(db).insert(document())

        val stored = local(db).observeByCar(carId).first()
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
        assertNull(local(newDb()).observeById(DocumentId("nope")).first())
    }

    @Test
    fun insert_leavesTheRowPendingForTheSyncEngine() = runTest {
        val db = newDb()
        local(db).insert(document())

        val row = db.documentQueries.selectById("doc-1").executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertEquals("2026-07-30T10:00:00Z", row.created_at)
        assertEquals("2026-07-30T10:00:00Z", row.updated_at)
        assertNull(row.remote_version)
    }

    @Test
    fun observeByCar_onlySeesThatCarsDocuments() = runTest {
        val db = newDb()
        val local = local(db)

        local.insert(document(id = "doc-1"))
        local.insert(document(id = "doc-2", car = CarId("car-2")))

        assertEquals(listOf("doc-1"), local.observeByCar(carId).first().map { it.id.value })
    }

    /* ------------------------- update ------------------------- */

    @Test
    fun update_rewritesTheEditableFieldsAndKeepsTheRest() = runTest {
        val db = newDb()
        local(db).insert(document())

        val edited = local(db, now = "2026-08-05T08:00:00Z").update(
            document(type = DocumentType.PUC, title = "Renewed PUC", expiresOn = LocalDate(2027, 9, 30)),
        )

        assertTrue(edited)
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
        val local = local(db)
        local.insert(document(source = DocumentSource.DIGILOCKER))

        // "Replace file": a phone photo over a DigiLocker copy has to lose the badge.
        local.update(document(source = DocumentSource.SCANNED, storagePath = "documents/car-1/doc-1.jpg"))

        val stored = assertNotNull(local.observeById(DocumentId("doc-1")).first())
        assertEquals("documents/car-1/doc-1.jpg", stored.storagePath)
        assertEquals(DocumentSource.SCANNED, stored.source)
        assertEquals(false, stored.source.isVerified)
    }

    @Test
    fun update_unknownDocument_answersFalse() = runTest {
        assertFalse(local(newDb()).update(document(id = "ghost")))
    }

    @Test
    fun update_deletedDocument_answersFalse() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(document())
        local.softDelete(DocumentId("doc-1"))

        assertFalse(local.update(document()))
    }

    /* ------------------------- soft delete ------------------------- */

    @Test
    fun softDelete_hidesTheDocumentButKeepsTheTombstone() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(document())

        local.softDelete(DocumentId("doc-1"))

        assertEquals(emptyList(), local.observeByCar(carId).first())
        assertNull(local.observeById(DocumentId("doc-1")).first())
        // The row survives so the deletion itself can reach the server.
        val row = db.documentQueries.selectByCar(carId.value).executeAsList()
        assertEquals(0, row.size)
        val tombstone = db.documentQueries.selectLiveId("doc-1").executeAsOneOrNull()
        assertNull(tombstone)
    }

    /* ------------------------- the free-tier count ------------------------- */

    @Test
    fun countLiveForOwner_countsLiveDocumentsAcrossEveryCar() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(document(id = "doc-1"))
        local.insert(document(id = "doc-2", car = CarId("car-2")))
        local.insert(document(id = "doc-3", owner = OwnerId("owner-2")))
        local.insert(document(id = "doc-4"))
        local.softDelete(DocumentId("doc-4"))

        // Two live documents for this owner: a second car does not multiply the allowance,
        // another owner's document is not theirs, and a deleted one no longer counts.
        assertEquals(2, local.countLiveForOwner(ownerId))
    }

    /* ------------------------- corrupt rows fail fast ------------------------- */

    /**
     * A row no domain write could produce: `storage_path` is required, and
     * [Document.reconstitute] throws rather than silently opening a document with no
     * file. The local data source does not catch — that is the repository's job.
     */
    @Test
    fun observeByCar_corruptRow_throwsFromTheMapper() = runTest {
        val db = newDb()
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

        assertFailsWith<IllegalStateException> { local(db).observeByCar(carId).first() }
    }
}
