package com.hopcape.odo.core.data.car

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.domain.car.model.FuelType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleCatalogImplTest {

    private fun seededDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver).also(::seedVehicleReferenceData)
    }

    @Test
    fun makes_areSeededInDisplayOrder() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())
        val makes = catalog.makes()
        assertTrue(makes.isNotEmpty())
        // First seed entry is the display-order leader.
        assertEquals("Maruti Suzuki", makes.first())
    }

    @Test
    fun models_areReturnedForAKnownMake() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())
        val models = catalog.models("Maruti Suzuki")
        assertTrue("Swift" in models, "expected Swift in $models")
    }

    @Test
    fun models_unknownMake_isEmpty() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())
        assertTrue(catalog.models("Nonexistent").isEmpty())
    }

    @Test
    fun seeding_isIdempotent() = runTest {
        val db = seededDb()
        val before = db.vehicleMakeQueries.countMakes().executeAsOne()
        seedVehicleReferenceData(db) // second run is a no-op
        val after = db.vehicleMakeQueries.countMakes().executeAsOne()
        assertEquals(before, after)
    }

    @Test
    fun yearsAndFuelTypes_comeFromDomain() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())
        val years = catalog.years()
        assertEquals(2100, years.first()) // newest first
        assertEquals(1980, years.last())
        assertEquals(FuelType.entries.toList(), catalog.fuelTypes())
    }
}
