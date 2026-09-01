package com.hopcape.odo.feature.profile.domain.usecase

import com.hopcape.odo.core.domain.city.City
import com.hopcape.odo.core.domain.city.CityCatalog

/**
 * The cities the city picker offers, fetched once when the edit-profile screen opens.
 */
internal class LoadCityCatalogUseCase(
    private val catalog: CityCatalog,
) {
    suspend operator fun invoke(): List<City> = catalog.cities()
}
