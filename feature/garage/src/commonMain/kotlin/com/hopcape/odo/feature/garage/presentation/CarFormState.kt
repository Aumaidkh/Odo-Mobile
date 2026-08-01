package com.hopcape.odo.feature.garage.presentation

import androidx.compose.runtime.Immutable
import arrow.core.NonEmptyList
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.garage.domain.usecase.VehicleCatalogSnapshot
import com.hopcape.odo.feature.garage.presentation.state.FormField
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_field_fuel
import com.hopcape.odo.feature.garage.resources.gr_error_field_make
import com.hopcape.odo.feature.garage.resources.gr_error_field_model
import com.hopcape.odo.feature.garage.resources.gr_error_field_year

/**
 * The answers that describe a car, as a form. Shared by the add and edit screens, which ask
 * the same four questions plus a plate and a nickname.
 *
 * Each answer carries its own error, so a rejected save marks the field that caused it
 * rather than showing one message above a form of five valid inputs.
 */
@Immutable
internal data class CarFormFields(
    val make: FormField<String> = FormField(),
    val model: FormField<CarModel> = FormField(),
    val year: FormField<Int> = FormField(),
    val fuel: FormField<FuelType> = FormField(),
    val registration: FormField<String> = FormField(),
    val nickname: FormField<String> = FormField(),
) {
    /**
     * Mark the fields the domain rejected.
     *
     * Errors it cannot pin on a field — a persistence failure, say — are left for the
     * screen's own message; a form that highlights nothing after a failed save is the
     * thing to avoid, not one that also shows a banner.
     */
    fun withErrors(errors: NonEmptyList<DomainError>): CarFormFields =
        errors.fold(this) { fields, error ->
            when (error) {
                DomainError.BlankMake -> fields.copy(make = fields.make.fail(UiText(Res.string.gr_error_field_make)))
                DomainError.BlankModel -> fields.copy(model = fields.model.fail(UiText(Res.string.gr_error_field_model)))
                DomainError.MissingYear, is DomainError.YearOutOfRange ->
                    fields.copy(year = fields.year.fail(UiText(Res.string.gr_error_field_year)))

                DomainError.MissingFuelType -> fields.copy(fuel = fields.fuel.fail(UiText(Res.string.gr_error_field_fuel)))
                else -> fields
            }
        }
}

/**
 * What the pickers offer. [models] depend on the chosen make, so they arrive separately
 * from the rest of the catalog and are empty until a brand is picked.
 */
@Immutable
internal data class CarFormOptions(
    val makes: List<String> = emptyList(),
    val popularMakes: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val fuelTypes: List<FuelType> = emptyList(),
    val models: List<CarModel> = emptyList(),
) {
    fun withCatalog(catalog: VehicleCatalogSnapshot): CarFormOptions = copy(
        makes = catalog.makes,
        popularMakes = catalog.popularMakes,
        years = catalog.years,
        fuelTypes = catalog.fuelTypes,
    )
}
