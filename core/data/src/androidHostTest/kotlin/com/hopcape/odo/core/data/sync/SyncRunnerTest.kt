package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.sync.SyncCursor
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Synchronizer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The push/pull algorithm and the conflict matrix (SYNC_DESIGN §6, §7).
 *
 * Driven through a hand-written [SyncTable] rather than a real repository, so each rule is
 * one short test with nothing else moving. The matrix gets a case per row on purpose: it is
 * the part most likely to rot, and every way of getting it wrong loses somebody's data.
 */
class SyncRunnerTest {

    private val t0 = Instant.parse("2026-08-01T10:00:00Z")
    private val t1 = Instant.parse("2026-08-01T11:00:00Z")

    /* ------------------------------ push ------------------------------ */

    @Test
    fun pushSendsPendingRowsAndMarksThemSynced() = runTest {
        val table = FakeTable(pending = listOf(row("a", t0), row("b", t0)))

        assertTrue(runner(table).run(FakeSynchronizer()))

        assertEquals(listOf("a", "b"), table.pushed.map { it.id })
        assertEquals(mapOf("a" to t0.toString(), "b" to t0.toString()), table.markedSynced)
    }

    @Test
    fun aRowTheServerDidNotStampStaysPending() = runTest {
        // An offline fake echoes the row back without a server timestamp. Marking it SYNCED
        // would make every later run skip exactly the row that never arrived.
        val table = FakeTable(pending = listOf(row("a", updatedAt = null)))

        assertTrue(runner(table).run(FakeSynchronizer()))

        assertTrue(table.markedSynced.isEmpty())
    }

    @Test
    fun aFailedPushLeavesRowsPendingAndStopsTheEntity() = runTest {
        val table = FakeTable(pending = listOf(row("a", t0)), pushThrows = RuntimeException("offline"))
        val synchronizer = FakeSynchronizer()

        assertFalse(runner(table).run(synchronizer))

        assertTrue(table.markedSynced.isEmpty())
        assertEquals(listOf(SyncEntity.CARS), synchronizer.failures)
    }

    @Test
    fun pushingTwiceStoresOneRow() = runTest {
        val table = FakeTable(pending = listOf(row("a", t0)))
        val synchronizer = FakeSynchronizer()

        runner(table).run(synchronizer)
        table.pending = emptyList()          // it is SYNCED now, so the outbox is empty
        runner(table).run(synchronizer)

        assertEquals(1, table.pushed.size)
    }

    @Test
    fun anEmptyOutboxMakesNoPush() = runTest {
        val table = FakeTable()
        assertTrue(runner(table).run(FakeSynchronizer()))
        assertTrue(table.pushed.isEmpty())
    }

    /* --------------------- permanently refused rows --------------------- */

    @Test
    fun aPermanentlyRefusedRowLeavesTheOutboxAndTheRunCarriesOn() = runTest {
        // A duplicate key gets the same answer every time. Leaving it PENDING would fail
        // this entity on every future run, and the engine stops at the first failure — so
        // one bad row would take every table after this one offline with it.
        val table = FakeTable(pending = listOf(row("a", t0)), refuses = setOf("a"))

        assertTrue(runner(table).run(FakeSynchronizer()))

        assertEquals(setOf("a"), table.markedConflict)
        assertTrue(table.markedSynced.isEmpty())
    }

    @Test
    fun aRefusedBatchIsRetriedRowByRowSoOnlyTheBadRowIsMarked() = runTest {
        // PostgREST answers a batch on its first violation, so a rejected batch says nothing
        // about the rows after the bad one.
        val table = FakeTable(pending = listOf(row("a", t0), row("b", t0)), refuses = setOf("a"))

        assertTrue(runner(table).run(FakeSynchronizer()))

        assertEquals(setOf("a"), table.markedConflict)
        assertEquals(mapOf("b" to t0.toString()), table.markedSynced)
    }

    @Test
    fun aRefusalIsLeftOnTheCursorRatherThanClearedByTheSameRun() = runTest {
        val table = FakeTable(pending = listOf(row("a", t0)), refuses = setOf("a"))
        val synchronizer = FakeSynchronizer()

        runner(table).run(synchronizer)

        // Otherwise the debug row reads "last sync fine" for an entity carrying a row the
        // server has permanently refused.
        assertNotNull(synchronizer.cursors[SyncEntity.CARS]?.lastError)
    }

    @Test
    fun aTransientRejectionStillStopsTheEntity() = runTest {
        // A 503 is about the moment, not the payload. Retrying is the right answer, so the
        // row stays in the outbox and the run reports failure.
        val table = FakeTable(pending = listOf(row("a", t0)), refuses = setOf("a"), refusalStatus = 503)
        val synchronizer = FakeSynchronizer()

        assertFalse(runner(table).run(synchronizer))

        assertTrue(table.markedConflict.isEmpty())
        assertEquals(listOf(SyncEntity.CARS), synchronizer.failures)
    }

    /* ------------------------------ pull ------------------------------ */

    @Test
    fun pushHappensBeforePull() = runTest {
        // Pulling first would let a stale server row look newer than a local edit that has
        // not gone up yet, and overwrite the owner's change with their own older data.
        val table = FakeTable(pending = listOf(row("a", t0)), remote = listOf(row("b", t1)))

        runner(table).run(FakeSynchronizer())

        assertEquals(listOf("push", "fetch"), table.calls)
    }

    @Test
    fun theCursorAdvancesToTheNewestRowSeen() = runTest {
        val table = FakeTable(remote = listOf(row("a", t0), row("b", t1)))
        val synchronizer = FakeSynchronizer()

        runner(table).run(synchronizer)

        assertEquals(t1, synchronizer.cursors[SyncEntity.CARS]?.lastPulledAt)
    }

    @Test
    fun theFetchOverlapsTheCursorSoASharedTimestampIsNotMissed() = runTest {
        val table = FakeTable()
        val synchronizer = FakeSynchronizer()
        synchronizer.cursors[SyncEntity.CARS] = SyncCursor(SyncEntity.CARS, lastPulledAt = t1)

        runner(table).run(synchronizer)

        // Five seconds back, not `gt` on the mark: two rows can share an updated_at, and
        // re-reading one is free while missing one is permanent.
        assertEquals(t1.minus(kotlin.time.Duration.parse("5s")), table.fetchedSince)
    }

    @Test
    fun anEmptyPullLeavesTheCursorWhereItWas() = runTest {
        val table = FakeTable()
        val synchronizer = FakeSynchronizer()
        synchronizer.cursors[SyncEntity.CARS] = SyncCursor(SyncEntity.CARS, lastPulledAt = t1)

        runner(table).run(synchronizer)

        assertEquals(t1, synchronizer.cursors[SyncEntity.CARS]?.lastPulledAt)
    }

    @Test
    fun aSucceedingRunClearsAPreviousError() = runTest {
        val synchronizer = FakeSynchronizer()
        synchronizer.cursors[SyncEntity.CARS] = SyncCursor(SyncEntity.CARS, lastError = "IllegalStateException")

        runner(FakeTable()).run(synchronizer)

        assertNull(synchronizer.cursors[SyncEntity.CARS]?.lastError)
    }

    /* ---------------------- the conflict matrix (§7) ---------------------- */

    @Test
    fun conflict_rowNotHeldLocally_isInserted() = runTest {
        val table = FakeTable(remote = listOf(row("a", t0)))
        runner(table).run(FakeSynchronizer())
        assertEquals(listOf("a"), table.applied.map { it.id })
    }

    @Test
    fun conflict_localSynced_remoteWins() = runTest {
        val table = FakeTable(
            remote = listOf(row("a", t1)),
            local = mapOf("a" to LocalRowState(SyncStatus.SYNCED, t0)),
        )
        runner(table).run(FakeSynchronizer())
        assertEquals(listOf("a"), table.applied.map { it.id })
    }

    @Test
    fun conflict_localPendingAndNewer_localWins() = runTest {
        val table = FakeTable(
            remote = listOf(row("a", t0)),
            local = mapOf("a" to LocalRowState(SyncStatus.PENDING, t1)),
        )
        runner(table).run(FakeSynchronizer())
        // The local edit survives and stays PENDING, so the next push overwrites the server.
        assertTrue(table.applied.isEmpty())
    }

    @Test
    fun conflict_localPendingAndOlder_remoteWins() = runTest {
        val table = FakeTable(
            remote = listOf(row("a", t1)),
            local = mapOf("a" to LocalRowState(SyncStatus.PENDING, t0)),
        )
        runner(table).run(FakeSynchronizer())
        assertEquals(listOf("a"), table.applied.map { it.id })
    }

    @Test
    fun conflict_timestampsEqual_localWins() = runTest {
        val table = FakeTable(
            remote = listOf(row("a", t0)),
            local = mapOf("a" to LocalRowState(SyncStatus.PENDING, t0)),
        )
        runner(table).run(FakeSynchronizer())
        // Bias to the device the owner is holding. If the versions are identical the push
        // that follows is a no-op anyway.
        assertTrue(table.applied.isEmpty())
    }

    @Test
    fun conflict_remoteHasNoTimestamp_localEditStands() = runTest {
        val table = FakeTable(
            remote = listOf(row("a", updatedAt = null)),
            local = mapOf("a" to LocalRowState(SyncStatus.PENDING, t0)),
        )
        runner(table).run(FakeSynchronizer())
        assertTrue(table.applied.isEmpty())
    }

    @Test
    fun aTombstoneIsAppliedLikeAnyOtherRow() = runTest {
        // A soft delete made on another device reaches this one as a normal pulled row with
        // deleted_at set. Filtering it out in the sync query would mean deletions never
        // propagate.
        val table = FakeTable(
            remote = listOf(row("a", t1, deleted = true)),
            local = mapOf("a" to LocalRowState(SyncStatus.SYNCED, t0)),
        )
        runner(table).run(FakeSynchronizer())
        assertTrue(table.applied.single().deleted)
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun runner(table: FakeTable) = SyncRunner(
        entity = SyncEntity.CARS,
        table = table,
        database = inMemoryDatabase().first,
        telemetry = silentSyncTelemetry(),
        clock = Clock.System,
    )

    private fun row(id: String, updatedAt: Instant? = null, deleted: Boolean = false) =
        Row(id, updatedAt, deleted)

    private data class Row(val id: String, val updatedAt: Instant?, val deleted: Boolean = false)

    /** A server refusal, permanent or not. Mirrors `SupabaseRequestFailed` without the HTTP. */
    private class TestRejection(
        override val status: Int,
        override val isPermanent: Boolean,
    ) : RuntimeException("refused"), SyncRejection

    private class FakeTable(
        var pending: List<Row> = emptyList(),
        private val remote: List<Row> = emptyList(),
        private val local: Map<String, LocalRowState> = emptyMap(),
        private val pushThrows: Throwable? = null,
        /** Ids the server refuses for good; a batch containing one is rejected whole. */
        private val refuses: Set<String> = emptySet(),
        private val refusalStatus: Int = 409,
    ) : SyncTable<Row> {
        val pushed = mutableListOf<Row>()
        val applied = mutableListOf<Row>()
        val markedSynced = mutableMapOf<String, String>()
        val markedConflict = mutableSetOf<String>()
        val calls = mutableListOf<String>()
        var fetchedSince: Instant? = null

        override fun idOf(dto: Row) = dto.id
        override fun updatedAtOf(dto: Row) = dto.updatedAt
        override suspend fun pending() = pending

        override suspend fun push(rows: List<Row>): List<Row> {
            calls += "push"
            pushThrows?.let { throw it }
            if (rows.any { it.id in refuses }) throw TestRejection(refusalStatus, refusalStatus == 409)
            pushed += rows
            return rows
        }

        override fun markSynced(id: String, remoteVersion: String) {
            markedSynced[id] = remoteVersion
        }

        override fun markConflict(id: String) {
            markedConflict += id
        }

        override suspend fun fetch(since: Instant?): List<Row> {
            calls += "fetch"
            fetchedSince = since
            return remote
        }

        override fun localState(id: String) = local[id]

        override fun applyRemote(dto: Row) {
            applied += dto
        }
    }

    private class FakeSynchronizer : Synchronizer {
        val cursors = mutableMapOf<SyncEntity, SyncCursor>()
        val failures = mutableListOf<SyncEntity>()

        override suspend fun cursor(entity: SyncEntity) = cursors.getOrPut(entity) { SyncCursor(entity) }

        override suspend fun updateCursor(entity: SyncEntity, update: SyncCursor.() -> SyncCursor) {
            cursors[entity] = cursor(entity).update()
        }

        override suspend fun recordFailure(entity: SyncEntity, cause: Throwable) {
            failures += entity
        }
    }

}
