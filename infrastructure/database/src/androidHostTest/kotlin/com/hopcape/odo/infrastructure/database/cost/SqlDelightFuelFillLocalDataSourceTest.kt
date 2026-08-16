package com.hopcape.odo.infrastructure.database.cost

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * SQL behaviour for [SqlDelightFuelFillLocalDataSource]. Error mapping and sync scheduling
 * live in [FuelFillRepositoryImplTest] instead, against a fake port.
 */
class SqlDelightFuelFillLocalDataSourceTest {

    private lateinit var driver: JdbcSqliteDriver

    private fun newDb(): OdoDatabase {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private data class StoredRow(val syncStatus: String, val remoteVersion: String?)

    /** No generated `selectById` exists for `fuel_fills` yet, so this reads the raw row. */
    private fun rowFor(id: String): StoredRow? = driver.executeQuery(
        identifier = null,
        sql = "SELECT sync_status, remote_version FROM fuel_fills WHERE id = ?",
        mapper = { cursor ->
            QueryResult.Value(
                if (cursor.next().value) StoredRow(cursor.getString(0)!!, cursor.getString(1)) else null,
            )
        },
        parameters = 1,
    ) { bindString(0, id) }.value

    private fun fill(
        id: String = "fill-1",
        carId: String = "car-1",
        filledOn: LocalDate = LocalDate(2026, 8, 1),
        odometerKm: Int? = 45_000,
        source: FillEntrySource = FillEntrySource.MANUAL,
    ) = FuelFill.create(
        id = FuelFillId(id),
        carId = CarId(carId),
        ownerId = OwnerId("owner-1"),
        filledOn = filledOn,
        odometerKm = odometerKm,
        quantityMilli = 32_450,
        unit = FuelUnit.LITRE,
        amountPaise = 320_000,
        today = LocalDate(2026, 8, 30),
        stationName = "HP Andheri",
        transactionRef = "txn-1",
        entrySource = source,
    ).getOrNull()!!

    @Test
    fun insert_storesTheFillAsPending() = runTest {
        val db = newDb()

        SqlDelightFuelFillLocalDataSource(database = db).insert(fill())

        assertEquals(SyncStatus.PENDING.name, rowFor("fill-1")?.syncStatus)
        assertNull(rowFor("fill-1")?.remoteVersion)
    }

    @Test
    fun theCapturingChannelSurvivesARoundTrip() = runTest {
        val local = SqlDelightFuelFillLocalDataSource(database = newDb())

        local.insert(fill(source = FillEntrySource.PUMP_OCR))

        assertEquals(FillEntrySource.PUMP_OCR, local.latestForCar(CarId("car-1"))?.entrySource)
    }

    @Test
    fun readsComeBackNewestFirst() = runTest {
        val local = SqlDelightFuelFillLocalDataSource(database = newDb())
        local.insert(fill(id = "old", filledOn = LocalDate(2026, 7, 1), odometerKm = 40_000))
        local.insert(fill(id = "new", filledOn = LocalDate(2026, 8, 1), odometerKm = 45_000))
        local.insert(fill(id = "mid", filledOn = LocalDate(2026, 7, 20), odometerKm = 43_000))

        val fills = local.observeByCar(CarId("car-1")).first()

        assertEquals(listOf("new", "mid", "old"), fills.map { it.id.value })
        assertEquals("new", local.latestForCar(CarId("car-1"))?.id?.value)
    }

    @Test
    fun anotherCarsFillsAreNotRead() = runTest {
        val local = SqlDelightFuelFillLocalDataSource(database = newDb())
        local.insert(fill(id = "mine", carId = "car-1"))
        local.insert(fill(id = "theirs", carId = "car-2"))

        assertEquals(listOf("mine"), local.observeByCar(CarId("car-1")).first().map { it.id.value })
    }

    @Test
    fun aTombstonedFillDropsOutOfEveryRead() = runTest {
        val db = newDb()
        val local = SqlDelightFuelFillLocalDataSource(database = db)
        local.insert(fill(id = "gone"))

        db.fuelFillQueries.softDeleteFuelFillsForCar(
            deletedAt = "2026-08-02T00:00:00Z",
            syncStatus = SyncStatus.PENDING.name,
            carId = "car-1",
        )

        assertEquals(emptyList(), local.observeByCar(CarId("car-1")).first())
        assertNull(local.latestForCar(CarId("car-1")))
        assertEquals(0, local.countBySource(CarId("car-1"), FillEntrySource.MANUAL))
    }

    @Test
    fun countsAreKeptPerChannel() = runTest {
        val local = SqlDelightFuelFillLocalDataSource(database = newDb())
        local.insert(fill(id = "a", source = FillEntrySource.DETECTED))
        local.insert(fill(id = "b", source = FillEntrySource.DETECTED))
        local.insert(fill(id = "c", source = FillEntrySource.MANUAL))

        assertEquals(2, local.countBySource(CarId("car-1"), FillEntrySource.DETECTED))
        assertEquals(1, local.countBySource(CarId("car-1"), FillEntrySource.MANUAL))
        assertEquals(0, local.countBySource(CarId("car-1"), FillEntrySource.PUMP_OCR))
    }

    /**
     * What a phone in production does on first launch after this update.
     *
     * Version 2 is what is shipped, and the one migration to 3 rebuilds `fuel_fills` to do
     * three things SQLite 3.18 cannot do in place: relax `odometer_km` to nullable, add
     * `entry_source`, and drop `paid_via`. A rebuild is the one kind of migration that can
     * destroy real data — a mistyped column list shifts values into the wrong slots and
     * nothing complains — so this drives it against the shipped DDL and checks every column,
     * not just the ones that changed. A copy that put `paid_via` where the odometer belongs
     * would still return a row.
     */
    @Test
    fun theOneMigrationCarriesAProductionRowAcrossUntouched() = runTest {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        executeRaw(SHIPPED_FUEL_FILLS_DDL)
        executeRaw(
            """
            INSERT INTO fuel_fills (
                id, car_id, owner_id, filled_on, odometer_km, quantity_milli, fuel_unit,
                amount_paise, station_name, paid_via, transaction_ref,
                created_at, updated_at, sync_status
            ) VALUES (
                'kept', 'car-1', 'owner-1', '2026-07-01', 40000, 20000, 'LITRE',
                200000, 'HP Andheri', 'UPI', 'txn-legacy',
                '2026-07-01T00:00:00Z', '2026-07-01T00:00:00Z', 'PENDING'
            )
            """.trimIndent(),
        )

        OdoDatabase.Schema.migrate(driver, oldVersion = 2L, newVersion = 3L).await()

        val local = SqlDelightFuelFillLocalDataSource(database = OdoDatabase(driver))
        val kept = local.latestForCar(CarId("car-1"))
        assertNotNull(kept, "the rebuild dropped the only row")
        assertEquals("kept", kept.id.value)
        assertEquals(40_000, kept.odometer?.km)
        assertEquals(20_000L, kept.quantityMilli)
        assertEquals(200_000L, kept.amount.paise)
        assertEquals("HP Andheri", kept.stationName)
        assertEquals("txn-legacy", kept.transactionRef)
        // The row predates the column, so it has no channel to name and reads as MANUAL.
        assertEquals(FillEntrySource.MANUAL, kept.entrySource)
    }

    /**
     * The migration also has to leave a working table behind, not just a populated one.
     *
     * A rebuild that forgot its index, or left the new table under its temporary name, would
     * still pass the row-by-row check above. Writing and reading after the migration is what
     * catches that.
     */
    @Test
    fun theMigratedTableStillTakesWritesAndAllowsAMissingOdometer() = runTest {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        executeRaw(SHIPPED_FUEL_FILLS_DDL)

        OdoDatabase.Schema.migrate(driver, oldVersion = 2L, newVersion = 3L).await()

        val local = SqlDelightFuelFillLocalDataSource(database = OdoDatabase(driver))
        // Against the shipped table this insert threw: odometer_km was NOT NULL.
        local.insert(fill(id = "no-reading", odometerKm = null))

        assertNull(local.latestForCar(CarId("car-1"))?.odometer)
    }

    @Test
    fun theRebuiltTableTakesAFillWithNoOdometerOnAFreshInstall() = runTest {
        // The `.sq` and the migration have to agree; this is the fresh-install half of it.
        val local = SqlDelightFuelFillLocalDataSource(database = newDb())

        local.insert(fill(id = "no-reading", odometerKm = null))

        assertNull(local.latestForCar(CarId("car-1"))?.odometer)
    }

    private fun executeRaw(sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }

    private companion object {
        /**
         * `fuel_fills` exactly as schema version 2 shipped it — the state every migration
         * test here starts from, because it is the state the migration really runs against.
         */
        val SHIPPED_FUEL_FILLS_DDL = """
            CREATE TABLE fuel_fills (
                id              TEXT NOT NULL PRIMARY KEY,
                car_id          TEXT NOT NULL,
                owner_id        TEXT NOT NULL,
                filled_on       TEXT NOT NULL,
                odometer_km     INTEGER NOT NULL,
                quantity_milli  INTEGER NOT NULL,
                fuel_unit       TEXT NOT NULL,
                amount_paise    INTEGER NOT NULL,
                station_name    TEXT,
                paid_via        TEXT NOT NULL DEFAULT 'UNKNOWN',
                transaction_ref TEXT,
                created_at      TEXT NOT NULL,
                updated_at      TEXT NOT NULL,
                deleted_at      TEXT,
                remote_version  TEXT,
                sync_status     TEXT NOT NULL DEFAULT 'PENDING'
            )
        """.trimIndent()
    }
}
