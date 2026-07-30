package com.hopcape.odo.feature.onboarding.domain.usecase

import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog

/**
 * The models offered for a chosen make, fetched when the owner picks a brand rather than up
 * front — loading every brand's models would be most of the catalog to show one list.
 *
 * A blank make short-circuits to an empty list: the picker is asking "which Honda?" before
 * a brand exists, and the catalog has no useful answer.
 */
internal class LoadCarModelsUseCase(
    private val catalog: VehicleCatalog,
) {
    suspend operator fun invoke(make: String?): List<CarModel> {
        val brand = make?.trim()?.ifBlank { null } ?: return emptyList()
        return catalog.models(brand)
    }
}
