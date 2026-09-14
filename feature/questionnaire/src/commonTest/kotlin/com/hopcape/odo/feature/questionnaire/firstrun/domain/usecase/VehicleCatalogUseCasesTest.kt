package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.FuelType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleCatalogUseCasesTest {

    private class FakeCatalog : VehicleCatalog {
        var modelsCallCount = 0
        var lastMake: String? = null
        override suspend fun makes(): List<String> = listOf("Maruti Suzuki", "Honda")
        override suspend fun popularMakes(): List<String> = listOf("Maruti Suzuki")
        override suspend fun models(make: String): List<CarModel> {
            modelsCallCount++
            lastMake = make
            return if (make == "Honda") {
                listOf(CarModel("City"), CarModel("City", "VX"), CarModel("Amaze"))
            } else {
                emptyList()
            }
        }

        override fun years(): List<Int> = listOf(2026, 2025, 2024)
        override fun fuelTypes(): List<FuelType> = FuelType.entries
    }

    @Test
    fun snapshot_carriesEverythingTheFormNeedsUpFront() = runTest {
        val snapshot = LoadVehicleCatalogUseCase(FakeCatalog())()

        assertEquals(listOf("Maruti Suzuki", "Honda"), snapshot.makes)
        assertEquals(listOf("Maruti Suzuki"), snapshot.popularMakes)
        assertEquals(listOf(2026, 2025, 2024), snapshot.years)
        assertTrue(FuelType.PETROL in snapshot.fuelTypes)
    }

    @Test
    fun snapshot_doesNotFetchModels() = runTest {
        val catalog = FakeCatalog()

        LoadVehicleCatalogUseCase(catalog)()

        // Models depend on the chosen make; loading them up front would pull most of
        // the catalog to show one list.
        assertEquals(0, catalog.modelsCallCount)
    }

    @Test
    fun models_areFetchedForTheChosenMake() = runTest {
        val catalog = FakeCatalog()

        val models = LoadCarModelsUseCase(catalog)("  Honda ")

        assertEquals(listOf(CarModel("City"), CarModel("City", "VX"), CarModel("Amaze")), models)
        assertEquals("Honda", catalog.lastMake)
    }

    @Test
    fun models_forBlankOrMissingMake_shortCircuitToEmpty() = runTest {
        val catalog = FakeCatalog()
        val useCase = LoadCarModelsUseCase(catalog)

        assertEquals(emptyList(), useCase(null))
        assertEquals(emptyList(), useCase("   "))
        assertEquals(0, catalog.modelsCallCount)
    }
}
