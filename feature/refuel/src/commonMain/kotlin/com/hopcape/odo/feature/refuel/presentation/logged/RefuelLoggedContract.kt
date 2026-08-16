package com.hopcape.odo.feature.refuel.presentation.logged

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.cost.model.FillEntrySource

/** What the owner did on the success screen. */
internal sealed interface RefuelLoggedEvent {

    data object DoneTapped : RefuelLoggedEvent

    data object ViewTimelineTapped : RefuelLoggedEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface RefuelLoggedEffect {

    /** Finished — leave the flow entirely rather than stepping back through the confirm. */
    data object Close : RefuelLoggedEffect

    data object OpenTimeline : RefuelLoggedEffect
}

/**
 * What was logged, and what the tank returned.
 *
 * Everything here is read back from the stored fill rather than carried from the confirm
 * step. The record is what the owner will see forever; showing them a summary built from the
 * form they just filled in would hide a write that silently stored something else.
 *
 * [mileage] is null far more often than not — the first fill has nothing to measure from,
 * and a short gap between two fills cannot support a figure. The screen leaves the line out
 * rather than hedging it.
 */
@Immutable
internal data class RefuelLoggedUiState(
    val loading: Boolean = true,
    val source: FillEntrySource = FillEntrySource.MANUAL,
    val stationName: String? = null,
    val quantityLabel: String = "",
    val rateLabel: String = "",
    val odometerKm: Int? = null,
    val mileage: UiText? = null,
    val mileageComparison: UiText? = null,
)
