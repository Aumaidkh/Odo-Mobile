package com.hopcape.odo.infrastructure.database.cost

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.payment.model.PaymentMethod
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun fill(id: String = "fill-1") = FuelFill.create(
        id = FuelFillId(id),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        filledOn = LocalDate(2026, 8, 1),
        odometerKm = 45_000,
        quantityMilli = 32_450,
        unit = FuelUnit.LITRE,
        amountPaise = 320_000,
        today = LocalDate(2026, 8, 1),
        stationName = "HP Andheri",
        paidVia = PaymentMethod.UPI,
        transactionRef = "txn-1",
    ).getOrNull()!!

    @Test
    fun insert_storesTheFillAsPending() = runTest {
        val db = newDb()

        SqlDelightFuelFillLocalDataSource(database = db).insert(fill())

        assertEquals(SyncStatus.PENDING.name, rowFor("fill-1")?.syncStatus)
        assertNull(rowFor("fill-1")?.remoteVersion)
    }
}
