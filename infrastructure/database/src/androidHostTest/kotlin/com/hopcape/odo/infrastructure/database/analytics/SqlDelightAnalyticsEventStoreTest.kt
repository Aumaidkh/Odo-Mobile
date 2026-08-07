package com.hopcape.odo.infrastructure.database.analytics

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.analytics.api.StoredAnalyticsContext
import com.hopcape.analytics.api.StoredAnalyticsEvent
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.silentDataTelemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** SQL behaviour for [SqlDelightAnalyticsEventStore] — round-trip, ordering, and the two caps. */
class SqlDelightAnalyticsEventStoreTest {

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun store(
        db: OdoDatabase = newDb(),
        rowCap: Int = 1000,
        // INFINITE, not the real 7-day default: these tests use tiny synthetic timestamps
        // (near epoch), which the real default would always read as older than any
        // Clock.System-relative cutoff and evict on the very next enqueue(). The one test
        // that exercises the age cap overrides this explicitly.
        maxAge: Duration = Duration.INFINITE,
        clock: Clock = Clock.System,
    ) = SqlDelightAnalyticsEventStore(
        database = db,
        telemetry = silentDataTelemetry(),
        clock = clock,
        rowCap = rowCap,
        maxAge = maxAge,
    )

    private fun event(
        name: String,
        id: String = name,
        timestampMs: Long = 0L,
        properties: Map<String, Any?> = mapOf("k" to "v"),
        attemptCount: Int = 0,
    ) = StoredAnalyticsEvent(
        eventId = id,
        name = name,
        properties = properties,
        sequenceNumber = 0L,
        timestampMs = timestampMs,
        context = StoredAnalyticsContext(
            appVersion = "1.0.0",
            platform = "android",
            deviceModel = "Pixel-Test",
            osVersion = "Android 14",
            locale = "en-IN",
            sessionId = "session-1",
            anonymousId = "anon-1",
            userId = "user-1",
        ),
        attemptCount = attemptCount,
    )

    @Test
    fun enqueue_thenPeekBatch_roundTripsEverything() = runTest {
        val sut = store()
        val original = event(
            "bill_scanned",
            timestampMs = 1000L,
            properties = mapOf("odometer" to 45210L, "workshop" to "auto-care", "verified" to true, "note" to null),
        )

        sut.enqueue(original)

        val restored = sut.peekBatch(10).single()
        assertEquals(original.eventId, restored.eventId)
        assertEquals(original.name, restored.name)
        assertEquals(original.properties, restored.properties)
        assertEquals(original.timestampMs, restored.timestampMs)
        assertEquals(original.context, restored.context)
        assertEquals(0, restored.attemptCount)
    }

    @Test
    fun peekBatch_isFifoByTimestamp_notInsertionOrder() = runTest {
        val sut = store()
        // Inserted newest-first; FIFO must still read oldest-first by timestamp_ms.
        sut.enqueue(event("c", timestampMs = 300L))
        sut.enqueue(event("a", timestampMs = 100L))
        sut.enqueue(event("b", timestampMs = 200L))

        assertEquals(listOf("a", "b", "c"), sut.peekBatch(10).map { it.name })
    }

    @Test
    fun peekBatch_isBoundedByMaxSize() = runTest {
        val sut = store()
        repeat(5) { sut.enqueue(event("e$it", id = "id$it", timestampMs = it.toLong())) }

        assertEquals(2, sut.peekBatch(2).size)
    }

    @Test
    fun remove_deletesOnlyMatchingIds() = runTest {
        val sut = store()
        sut.enqueue(event("keep", id = "keep-id"))
        sut.enqueue(event("drop", id = "drop-id"))

        sut.remove(listOf("drop-id"))

        assertEquals(1, sut.size())
        assertEquals("keep", sut.peekBatch(10).single().name)
    }

    @Test
    fun size_reflectsRowCount() = runTest {
        val sut = store()
        assertEquals(0, sut.size())
        sut.enqueue(event("a", id = "a"))
        sut.enqueue(event("b", id = "b"))
        assertEquals(2, sut.size())
    }

    @Test
    fun recordAttempt_updatesTheRow_visibleOnNextPeekBatch() = runTest {
        val sut = store()
        sut.enqueue(event("stubborn", id = "stubborn-id"))

        sut.recordAttempt("stubborn-id", attempt = 2)

        assertEquals(2, sut.peekBatch(10).single().attemptCount)
    }

    @Test
    fun rowCap_evictsOldestBeyondTheLimit() = runTest {
        val sut = store(rowCap = 3)

        repeat(5) { i -> sut.enqueue(event("e$i", id = "id$i", timestampMs = i.toLong())) }

        val remaining = sut.peekBatch(10)
        assertEquals(3, remaining.size)
        // The newest 3 survive (timestamps 2, 3, 4); the oldest 2 (0, 1) were evicted.
        assertEquals(listOf("e2", "e3", "e4"), remaining.map { it.name })
    }

    @Test
    fun ageCap_evictsRowsOlderThanMaxAge() = runTest {
        val referenceNow = Instant.fromEpochMilliseconds(100.days.inWholeMilliseconds)
        val fixedClock = object : Clock {
            override fun now(): Instant = referenceNow
        }
        val sut = store(maxAge = 1.days, clock = fixedClock)

        // 2 days before the store's clock — outside the 1-day cap.
        sut.enqueue(event("old", id = "old-id", timestampMs = (referenceNow - 2.days).toEpochMilliseconds()))

        // A second enqueue is what triggers the sweep — eviction runs on every insert, not
        // on a timer, so "old" is only actually evicted once this one lands.
        sut.enqueue(event("fresh", id = "fresh-id", timestampMs = referenceNow.toEpochMilliseconds()))

        val remaining = sut.peekBatch(10).map { it.name }
        assertTrue("old" !in remaining, "an event older than the cap must not survive the next enqueue's sweep")
        assertTrue("fresh" in remaining)
    }

    @Test
    fun unreadableRow_isSkippedAndRemoved_ratherThanCrashingTheBatch() = runTest {
        val db = newDb()
        val sut = store(db)
        sut.enqueue(event("good", id = "good-id", timestampMs = 1L))
        // Written directly, bypassing the codec, to simulate a row a future format change
        // can no longer decode.
        db.analyticsEventQueries.insertEvent(
            eventId = "bad-id",
            name = "bad",
            propertiesJson = "not json",
            contextJson = "not json",
            sequenceNumber = 0L,
            timestampMs = 2L,
            attemptCount = 0L,
        )

        val batch = sut.peekBatch(10)

        assertEquals(listOf("good"), batch.map { it.name })
        assertEquals(1, sut.size(), "the unreadable row must have been removed, not just skipped")
    }
}
