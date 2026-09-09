package com.hopcape.odo.infrastructure.database.owner

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.owner.QuestionAnswerDto
import com.hopcape.odo.core.data.owner.QuestionAnswerRemoteDataSource
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.sync.SyncCursor
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncRunner
import com.hopcape.odo.infrastructure.database.sync.inMemoryDatabase
import com.hopcape.odo.infrastructure.database.sync.silentSyncTelemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What sync does to a deselected answer.
 *
 * Deselecting is a soft delete, and a soft delete only reaches other devices if it is pushed.
 * These tests pin what happens to the tombstone when a pull arrives while the server still has
 * the answer live — the case that decides whether "I unticked that" can be silently undone.
 */
class QuestionAnswerDeselectionSyncTest {

    private val owner = OwnerId("5b28c012-545f-447d-9a85-920084f68246")
    private val goal = QuestionKey("goal.v1")
    private val answered = Instant.parse("2026-09-01T10:00:00Z")

    private class FixedClock(private var instant: Instant) : Clock {
        override fun now(): Instant = instant
        fun advanceTo(next: Instant) { instant = next }
    }

    /**
     * The owner deselects an answer the server still has live, and a pull lands before the
     * tombstone has been pushed.
     *
     * The tombstone is newer, so last-write-wins should keep it.
     */
    @Test
    fun `a pull does not resurrect an answer the owner just deselected`() = runTest {
        val (db, driver) = inMemoryDatabase()
        val clock = FixedClock(answered)
        val local = SqlDelightQuestionAnswerLocalDataSource(
            database = db,
            idGenerator = IdGenerator { "answer-1" },
            clock = clock,
        )
        local.replaceAnswers(owner, goal, setOf("SELL_SOON"))
        // The server accepted it earlier, so both sides hold the same id.
        driver.exec("UPDATE profile_answers SET sync_status = 'SYNCED'")

        clock.advanceTo(Instant.parse("2026-09-02T10:00:00Z"))
        local.replaceAnswers(owner, goal, emptySet())
        assertEquals(1, driver.count("SELECT COUNT(*) FROM profile_answers WHERE deleted_at IS NOT NULL"))

        // The server has not heard about the deletion yet and sends the row back as live.
        val remote = FakeRemote(listOf(dto(id = "answer-1", updatedAt = answered)))
        runner(db, remote).pull(FakeSynchronizer())

        assertEquals(
            1,
            driver.count("SELECT COUNT(*) FROM profile_answers WHERE deleted_at IS NOT NULL"),
            "the deselection was undone by a pull",
        )
    }

    /**
     * The same deselection, but the server's row carries a **newer** timestamp — another
     * device re-selected it after this one deselected it.
     *
     * Here the remote genuinely is newer, so it winning is correct, not a bug.
     */
    @Test
    fun `a newer answer from another device does win`() = runTest {
        val (db, driver) = inMemoryDatabase()
        val clock = FixedClock(answered)
        val local = SqlDelightQuestionAnswerLocalDataSource(
            database = db,
            idGenerator = IdGenerator { "answer-1" },
            clock = clock,
        )
        local.replaceAnswers(owner, goal, setOf("SELL_SOON"))
        driver.exec("UPDATE profile_answers SET sync_status = 'SYNCED'")
        clock.advanceTo(Instant.parse("2026-09-02T10:00:00Z"))
        local.replaceAnswers(owner, goal, emptySet())

        val later = Instant.parse("2026-09-03T10:00:00Z")
        runner(db, FakeRemote(listOf(dto(id = "answer-1", updatedAt = later)))).pull(FakeSynchronizer())

        assertEquals(0, driver.count("SELECT COUNT(*) FROM profile_answers WHERE deleted_at IS NOT NULL"))
    }

    /**
     * The same answer written on two devices offline: two ids, one triple.
     *
     * The pull's insert is refused by the unique index and its update keys on the server's id,
     * so the remote row lands nowhere. This pins that the local answer survives rather than
     * being duplicated.
     */
    @Test
    fun `an answer written under two ids does not duplicate`() = runTest {
        val (db, driver) = inMemoryDatabase()
        val local = SqlDelightQuestionAnswerLocalDataSource(
            database = db,
            idGenerator = IdGenerator { "local-id" },
            clock = FixedClock(answered),
        )
        local.replaceAnswers(owner, goal, setOf("SELL_SOON"))

        runner(db, FakeRemote(listOf(dto(id = "server-id", updatedAt = answered)))).pull(FakeSynchronizer())

        assertEquals(1, driver.count("SELECT COUNT(*) FROM profile_answers"))
    }

    /**
     * The same deselection, but the tombstone's push was **refused** and the row went to
     * CONFLICT before the pull arrived.
     *
     * **Pins today's behaviour, which is that the deselection is lost.** `decide` short-circuits
     * on anything that is not PENDING — "no local edit is at risk" — so an older remote row is
     * applied with no timestamp comparison, and the answer comes back live.
     *
     * Whether that is right is a decision about the whole engine, not this table. A CONFLICT row
     * is an unsent local edit, which argues for last-write-wins; but it is also a row the server
     * has already refused, and letting the server settle it is how the row ever converges.
     * Changing it moves every syncable table at once, so it is deliberately not changed here.
     */
    @Test
    fun `a deselection stuck in CONFLICT is currently lost to an older remote row`() = runTest {
        val (db, driver) = inMemoryDatabase()
        val clock = FixedClock(answered)
        val local = SqlDelightQuestionAnswerLocalDataSource(
            database = db,
            idGenerator = IdGenerator { "answer-1" },
            clock = clock,
        )
        local.replaceAnswers(owner, goal, setOf("SELL_SOON"))
        driver.exec("UPDATE profile_answers SET sync_status = 'SYNCED'")

        clock.advanceTo(Instant.parse("2026-09-02T10:00:00Z"))
        local.replaceAnswers(owner, goal, emptySet())
        // The server refused the tombstone, so it left the outbox.
        driver.exec("UPDATE profile_answers SET sync_status = 'CONFLICT'")

        runner(db, FakeRemote(listOf(dto(id = "answer-1", updatedAt = answered)))).pull(FakeSynchronizer())

        assertEquals(
            0,
            driver.count("SELECT COUNT(*) FROM profile_answers WHERE deleted_at IS NOT NULL"),
            "if this now fails, the engine's CONFLICT handling changed — read the KDoc above",
        )
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun runner(db: OdoDatabase, remote: FakeRemote) = SyncRunner(
        entity = SyncEntity.PROFILE_ANSWERS,
        table = QuestionAnswerSyncTable(database = db, remote = remote, ownerId = { owner.value }),
        database = db,
        telemetry = silentSyncTelemetry(),
    )

    private fun dto(id: String, updatedAt: Instant) = QuestionAnswerDto(
        id = id,
        ownerId = owner.value,
        questionKey = goal.value,
        value = "SELL_SOON",
        answeredAt = answered.toString(),
        createdAt = answered.toString(),
        updatedAt = updatedAt.toString(),
        deletedAt = null,
    )

    private class FakeRemote(private val rows: List<QuestionAnswerDto>) : QuestionAnswerRemoteDataSource {
        override suspend fun fetchSince(ownerId: String, since: Instant?) = rows
        override suspend fun push(answers: List<QuestionAnswerDto>) = answers
    }

    private class FakeSynchronizer : Synchronizer {
        private val cursors = mutableMapOf<SyncEntity, SyncCursor>()
        override suspend fun cursor(entity: SyncEntity): SyncCursor =
            cursors.getOrPut(entity) { SyncCursor(entity) }
        override suspend fun updateCursor(entity: SyncEntity, update: SyncCursor.() -> SyncCursor) {
            cursors[entity] = cursor(entity).update()
        }
        override suspend fun recordFailure(entity: SyncEntity, cause: Throwable) = Unit
    }

    private fun JdbcSqliteDriver.count(sql: String): Int =
        executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0)!!.toInt() else 0) }, 0).value

    private fun JdbcSqliteDriver.exec(sql: String) = execute(null, sql, 0)
}
