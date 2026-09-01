package com.hopcape.odo.infrastructure.database.car

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import kotlinx.coroutines.test.runTest
import java.time.Year
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
    fun popularMakes_areAPrefixOfTheFullList() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())

        val popular = catalog.popularMakes()
        val all = catalog.makes()

        // Same ordering, just fewer — "popular" and "listed first" must never disagree.
        assertEquals(all.take(popular.size), popular)
        assertTrue(popular.size < all.size, "chips must be a subset, not the whole list")
    }

    @Test
    fun models_areReturnedForAKnownMake() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())
        val models = catalog.models("Maruti Suzuki")
        assertTrue(models.any { it.name == "Swift" }, "expected Swift in $models")
    }

    @Test
    fun everyModel_isAlsoOfferedWithoutATrim() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())
        val models = catalog.models("Maruti Suzuki")

        // An owner whose exact trim isn't seeded must still be able to name their car,
        // rather than pick a wrong one — a wrong trim feeds per-km cost and fairness.
        val named = models.map { it.name }.distinct()
        val trimless = models.filter { it.variant == null }.map { it.name }
        assertEquals(named.toSet(), trimless.toSet())
    }

    @Test
    fun trims_followTheirModelInLadderOrder() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())
        val swift = catalog.models("Maruti Suzuki").filter { it.name == "Swift" }

        assertEquals(
            listOf(
                CarModel("Swift"),
                CarModel("Swift", "LXI"),
                CarModel("Swift", "VXI"),
                CarModel("Swift", "ZXI"),
                CarModel("Swift", "ZXI+"),
            ),
            swift,
        )
    }

    @Test
    fun modelsOfOneMake_stayGroupedTogether() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())
        val names = catalog.models("Honda").map { it.name }

        // display_order packs model and trim position into one number, so a model's
        // trims must never be interleaved with another model's.
        assertEquals(names.distinct().size, names.zipWithNext().count { (a, b) -> a != b } + 1)
    }

    @Test
    fun models_unknownMake_isEmpty() = runTest {
        val catalog = VehicleCatalogImpl(seededDb())
        assertTrue(catalog.models("Nonexistent").isEmpty())
    }

    @Test
    fun seed_producesARowForEveryModelAndTrim() = runTest {
        val db = seededDb()

        // Guards against id-slug collisions: two trims that slugged to the same id would
        // be silently swallowed by INSERT OR IGNORE, quietly losing a trim from the
        // picker. "ZXI" vs "ZXI+" did exactly that before the slug spelled `+` out.
        val expected = VEHICLE_SEED.sumOf { make ->
            make.models.sumOf { model -> 1L + model.variants.size }
        }
        assertEquals(expected, db.vehicleModelQueries.countModels().executeAsOne())
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
    fun reseed_replacesRowsWhenTheStoredVersionIsStale() = runTest {
        val db = seededDb()
        // Simulate an app update that changed the bundled catalog: the stored version no
        // longer matches what the (unchanged, in this test) code ships.
        db.vehicleCatalogMetaQueries.setSeedVersion(-1L)
        db.vehicleMakeQueries.deleteAllMakes()
        db.vehicleModelQueries.deleteAllModels()
        assertEquals(0L, db.vehicleMakeQueries.countMakes().executeAsOne())

        seedVehicleReferenceData(db)

        assertEquals(VEHICLE_SEED.size.toLong(), db.vehicleMakeQueries.countMakes().executeAsOne())
        assertTrue(db.vehicleModelQueries.countModels().executeAsOne() > 0)
    }

    @Test
    fun reseed_isSkippedWhenTheStoredVersionAlreadyMatches() = runTest {
        val db = seededDb()
        // A row an ordinary reseed would never insert — proof that a matching version
        // short-circuits before the delete-and-reinsert runs at all.
        db.vehicleMakeQueries.insertMake(id = "sentinel", name = "Sentinel Motors", display_order = 999L)

        seedVehicleReferenceData(db)

        assertTrue(db.vehicleMakeQueries.selectAllMakes().executeAsList().contains("Sentinel Motors"))
    }

    @Test
    fun years_areNewestFirstAndNeverInTheFuture() = runTest {
        val currentYear = Year.now().value
        val catalog = VehicleCatalogImpl(seededDb())
        val years = catalog.years()

        assertEquals(currentYear, years.first()) // newest selectable = current year, never future
        assertEquals(1980, years.last())
        assertTrue(years.none { it > currentYear }, "no future years may be selectable")
        assertEquals(FuelType.entries.toList(), catalog.fuelTypes())
    }
}
