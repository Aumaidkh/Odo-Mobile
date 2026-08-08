package com.hopcape.odo.infrastructure.database.trip

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.model.SessionSnapshot
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** SQL behaviour for [TripSessionStoreImpl] — the single-row crash-resume journal. */
class TripSessionStoreImplTest {

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private val snapshot = SessionSnapshot(
        phase = "TRACKING",
        carId = "car-1",
        mode = "BT_VERIFIED",
        startedAt = Instant.parse("2026-08-07T08:00:00Z"),
        distanceMeters = 1_500,
        estimatedMeters = 0,
        lastFix = LocationSample(
            at = Instant.parse("2026-08-07T08:10:00Z"),
            elapsed = 600.seconds,
            lat = 19.0760,
            lon = 72.8777,
            accuracyM = 10f,
            speedMps = 12f,
        ),
        stitchDeadline = null,
    )

    @Test
    fun load_withNoSavedSession_isNull() = runTest {
        assertNull(TripSessionStoreImpl(newDb()).load())
    }

    @Test
    fun savedSession_roundTrips() = runTest {
        val store = TripSessionStoreImpl(newDb())
        store.save(snapshot)

        val loaded = store.load()

        assertEquals(snapshot.phase, loaded?.phase)
        assertEquals(snapshot.carId, loaded?.carId)
        assertEquals(snapshot.mode, loaded?.mode)
        assertEquals(snapshot.startedAt, loaded?.startedAt)
        assertEquals(snapshot.distanceMeters, loaded?.distanceMeters)
        assertEquals(snapshot.lastFix?.lat, loaded?.lastFix?.lat)
        assertEquals(snapshot.lastFix?.lon, loaded?.lastFix?.lon)
        // Monotonic elapsed does not survive a process restart — reconstructed as zero.
        assertEquals(kotlin.time.Duration.ZERO, loaded?.lastFix?.elapsed)
    }

    @Test
    fun savingTwice_updatesTheSameSingletonRow() = runTest {
        val store = TripSessionStoreImpl(newDb())
        store.save(snapshot)
        store.save(snapshot.copy(distanceMeters = 2_000, phase = "SOFT_PAUSED"))

        val loaded = store.load()

        assertEquals(2_000L, loaded?.distanceMeters)
        assertEquals("SOFT_PAUSED", loaded?.phase)
    }

    @Test
    fun clear_removesTheSession() = runTest {
        val store = TripSessionStoreImpl(newDb())
        store.save(snapshot)

        store.clear()

        assertNull(store.load())
    }
}
