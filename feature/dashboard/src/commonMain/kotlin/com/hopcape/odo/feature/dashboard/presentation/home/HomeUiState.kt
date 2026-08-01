package com.hopcape.odo.feature.dashboard.presentation.home

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.alerts.model.CarAttention
import com.hopcape.odo.core.domain.cost.model.CostTrend
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.insight.model.CarInsight
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.dashboard.domain.model.SetupProgress
import com.hopcape.odo.feature.dashboard.presentation.state.Loadable

/**
 * Home render state.
 *
 * Nothing here is a pre-formatted display string. The cards carry typed domain values —
 * [Amount] in paise, [Distance] in km, [CarAttention], [CarInsight] — and the composable
 * turns each into copy from `strings.xml`. Baking the copy in here would put product
 * wording in a state class and make the same sentence unreachable from a preview.
 */
@Immutable
internal data class HomeUiState(
    val content: Loadable<HomeContent> = Loadable.Loading,
)

/** A loaded dashboard. */
@Immutable
internal data class HomeContent(
    /** The owner's name for the greeting; empty falls back to a generic hello. */
    val userName: String = "",
    /** "Swift VXI" — empty before setup has stored a car. */
    val carName: String = "",
    /** The car's reading today, for the line under the greeting. */
    val odometer: Distance? = null,
    val score: Int = 0,
    val band: HealthBand = HealthBand.POOR,
    /** Points against the score from a month ago; `null` hides the line. */
    val scoreDelta: Int? = null,
    /** `null` when the car has not moved far enough this quarter to quote a rate. */
    val perKm: Amount? = null,
    /** `null` when either quarter has no rate to compare. */
    val costTrend: CostTrend? = null,
    val overchargeTotal: Amount = Amount.ZERO,
    val overchargesCaught: Int = 0,
    /** The one thing to act on; `null` renders the all-clear card. */
    val attention: CarAttention? = null,
    /** `null` hides the insight card rather than inventing something to say. */
    val insight: CarInsight? = null,
    /** The newest event on the car's feed; `null` hides the recent section. */
    val recent: ActivityEvent? = null,
    val setup: SetupProgress = SetupProgress(
        carAdded = false,
        billScanned = false,
        documentsFiled = false,
        hasServiceLogs = false,
    ),
    /**
     * Nothing logged or filed yet, so the checklist shows instead of the score. Decided in
     * the domain ([com.hopcape.odo.feature.dashboard.domain.model.HomeSnapshot.isNewUser])
     * and carried, not re-derived, so the screen and the score agree on who is new.
     */
    val isNewUser: Boolean = true,
) {
    /** Setup has never stored a car, so there is nothing truthful to say about one. */
    val hasNoCar: Boolean get() = !setup.carAdded
}
