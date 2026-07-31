package com.hopcape.odo.feature.garage.domain.usecase

import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * The reference data the add-car and edit-car forms need the moment they open: every brand,
 * the handful offered as one-tap chips, the selectable years, and the fuel types.
 *
 * One snapshot rather than four calls, because the form cannot render usefully until it has
 * all of it, and four round trips to the same local table only buy four chances to be
 * half-loaded.
 *
 * Models are not here — they depend on which make is chosen, so they are fetched on
 * selection by [LoadCarModelsUseCase].
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

/** Everything the car-detail pickers offer, except the make-dependent model list. */
internal data class VehicleCatalogSnapshot(
    val makes: List<String>,
    val popularMakes: List<String>,
    val years: List<Int>,
    val fuelTypes: List<FuelType>,
)

/**
 * The models offered for a chosen make, fetched when a brand is picked rather than up
 * front — loading every brand's models would be most of the catalog to show one list.
 *
 * A blank make answers with an empty list: the picker is asking "which Honda?" before there
 * is a brand, and the catalog has nothing useful to say.
 */
internal class LoadCarModelsUseCase(
    private val catalog: VehicleCatalog,
) {
    suspend operator fun invoke(make: String?): List<CarModel> {
        val brand = make?.trim()?.ifBlank { null } ?: return emptyList()
        return catalog.models(brand)
    }
}
