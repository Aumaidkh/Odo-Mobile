package com.hopcape.odo.infrastructure.database.refuel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.domain.refuel.PendingFill
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The store that makes a missed detection recoverable.
 *
 * Its whole job is to survive the things a notification does not — being dismissed, the
 * process dying, the same payment being read again on reconnect — so those are what this
 * covers.
 */
class SqlDelightPendingFillStoreTest {

    private lateinit var driver: JdbcSqliteDriver

    private fun store(): SqlDelightPendingFillStore {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return SqlDelightPendingFillStore(OdoDatabase(driver))
    }

    @Test
    fun aDetectionSurvivesToBeAskedAbout() = runTest {
        val store = store()

        store.remember(fill())

        val open = store.observeOpen().first()
        assertEquals(1, open.size)
        assertEquals("Bharat Petroleum, Karol Bagh", open.first().merchant)
        assertEquals(200_000L, open.first().amount?.paise)
    }

    @Test
    fun readingTheSameNotificationTwiceIsOneQuestion() = runTest {
        val store = store()

        store.remember(fill())
        store.remember(fill())

        assertEquals(1, store.open().size)
    }

    @Test
    fun rememberingAgainDoesNotOverwriteWhatIsAlreadyThere() = runTest {
        val store = store()
        store.remember(fill(payload = "original"))

        // The listener re-reads the shade on every reconnect. A second read must not clobber
        // a draft the owner may be part-way through answering.
        store.remember(fill(payload = "rewritten"))

        assertEquals("original", store.open().single().draftPayload)
    }

    @Test
    fun ananswerTakesItOutOfTheListButNotOutOfTheTable() = runTest {
        val store = store()
        store.remember(fill())

        store.resolve("key-1", Instant.parse("2026-08-15T10:00:00Z"))

        assertTrue(store.open().isEmpty())
        // Still remembered, so the same payment sitting in the shade cannot reopen it.
        store.remember(fill())
        assertTrue(store.open().isEmpty())
    }

    @Test
    fun answeredRowsArePrunedOnceTheyCannotBeReAsked() = runTest {
        val store = store()
        store.remember(fill())
        store.resolve("key-1", Instant.parse("2026-08-01T10:00:00Z"))

        store.pruneResolved(before = Instant.parse("2026-08-15T10:00:00Z"))

        // Gone for good — so the same key could be detected afresh, which is correct a
        // fortnight later.
        store.remember(fill())
        assertEquals(1, store.open().size)
    }

    @Test
    fun anUnansweredRowIsNeverPruned() = runTest {
        val store = store()
        store.remember(fill())

        store.pruneResolved(before = Instant.parse("2030-01-01T00:00:00Z"))

        assertEquals(1, store.open().size)
    }

    private fun fill(payload: String = "payload") = PendingFill(
        id = "key-1",
        draftPayload = payload,
        merchant = "Bharat Petroleum, Karol Bagh",
        amount = Amount.of(200_000).getOrNull(),
        detectedAt = Instant.parse("2026-08-15T09:00:00Z"),
    )
}
