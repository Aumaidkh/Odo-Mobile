package com.hopcape.odo.feature.onboarding.domain.usecase

import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.PublicAssetUrls
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadMakeChoicesUseCaseTest {

    @Test
    fun logoPaths_becomeUrls_andMakesWithoutOneAreLeftOut() = runTest {
        val choices = LoadMakeChoicesUseCase(
            catalog = Catalog(logos = mapOf("Kia" to "vehicle-logos/makes/make-kia.png")),
            assetUrls = PublicAssetUrls { "https://api.test/storage/v1/object/public/$it" },
        )()

        assertEquals(mapOf("Kia" to "https://api.test/storage/v1/object/public/vehicle-logos/makes/make-kia.png"), choices.logos)
    }

    @Test
    fun noServer_meansNoLogos() = runTest {
        val choices = LoadMakeChoicesUseCase(
            catalog = Catalog(logos = mapOf("Kia" to "vehicle-logos/makes/make-kia.png")),
            assetUrls = PublicAssetUrls { null },
        )()

        assertEquals(emptyMap(), choices.logos)
    }

    @Test
    fun anUnreadableLogoTable_stillLoadsTheMakes() = runTest {
        val choices = LoadMakeChoicesUseCase(catalog = Catalog(logosFail = true))()

        assertEquals(listOf("Kia", "Tata"), choices.all)
        assertEquals(emptyMap(), choices.logos)
    }

    private class Catalog(
        private val logos: Map<String, String> = emptyMap(),
        private val logosFail: Boolean = false,
    ) : VehicleCatalog {
        override suspend fun makes(): List<String> = listOf("Kia", "Tata")
        override suspend fun popularMakes(limit: Int): List<String> = makes().take(limit)
        override suspend fun makeLogoPaths(): Map<String, String> = if (logosFail) error("unreadable") else logos
        override suspend fun models(make: String): List<CarModel> = emptyList()
        override fun years(): List<Int> = emptyList()
        override fun fuelTypes(): List<FuelType> = FuelType.entries
    }
}
