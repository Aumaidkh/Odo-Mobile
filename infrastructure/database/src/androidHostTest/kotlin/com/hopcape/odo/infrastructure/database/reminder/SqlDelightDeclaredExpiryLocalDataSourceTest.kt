package com.hopcape.odo.infrastructure.database.reminder

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.DeclaredExpiry
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightDeclaredExpiryLocalDataSourceTest {

    private val car = CarId("car-1")

    private fun database(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun expiry(id: String, dueOn: LocalDate) =
        DeclaredExpiry(id, car, OwnerId("owner-1"), ReminderKind.INSURANCE_EXPIRY, dueOn)

    @Test
    fun aDeclaredExpiryIsReadBackAndPendingForSync() = runTest {
        val db = database()
        val source = SqlDelightDeclaredExpiryLocalDataSource(db, dispatcher = Dispatchers.Unconfined)

        source.replace(expiry("dec-1", LocalDate(2027, 3, 31)))

        assertEquals(listOf(expiry("dec-1", LocalDate(2027, 3, 31))), source.observeByCar(car).first())
        assertEquals("PENDING", db.reminderQueries.selectPending().executeAsList().single().sync_status)
    }

    @Test
    fun declaringAgainReplacesTheEarlierOne() = runTest {
        val source = SqlDelightDeclaredExpiryLocalDataSource(database(), dispatcher = Dispatchers.Unconfined)

        source.replace(expiry("dec-1", LocalDate(2027, 3, 31)))
        source.replace(expiry("dec-2", LocalDate(2027, 5, 31)))

        assertEquals(listOf("dec-2"), source.observeByCar(car).first().map { it.id })
    }
}
