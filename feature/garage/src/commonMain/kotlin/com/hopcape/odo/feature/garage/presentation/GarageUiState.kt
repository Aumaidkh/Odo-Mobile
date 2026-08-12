package com.hopcape.odo.feature.garage.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.garage.domain.model.AutoOdometerCardState
import com.hopcape.odo.feature.garage.domain.model.GarageDocument
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet
import com.hopcape.odo.feature.garage.domain.model.ServiceHistoryEntry
import com.hopcape.odo.feature.garage.domain.model.matching
import com.hopcape.odo.feature.garage.domain.model.totalSpent
import com.hopcape.odo.feature.garage.presentation.state.Loadable

/**
 * Display state for the garage's home base.
 *
 * [content] carries the car, its papers and its history as one value, because they were
 * read as one: a card showing a reading the history below it has already passed would be
 * worse than a slow screen. The chosen chip sits outside it — it is the owner's choice, not
 * something the read can change.
 */
@Immutable
internal data class GarageUiState(
    val content: Loadable<GarageContent> = Loadable.Loading,
    val filter: ServiceFacet = ServiceFacet.ALL,
) {
    /** The entries under the chosen chip — the list the history section renders. */
    val filteredHistory: List<ServiceHistoryEntry>
        get() = (content as? Loadable.Ready)?.value?.history?.matching(filter).orEmpty()

    /** What the filtered entries cost together, for the section's summary line. */
    val filteredTotal: Amount get() = filteredHistory.totalSpent()
}

/**
 * The garage at one instant. [car] is `null` when the owner has no car yet, which is the
 * home base's "add your car" state rather than a failure.
 */
@Immutable
internal data class GarageContent(
    val car: Car?,
    /** The newest reading on record, logs included — what the car card shows. */
    val odometer: Distance? = null,
    val documents: List<GarageDocument>,
    val history: List<ServiceHistoryEntry>,
    /** The auto-odometer pitch card / status tile slot — [AutoOdometerCardState.Hidden] by default. */
    val autoOdometerCard: AutoOdometerCardState = AutoOdometerCardState.Hidden,
)
