package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import com.hopcape.odo.infrastructure.database.sync.silentDataTelemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnlistedVehicleReporterImplTest {

    private val telemetry = silentDataTelemetry()

    @Test
    fun report_insertsAPendingRowUnderTheCurrentOwner_andRequestsASync() = runTest {
        val (db, _) = inMemoryDatabase()
        val scheduler = RecordingScheduler()
        val reporter = UnlistedVehicleReporterImpl(
            database = db,
            owner = { OwnerId("owner-1") },
            idGenerator = IdGenerator { "submission-1" },
            scheduler = scheduler,
            telemetry = telemetry,
        )

        reporter.report(make = "Rare Motors", model = "Concept One", variant = "Turbo")

        val stored = db.vehicleCatalogSubmissionQueries.selectPending().executeAsOne()
        assertEquals("owner-1", stored.owner_id)
        assertEquals("Rare Motors", stored.make)
        assertEquals("Concept One", stored.model)
        assertEquals("Turbo", stored.variant)
        assertEquals("PENDING", stored.sync_status)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    /**
     * The whole point of routing this through the local outbox: a report filed before
     * sign-in used to be dropped outright (RLS refuses an unauthenticated insert, and there
     * was nowhere local to hold it). Now it is stored like any other pre-auth write, under
     * the placeholder owner, for sign-in adoption to pick up later (SYNC_DESIGN §9).
     */
    @Test
    fun report_isStoredEvenBeforeAnyoneHasSignedIn() = runTest {
        val (db, _) = inMemoryDatabase()
        val reporter = UnlistedVehicleReporterImpl(
            database = db,
            owner = { OwnerId.LOCAL_PLACEHOLDER },
            idGenerator = IdGenerator { "submission-2" },
            scheduler = RecordingScheduler(),
            telemetry = telemetry,
        )

        reporter.report(make = "Rare Motors", model = "Concept One", variant = null)

        val stored = db.vehicleCatalogSubmissionQueries.selectPending().executeAsOne()
        assertEquals(OwnerId.LOCAL_PLACEHOLDER.value, stored.owner_id)
        assertNull(stored.variant)
    }

    @Test
    fun report_neverThrowsWhenSchedulingFails() = runTest {
        val (db, _) = inMemoryDatabase()
        val reporter = UnlistedVehicleReporterImpl(
            database = db,
            owner = { OwnerId("owner-1") },
            idGenerator = IdGenerator { "submission-3" },
            scheduler = ThrowingScheduler,
            telemetry = telemetry,
        )

        // The whole contract: saving the owner's car must never depend on this succeeding.
        reporter.report(make = "Rare Motors", model = "Concept One", variant = null)

        // The local write still landed — only the schedule call failed.
        assertTrue(db.vehicleCatalogSubmissionQueries.selectPending().executeAsList().isNotEmpty())
    }

    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) {
            requested += reason
        }
    }

    private object ThrowingScheduler : SyncScheduler {
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason): Nothing = error("scheduling failed")
    }
}
