package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.PublicAssetUrls
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadMakeLogoUseCaseTest {

    private val urls = PublicAssetUrls { "https://api.test/$it" }

    @Test
    fun aKnownMake_getsItsUrl_whateverItsCase() = runTest {
        val load = LoadMakeLogoUseCase(Catalog(mapOf("Hyundai" to "vehicle-logos/makes/make-hyundai.png")), urls)

        assertEquals("https://api.test/vehicle-logos/makes/make-hyundai.png", load(" hyundai "))
    }

    @Test
    fun aMakeWithoutALogo_getsNull() = runTest {
        assertNull(LoadMakeLogoUseCase(Catalog(emptyMap()), urls)("Premier"))
    }

    @Test
    fun anUnreadableCatalog_getsNull() = runTest {
        assertNull(LoadMakeLogoUseCase(Catalog(fails = true), urls)("Hyundai"))
    }

    private class Catalog(
        private val logos: Map<String, String> = emptyMap(),
        private val fails: Boolean = false,
    ) : VehicleCatalog {
        override suspend fun makes(): List<String> = logos.keys.toList()
        override suspend fun popularMakes(limit: Int): List<String> = makes().take(limit)
        override suspend fun makeLogoPaths(): Map<String, String> = if (fails) error("unreadable") else logos
        override suspend fun models(make: String): List<CarModel> = emptyList()
        override fun years(): List<Int> = emptyList()
        override fun fuelTypes(): List<FuelType> = FuelType.entries
    }
}
