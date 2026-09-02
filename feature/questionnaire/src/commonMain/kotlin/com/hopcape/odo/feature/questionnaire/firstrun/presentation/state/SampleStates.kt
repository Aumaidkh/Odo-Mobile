package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * Seed state for `@Preview`s only — a plausible Pune owner mid-flow.
 *
 * Previews are the only consumer: the running app gets its reference data from the
 * `VehicleCatalog` port through `OnboardingViewModel`, never from here.
 */
internal fun sampleCarStep(): CarStepState = CarStepState(
    plate = FormField("MH12AB1234"),
    lookup = PlateLookup.Found(
        RtoMatch(
            make = "Maruti",
            model = "Swift",
            variant = "VXI",
            year = 2020,
            fuelType = FuelType.PETROL,
        ),
    ),
)

internal fun sampleCarDetails(): CarDetailsState = CarDetailsState(
    catalog = Loadable.Ready(sampleCatalog()),
    models = sampleModels,
    make = FormField("Honda"),
    model = FormField(CarModel("City", "VX CVT")),
    year = FormField(2026),
    fuel = FormField(FuelType.PETROL),
)

internal fun sampleProfile(): ProfileState = ProfileState(
    name = FormField("Rahul"),
    goals = setOf("TRACK_COSTS"),
)

internal fun sampleCatalog(): CatalogOptions = CatalogOptions(
    makes = sampleMakes,
    popularMakes = samplePopularMakes,
    years = CatalogOptions.DEFAULT_YEARS,
    fuelTypes = FuelType.entries,
)

internal val sampleMakes: List<String> = listOf(
    "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Honda", "Toyota",
    "Kia", "Renault", "Volkswagen", "Skoda", "MG", "Nissan",
)

internal val samplePopularMakes: List<String> = listOf("Maruti Suzuki", "Hyundai", "Tata", "Honda")

internal val sampleModels: List<CarModel> = listOf(
    CarModel("City"),
    CarModel("City", "VX CVT"),
    CarModel("City", "V MT"),
    CarModel("Amaze", "S MT"),
    CarModel("Elevate", "ZX CVT"),
    CarModel("Jazz", "VX MT"),
)
