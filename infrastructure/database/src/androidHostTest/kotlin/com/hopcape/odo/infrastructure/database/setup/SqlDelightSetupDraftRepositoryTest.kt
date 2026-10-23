package com.hopcape.odo.infrastructure.database.setup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.setup.SetupDraft
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.performance.api.APM
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SqlDelightSetupDraftRepositoryTest {

    private fun repository(): SqlDelightSetupDraftRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return SqlDelightSetupDraftRepository(OdoDatabase(driver), HLogger.asLogger(), APM.asTracer())
    }

    @Test
    fun noDraftReadsAsNull() = runTest {
        assertNull(repository().observe().first())
    }

    @Test
    fun aSavedDraftReadsBackWhole() = runTest {
        val repository = repository()
        val draft = SetupDraft(
            make = "Hyundai",
            modelName = "Creta",
            modelVariant = "SX",
            year = 2022,
            fuel = FuelType.PETROL,
            updatedAt = Instant.parse("2026-10-24T10:00:00Z"),
        )

        assertTrue(repository.save(draft).isRight())

        assertEquals(draft, repository.observe().first())
    }

    @Test
    fun aSecondSaveReplacesTheFirst() = runTest {
        val repository = repository()
        repository.save(SetupDraft(make = "Hyundai", modelName = "Creta", updatedAt = NOW))

        repository.save(SetupDraft(make = "Tata", updatedAt = NOW))

        assertEquals(SetupDraft(make = "Tata", updatedAt = NOW), repository.observe().first())
    }

    @Test
    fun clearLeavesNoDraft() = runTest {
        val repository = repository()
        repository.save(SetupDraft(make = "Hyundai", updatedAt = NOW))

        assertTrue(repository.clear().isRight())

        assertNull(repository.observe().first())
    }

    private companion object {
        val NOW = Instant.parse("2026-10-24T10:00:00Z")
    }
}
