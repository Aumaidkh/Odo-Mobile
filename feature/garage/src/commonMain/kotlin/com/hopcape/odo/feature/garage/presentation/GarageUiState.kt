package com.hopcape.odo.feature.garage.presentation

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.Vin
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.garage.domain.model.GarageDocument
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet
import com.hopcape.odo.feature.garage.domain.model.ServiceHistoryEntry
import com.hopcape.odo.feature.garage.domain.model.matching
import com.hopcape.odo.feature.garage.domain.model.totalSpent

/**
 * Garage render state — the car, its papers, and its history.
 *
 * ViewModel-ready: every field has a default, so the first emission is
 * `MutableStateFlow(GarageUiState())` — a loading garage with no car yet — and each later
 * emission is a `copy(...)` of what the aggregation use case delivered.
 *
 * Holds domain types, not display strings: [Car] with its typed value objects,
 * [GarageDocument] rows with their validity already resolved against today (the clock is
 * the ViewModel's, not the composable's), and [ServiceHistoryEntry] with [Amount] in
 * paise. Rupees, kilometres and dates become text in the UI layer and nowhere else.
 */
internal data class GarageUiState(
    val car: Car? = null,
    /** Not persisted yet — see [Vin]. Absent for every car until a column exists. */
    val vin: Vin? = null,
    val documents: List<GarageDocument> = emptyList(),
    val serviceHistory: List<ServiceHistoryEntry> = emptyList(),
    val filter: ServiceFacet = ServiceFacet.ALL,
    val isLoading: Boolean = true,
) {
    /** No car set up yet — the garage shows its "add your car" state instead of a home base. */
    val hasCar: Boolean get() = car != null

    /** The entries under the selected chip — the list the history section renders. */
    val filteredHistory: List<ServiceHistoryEntry> get() = serviceHistory.matching(filter)

    /** What the filtered entries cost together, for the section's summary line. */
    val filteredTotal: Amount get() = filteredHistory.totalSpent()
}
