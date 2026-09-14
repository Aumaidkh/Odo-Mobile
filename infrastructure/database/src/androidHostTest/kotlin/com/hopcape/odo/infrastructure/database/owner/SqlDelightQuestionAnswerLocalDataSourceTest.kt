package com.hopcape.odo.infrastructure.database.owner

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** SQL behaviour for [SqlDelightQuestionAnswerLocalDataSource], mostly the replace-and-revive write. */
class SqlDelightQuestionAnswerLocalDataSourceTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: OdoDatabase

    private val owner = OwnerId("owner-1")
    private val goal = QuestionKey("goal.v1")

    /** Sequential rather than random, so a test can assert an id did not change. */
    private var nextId = 0
    private val ids = IdGenerator { "answer-${nextId++}" }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private fun newSource(clock: Clock = Clock.System): SqlDelightQuestionAnswerLocalDataSource {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        database = OdoDatabase(driver)
        return SqlDelightQuestionAnswerLocalDataSource(
            database = database,
            idGenerator = ids,
            clock = clock,
        )
    }

    private data class StoredRow(val id: String, val deletedAt: String?, val syncStatus: String)

    /** Reads the raw row including tombstones, which no generated query exposes. */
    private fun rowFor(value: String): StoredRow? = driver.executeQuery(
        identifier = null,
        sql = "SELECT id, deleted_at, sync_status FROM profile_answers " +
            "WHERE owner_id = ? AND question_key = ? AND answer_value = ?",
        mapper = { cursor ->
            QueryResult.Value(
                if (cursor.next().value) {
                    StoredRow(cursor.getString(0)!!, cursor.getString(1), cursor.getString(2)!!)
                } else {
                    null
                },
            )
        },
        parameters = 3,
    ) { bindString(0, owner.value); bindString(1, goal.value); bindString(2, value) }.value

    private fun rowCount(): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM profile_answers",
        mapper = { cursor -> QueryResult.Value(cursor.next().value.let { cursor.getLong(0)!! }) },
        parameters = 0,
    ) {}.value

    @Test
    fun `stores every selected option as its own row`() = runTest {
        val source = newSource()

        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS", "NEVER_MISS_RENEWAL"))

        val stored = source.answersFor(goal).map { it.value }.toSet()
        assertEquals(setOf("TRACK_COSTS", "NEVER_MISS_RENEWAL"), stored)
    }

    @Test
    fun `replaces rather than appends`() = runTest {
        val source = newSource()
        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS", "SELL_SOON"))

        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS"))

        assertEquals(listOf("TRACK_COSTS"), source.answersFor(goal).map { it.value })
    }

    /** A tombstone is the only way a second device learns the option was deselected here. */
    @Test
    fun `a deselected option is tombstoned and left pending`() = runTest {
        val source = newSource()
        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS", "SELL_SOON"))

        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS"))

        val dropped = assertNotNull(rowFor("SELL_SOON"))
        assertNotNull(dropped.deletedAt, "a deselected option must survive as a tombstone")
        assertEquals("PENDING", dropped.syncStatus)
    }

    /** Why the unique index covers tombstones: re-selecting must revive, not add a row. */
    @Test
    fun `re-selecting revives the same row instead of adding one`() = runTest {
        val source = newSource()
        source.replaceAnswers(owner, goal, setOf("SELL_SOON"))
        val originalId = assertNotNull(rowFor("SELL_SOON")).id

        source.replaceAnswers(owner, goal, emptySet())
        source.replaceAnswers(owner, goal, setOf("SELL_SOON"))

        val revived = assertNotNull(rowFor("SELL_SOON"))
        assertEquals(originalId, revived.id, "a revived answer must keep its id")
        assertNull(revived.deletedAt)
        assertEquals(1L, rowCount(), "re-selecting must not accumulate rows")
    }

    /** An empty set is a real answer meaning "none of these", not a no-op. */
    @Test
    fun `an empty set clears the question`() = runTest {
        val source = newSource()
        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS", "SELL_SOON"))

        source.replaceAnswers(owner, goal, emptySet())

        assertTrue(source.answersFor(goal).isEmpty())
        assertNotNull(assertNotNull(rowFor("TRACK_COSTS")).deletedAt)
    }

    /** Saving one question must not disturb another. */
    @Test
    fun `replacing one key leaves other keys alone`() = runTest {
        val source = newSource()
        val workshop = QuestionKey("workshop_tier.v1")
        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS"))
        source.replaceAnswers(owner, workshop, setOf("LOCAL"))

        source.replaceAnswers(owner, goal, setOf("SELL_SOON"))

        assertEquals(listOf("LOCAL"), source.answersFor(workshop).map { it.value })
    }

    @Test
    fun `observe excludes tombstones`() = runTest {
        val source = newSource()
        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS", "SELL_SOON"))
        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS"))

        val observed = source.observeAnswers().first()

        assertEquals(listOf("TRACK_COSTS"), observed.map { it.value })
    }

    /** `answered_at` is the owner-facing timestamp and must survive the round trip. */
    @Test
    fun `answeredAt is stamped from the clock`() = runTest {
        val instant = Instant.parse("2026-09-02T10:15:30Z")
        val source = newSource(clock = FixedClock(instant))

        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS"))

        assertEquals(instant, source.answersFor(goal).single().answeredAt)
    }

    /** Writing the same set twice changes nothing but the timestamps. */
    @Test
    fun `saving the same set twice is not an error`() = runTest {
        val source = newSource()
        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS"))

        source.replaceAnswers(owner, goal, setOf("TRACK_COSTS"))

        assertEquals(listOf("TRACK_COSTS"), source.answersFor(goal).map { it.value })
        assertEquals(1L, rowCount())
    }
}
