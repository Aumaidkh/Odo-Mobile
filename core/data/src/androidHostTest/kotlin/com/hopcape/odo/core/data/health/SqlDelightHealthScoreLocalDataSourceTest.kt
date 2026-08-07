package com.hopcape.odo.core.data.health

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQL behaviour for [SqlDelightHealthScoreLocalDataSource] — append-only ordering
 * (newest-first, oldest-first) and per-car scoping. Error mapping and sync scheduling
 * live in [HealthScoreRepositoryImplTest] instead, against a fake port.
 */
class SqlDelightHealthScoreLocalDataSourceTest {

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

    private fun local(db: OdoDatabase, now: String = "2026-08-01T10:00:00Z") =
        SqlDelightHealthScoreLocalDataSource(database = db, clock = FixedClock(Instant.parse(now)))

    private fun score(maintenance: Int = 28, documentation: Int = 24, cost: Int = 14, history: Int = 8) =
        HealthScore(
            factors = listOf(
                HealthFactor.of(HealthFactorKind.MAINTENANCE, maintenance),
                HealthFactor.of(HealthFactorKind.DOCUMENTATION, documentation),
                HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, cost),
                HealthFactor.of(HealthFactorKind.HISTORY, history),
            ),
        )

    private fun snapshot(
        id: String = "snap-1",
        car: CarId = carId,
        computedAt: String = "2026-08-01T10:00:00Z",
        score: HealthScore = score(),
        algoVersion: String = HealthScoreCalculator.RULES_VERSION,
    ) = HealthSnapshot(
        id = HealthSnapshotId(id),
        carId = car,
        ownerId = ownerId,
        score = score,
        computedAt = Instant.parse(computedAt),
        algoVersion = algoVersion,
    )

    @Test
    fun insert_storesTheBreakdownAndReadsItBack() = runTest {
        val db = newDb()
        val local = local(db)

        local.insert(snapshot())

        val latest = assertNotNull(local.latest(carId))
        assertEquals("snap-1", latest.id.value)
        assertEquals(74, latest.score.total)
        assertEquals(listOf(28, 24, 14, 8), latest.score.factors.map { it.earned })
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), latest.computedAt)
    }

    @Test
    fun insert_landsPendingForTheSyncEngine() = runTest {
        val db = newDb()
        local(db).insert(snapshot())

        val row = db.healthScoreQueries.selectLatest(carId.value).executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertEquals("2026-08-01T10:00:00Z", row.created_at)
        assertEquals("rule-v1", row.algo_version)
    }

    @Test
    fun latest_isTheNewestSnapshotNotTheLastWritten() = runTest {
        val db = newDb()
        val local = local(db)

        // Written newest-first, which is what a backfill would do.
        local.insert(snapshot(id = "new", computedAt = "2026-08-01T10:00:00Z", score = score(maintenance = 30)))
        local.insert(snapshot(id = "old", computedAt = "2026-06-01T10:00:00Z"))

        assertEquals("new", local.latest(carId)?.id?.value)
    }

    @Test
    fun latest_isNullForACarWithNoHistory() = runTest {
        assertNull(local(newDb()).latest(CarId("never-scored")))
    }

    @Test
    fun latest_ignoresAnotherCarsHistory() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(snapshot(id = "other", car = CarId("car-2")))

        assertNull(local.latest(carId))
    }

    @Test
    fun latestOnOrBefore_picksTheNewestSnapshotThatIsOldEnough() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(snapshot(id = "june", computedAt = "2026-06-15T10:00:00Z", score = score(maintenance = 20)))
        local.insert(snapshot(id = "july", computedAt = "2026-07-01T10:00:00Z", score = score(maintenance = 24)))
        local.insert(snapshot(id = "today", computedAt = "2026-08-01T10:00:00Z"))

        val baseline = local.latestOnOrBefore(carId, Instant.parse("2026-07-02T10:00:00Z"))

        assertEquals("july", baseline?.id?.value)
    }

    @Test
    fun latestOnOrBefore_includesASnapshotTakenExactlyThen() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(snapshot(id = "july", computedAt = "2026-07-01T10:00:00Z"))

        assertEquals("july", local.latestOnOrBefore(carId, Instant.parse("2026-07-01T10:00:00Z"))?.id?.value)
    }

    @Test
    fun latestOnOrBefore_isNullWhenNothingIsThatOld() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(snapshot(computedAt = "2026-08-01T10:00:00Z"))

        // The screen hides the delta rather than comparing against a newer number.
        assertNull(local.latestOnOrBefore(carId, Instant.parse("2026-07-01T10:00:00Z")))
    }

    @Test
    fun observeHistory_isOldestFirst() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(snapshot(id = "july", computedAt = "2026-07-01T10:00:00Z"))
        local.insert(snapshot(id = "june", computedAt = "2026-06-01T10:00:00Z"))
        local.insert(snapshot(id = "august", computedAt = "2026-08-01T10:00:00Z"))

        // Written out of order, read in the order the moves are worked out in.
        val history = local.observeHistory(carId).first()

        assertEquals(listOf("june", "july", "august"), history.map { it.id.value })
    }

    @Test
    fun observeHistory_carriesTheRulesVersionEachScoreWasTakenUnder() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(snapshot(id = "old-rules", algoVersion = "rule-v0"))
        local.insert(snapshot(id = "new-rules", computedAt = "2026-08-02T10:00:00Z"))

        val history = local.observeHistory(carId).first()

        // The stamp travels with the row rather than being rewritten by whatever this build
        // computes — a comparison across the two is what the timeline has to refuse.
        assertEquals(listOf("rule-v0", HealthScoreCalculator.RULES_VERSION), history.map { it.algoVersion })
    }

    @Test
    fun observeHistory_ignoresAnotherCarsSnapshots() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(snapshot(id = "mine"))
        local.insert(snapshot(id = "theirs", car = CarId("car-2")))

        assertEquals(listOf("mine"), local.observeHistory(carId).first().map { it.id.value })
    }
}
