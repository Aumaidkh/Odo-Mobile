package com.hopcape.odo.feature.onboarding.presentation.state

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * Everything the manual-entry pickers offer, fetched as one snapshot.
 *
 * Models are absent on purpose: they depend on which make the owner picks, so they are
 * fetched on selection and live on [CarDetailsState.models].
 */
@Immutable
internal data class CatalogOptions(
    val makes: List<String>,
    val popularMakes: List<String>,
    val years: IntRange,
    val fuelTypes: List<FuelType>,
) {
    companion object {
        /**
         * Model years to fall back on if the catalog answers with none (an unseeded DB).
         * A usable picker beats an empty one; the catalog's own range wins whenever it has
         * one, so this is a floor, not a policy.
         */
        val DEFAULT_YEARS: IntRange = 1990..2026
    }
}

/**
 * The manual route of the car step — the same step answered by hand when the plate lookup
 * misses. Four pickers plus the odometer, no free text: every value has to match the
 * catalog, because the fairness benchmarks are keyed on make/model/year/fuel.
 *
 * [models] is a plain list rather than a [Loadable]: it is a local read that arrives in
 * milliseconds, and the picker has no loading affordance to render the wait in. A failed
 * read leaves it empty, where the sheet's own "no models match" copy already says the
 * truth. The [catalog] snapshot *is* a [Loadable], because if that fails there is no form
 * to show at all — only a retry.
 */
@Immutable
internal data class CarDetailsState(
    val catalog: Loadable<CatalogOptions> = Loadable.Loading,
    val models: List<CarModel> = emptyList(),
    val make: FormField<String> = FormField(),
    val model: FormField<CarModel> = FormField(),
    val year: FormField<Int> = FormField(),
    val fuel: FormField<FuelType> = FormField(),
) {
    /** The loaded pickers' options, or `null` while the catalog is loading or failed. */
    val options: CatalogOptions? get() = catalog.valueOrNull

    /**
     * Whether all four pickers have been answered. The odometer is deliberately not part of
     * this: it belongs to the step rather than to either route, so
     * [OnboardingUiState.canContinue] is what combines the two.
     */
    val isAnswered: Boolean
        get() = !make.value.isNullOrBlank() &&
            model.value != null &&
            year.value != null &&
            fuel.value != null
}
