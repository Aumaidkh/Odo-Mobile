package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * The reference data the manual-entry step needs the moment it opens: every brand, the
 * handful offered as one-tap chips, the selectable years, and the fuel types.
 *
 * Fetched as one snapshot rather than three separate calls because the screen needs all of
 * it before it can render a usable form, and three round trips to the same local table only
 * buys three chances to be half-loaded.
 *
 * Models are **not** here — they depend on which make the owner picks, so they are fetched
 * on selection by [LoadCarModelsUseCase].
 */
internal class LoadVehicleCatalogUseCase(
    private val catalog: VehicleCatalog,
) {
    suspend operator fun invoke(): VehicleCatalogSnapshot = VehicleCatalogSnapshot(
        makes = catalog.makes(),
        popularMakes = catalog.popularMakes(),
        years = catalog.years(),
        fuelTypes = catalog.fuelTypes(),
    )
}

/** Everything the manual-entry pickers offer, except the make-dependent model list. */
internal data class VehicleCatalogSnapshot(
    val makes: List<String>,
    val popularMakes: List<String>,
    val years: List<Int>,
    val fuelTypes: List<FuelType>,
)
