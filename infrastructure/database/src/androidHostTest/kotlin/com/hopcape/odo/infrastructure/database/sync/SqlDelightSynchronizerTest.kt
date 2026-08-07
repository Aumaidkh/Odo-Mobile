package com.hopcape.odo.infrastructure.database.sync

import com.hopcape.odo.core.data.sync.SyncRejection
import com.hopcape.odo.core.sync.SyncEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The engine's bookkeeping. What matters is that a first run reads as "never synced"
 * without writing anything, and that a saved cursor survives exactly as saved.
 */
class SqlDelightSynchronizerTest {

    private val pulledAt = Instant.parse("2026-08-01T10:00:00Z")
    private val pushedAt = Instant.parse("2026-08-01T10:05:00Z")

    @Test
    fun anEntityThatNeverSyncedReadsAsEmptyAndWritesNothing() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        val cursor = synchronizer.cursor(SyncEntity.CARS)

        assertEquals(SyncEntity.CARS, cursor.entity)
        assertNull(cursor.lastPulledAt)
        assertNull(cursor.lastPushedAt)
        assertNull(cursor.lastError)
        // A read must not create a row. Otherwise every first run leaves state behind for an
        // entity that has not actually synced.
        assertEquals(0, db.syncStateQueries.selectAll().executeAsList().size)
    }

    @Test
    fun aSavedCursorComesBackUnchanged() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        synchronizer.updateCursor(SyncEntity.SERVICE_LOGS) {
            copy(lastPulledAt = pulledAt, lastPushedAt = pushedAt)
        }

        val cursor = synchronizer.cursor(SyncEntity.SERVICE_LOGS)
        assertEquals(pulledAt, cursor.lastPulledAt)
        assertEquals(pushedAt, cursor.lastPushedAt)
    }

    @Test
    fun updatingIsReadModifyWrite_soOneFieldDoesNotEraseAnother() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        synchronizer.updateCursor(SyncEntity.CARS) { copy(lastPushedAt = pushedAt) }
        // A pull writes only its own field. If this were a blind insert the push mark would
        // vanish, and the debug row would say the outbox had never run.
        synchronizer.updateCursor(SyncEntity.CARS) { copy(lastPulledAt = pulledAt) }

        val cursor = synchronizer.cursor(SyncEntity.CARS)
        assertEquals(pushedAt, cursor.lastPushedAt)
        assertEquals(pulledAt, cursor.lastPulledAt)
    }

    @Test
    fun entitiesKeepSeparateCursors() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        synchronizer.updateCursor(SyncEntity.CARS) { copy(lastPulledAt = pulledAt) }
        synchronizer.updateCursor(SyncEntity.DOCUMENTS) { copy(lastPulledAt = pushedAt) }

        assertEquals(pulledAt, synchronizer.cursor(SyncEntity.CARS).lastPulledAt)
        assertEquals(pushedAt, synchronizer.cursor(SyncEntity.DOCUMENTS).lastPulledAt)
    }

    /** A refusal from a remote data source, without dragging in a vendor client. */
    private class TestRejection(
        override val status: Int,
        override val isPermanent: Boolean,
        message: String,
    ) : RuntimeException(message), SyncRejection

    private fun rejection(status: Int, permanent: Boolean, message: String = "refused") =
        TestRejection(status, permanent, message)

    @Test
    fun aFailureIsRecordedByTypeName_neverByMessage() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        synchronizer.recordFailure(
            SyncEntity.DOCUMENTS,
            IllegalStateException("row violates policy for MH01AB1234"),
        )

        val cursor = synchronizer.cursor(SyncEntity.DOCUMENTS)
        assertEquals("IllegalStateException", cursor.lastError)
    }

    @Test
    fun aPermanentRefusalIsRecordedAsPermanent() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        // A duplicate key. Sending the same rows again gets the same answer forever, while
        // the scheduler's backoff quietly walks the retries out to hours — so unless this is
        // written down, an entity that can never sync reads as "still working on it".
        synchronizer.recordFailure(SyncEntity.CARS, rejection(status = 409, permanent = true))

        assertEquals("TestRejection(409, permanent)", synchronizer.cursor(SyncEntity.CARS).lastError)
    }

    @Test
    fun aTransientRefusalIsRecordedWithoutIt() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        // The server was unwell. Retrying is exactly the right answer, so nothing should
        // suggest otherwise.
        synchronizer.recordFailure(SyncEntity.CARS, rejection(status = 503, permanent = false))

        assertEquals("TestRejection(503)", synchronizer.cursor(SyncEntity.CARS).lastError)
    }

    @Test
    fun aRejectionsMessageIsStillNeverRecorded() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        synchronizer.recordFailure(
            SyncEntity.CARS,
            rejection(status = 409, permanent = true, message = "duplicate plate MH01AB1234"),
        )

        // The status is a fact about the request; the message quotes the row. Only one of
        // those may be stored.
        val recorded = synchronizer.cursor(SyncEntity.CARS).lastError
        assertEquals("TestRejection(409, permanent)", recorded)
    }

    @Test
    fun aFailureDoesNotRewindTheCursor() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        synchronizer.updateCursor(SyncEntity.CARS) { copy(lastPulledAt = pulledAt) }
        synchronizer.recordFailure(SyncEntity.CARS, RuntimeException("boom"))

        // Losing the high-water mark on failure would turn every failed run into a full
        // re-pull of everything.
        assertEquals(pulledAt, synchronizer.cursor(SyncEntity.CARS).lastPulledAt)
    }

    @Test
    fun aSucceedingRunClearsTheStaleError() = runTest {
        val (db, _) = inMemoryDatabase()
        val synchronizer = SqlDelightSynchronizer(db, silentDataTelemetry())

        synchronizer.recordFailure(SyncEntity.CARS, RuntimeException("boom"))
        synchronizer.updateCursor(SyncEntity.CARS) { copy(lastPulledAt = pulledAt, lastError = null) }

        // The debug row must not keep showing yesterday's failure after today's success.
        assertNull(synchronizer.cursor(SyncEntity.CARS).lastError)
    }

    @Test
    fun anUnparseableStoredTimestampReadsAsNeverSynced() = runTest {
        val (db, _) = inMemoryDatabase()
        db.syncStateQueries.transaction {
            db.syncStateQueries.insertIgnore(SyncEntity.CARS.name)
            db.syncStateQueries.update(
                lastPulledAt = "not-a-timestamp",
                lastPushedAt = null,
                lastError = null,
                entity = SyncEntity.CARS.name,
            )
        }

        // Degrades to a full re-pull rather than failing the run. Applying a pulled row is
        // idempotent, so re-pulling costs bandwidth and nothing else.
        assertNull(SqlDelightSynchronizer(db, silentDataTelemetry()).cursor(SyncEntity.CARS).lastPulledAt)
    }
}
