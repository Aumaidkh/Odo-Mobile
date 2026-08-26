package com.hopcape.odo.infrastructure.database.diagnostics

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The outbox that has to outlive the tap.
 *
 * The reference code is shown to somebody before any upload has happened, so what matters
 * here is that the row survives, that it is only closed once, and that a delivered request is
 * eventually cleared away rather than kept forever.
 */
class SqlDelightDiagnosticRequestsTest {

    private val now = Instant.parse("2026-08-26T10:00:00Z")

    private fun database(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun store(
        database: OdoDatabase = database(),
        at: Instant = now,
    ) = SqlDelightDiagnosticRequests(database, clock = FixedClock(at))

    @Test
    fun anOpenRequestIsWhatTheNextUploadPassReads() = runTest {
        val requests = store()

        requests.open("ODO-AB12-CD34", now.toEpochMilliseconds())

        assertEquals("ODO-AB12-CD34", requests.oldestOpen())
    }

    @Test
    fun theOldestOpenRequestIsTheOneAnswered() = runTest {
        val requests = store()

        requests.open("ODO-AB12-OLD1", now.minus(2.days).toEpochMilliseconds())
        requests.open("ODO-AB12-NEW1", now.toEpochMilliseconds())

        assertEquals("ODO-AB12-OLD1", requests.oldestOpen())
    }

    @Test
    fun aDeliveredRequestIsNoLongerOffered() = runTest {
        val requests = store()
        requests.open("ODO-AB12-CD34", now.toEpochMilliseconds())

        requests.markDelivered("ODO-AB12-CD34")

        assertNull(requests.oldestOpen())
    }

    @Test
    fun reopeningTheSameCodeCannotReviveADeliveredRequest() = runTest {
        val requests = store()
        requests.open("ODO-AB12-CD34", now.toEpochMilliseconds())
        requests.markDelivered("ODO-AB12-CD34")

        // INSERT OR IGNORE: a retried enqueue is the same request, not a new one.
        requests.open("ODO-AB12-CD34", now.toEpochMilliseconds())

        assertNull(requests.oldestOpen())
    }

    @Test
    fun aFailedAttemptLeavesTheRequestOpen() = runTest {
        val requests = store()
        requests.open("ODO-AB12-CD34", now.toEpochMilliseconds())

        requests.markAttemptFailed("ODO-AB12-CD34", "files left for retry")

        assertEquals("ODO-AB12-CD34", requests.oldestOpen())
    }

    @Test
    fun aDeliveredRequestIsPrunedOnceItIsOldEnough() = runTest {
        val database = database()
        val requests = store(database)

        requests.open("ODO-AB12-OLD1", now.minus(200.days).toEpochMilliseconds())
        requests.open("ODO-AB12-NEW1", now.toEpochMilliseconds())
        requests.markDelivered("ODO-AB12-OLD1")
        requests.markDelivered("ODO-AB12-NEW1")

        // Support may ask about a code weeks later, so a delivered request is kept — but not
        // forever, or the outbox grows for the life of the install.
        assertEquals(1L, database.diagnosticRequestQueries.countRequests().executeAsOne())
    }
}

private class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}
