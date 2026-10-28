package com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.Loadable

/**
 * The v2 setup steps. [position] counts first run as the progress bar draws it: Model,
 * Year and fuel, then the value screen.
 */
internal enum class SetupV2Step(val position: Int) {
    MODEL(1),
    YEAR_FUEL(2),
    ;

    companion object {
        const val TOTAL: Int = 3
    }
}

/** The v2 catalog: the make's models and the year and fuel choices. */
@Immutable
internal data class SetupV2Catalog(
    val makes: List<String>,
    /** Every model and trim, for search. */
    val models: List<CarModel>,
    /** Newest first. */
    val years: List<Int>,
    val fuels: List<FuelType>,
) {
    /** The tiles on the Model step: models without a trim, in catalog order. */
    val baseModels: List<CarModel>
        get() = models.filter { it.variant == null }.ifEmpty {
            models.map { CarModel(it.name) }.distinctBy { it.name }
        }

    /** The year chips: the newest [YEAR_CHIPS]. */
    val recentYears: List<Int> get() = years.take(YEAR_CHIPS)

    /** Everything the "Older" wheel offers. */
    val yearRange: IntRange
        get() = if (years.isEmpty()) DEFAULT_YEARS else years.min()..years.max()

    companion object {
        const val YEAR_CHIPS = 8
        val DEFAULT_YEARS: IntRange = 1990..2026
    }
}

/** Everything the v2 setup renders. Nothing is preselected. */
@Immutable
internal data class SetupV2UiState(
    val make: String,
    /** The make's logo; null shows its monogram. */
    val makeLogoUrl: String? = null,
    val step: SetupV2Step = SetupV2Step.MODEL,
    val catalog: Loadable<SetupV2Catalog> = Loadable.Loading,
    val model: CarModel? = null,
    val year: Int? = null,
    val fuel: FuelType? = null,
    val saving: Boolean = false,
    /** The full model list, opened from "Can't find your car?". */
    val searchOpen: Boolean = false,
    /** The year wheel behind "Older". */
    val olderOpen: Boolean = false,
) {
    /** "Save my car" is live only with a year and a fuel, and never while a save runs. */
    val canSave: Boolean get() = model != null && year != null && fuel != null && !saving
}

internal sealed interface SetupV2Event {
    /** [fromSearch] is true when the model came from the full list rather than the grid. */
    data class ModelChosen(val model: CarModel, val fromSearch: Boolean = false) : SetupV2Event
    data object ModelSearchOpened : SetupV2Event
    data object ModelSearchDismissed : SetupV2Event

    /** [fromOlder] is true when the year came from the "Older" wheel. */
    data class YearChosen(val year: Int, val fromOlder: Boolean = false) : SetupV2Event
    data object OlderYearsOpened : SetupV2Event
    data object OlderYearsDismissed : SetupV2Event
    data class FuelChosen(val fuel: FuelType) : SetupV2Event
    data object SaveClicked : SetupV2Event
    data object CatalogRetried : SetupV2Event

    /** "Change" on the Model step: back to the make grid. */
    data object ChangeMakeClicked : SetupV2Event
    data object BackClicked : SetupV2Event
}

internal sealed interface SetupV2Effect {
    /** Leave setup, back to Welcome. */
    data object NavigateBack : SetupV2Effect
    data class SaveFailed(val message: UiText) : SetupV2Effect
    data object ShowValue : SetupV2Effect
}
