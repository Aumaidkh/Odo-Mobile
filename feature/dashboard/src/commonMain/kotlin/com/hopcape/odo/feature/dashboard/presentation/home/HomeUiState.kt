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
import com.hopcape.odo.feature.dashboard.domain.model.TankStatus

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
    /**
     * Whether to offer automatic fuel logging.
     *
     * Kept beside [content] rather than inside it: it comes from a device-local setting that
     * has nothing to do with the car, and folding it into the snapshot would put it behind the
     * same failed read that hides the dashboard.
     *
     * True only while the feature is available and the owner has not already turned it on.
     * Buried three screens deep in settings, it is a feature nobody discovers; shown after
     * they have it, it is an advert for something they already own.
     */
    val offerAutoDetect: Boolean = false,

    /**
     * Whether the SCAN coach mark is up (#228) — granted by the `ShowcaseArbiter` when
     * this is a first-run device with a car and nothing logged, held until the owner
     * answers it. Beside [content] for the same reason as the offers: it is device
     * state, not car state.
     */
    val scanShowcase: Boolean = false,

    /** Whether the health-score coach mark is up (#232) — granted on the first scored dashboard. */
    val healthShowcase: Boolean = false,

    /**
     * Whether the reminders-bell coach mark is up — granted the first time the bell shows for
     * a car, since the bell is the only door into Reminders and carries no badge of its own.
     */
    val remindersShowcase: Boolean = false,

    /**
     * Whether the owner's plan is Pro — read only to pick the health coach mark's copy.
     * The epic's rule: a hook pointing at a gated feature says so in its own words (a
     * free owner is told the breakdown is included with Pro), and a Pro owner never sees
     * a plan mentioned.
     */
    val proPlan: Boolean = false,

    /**
     * Whether to pitch the auto odometer.
     *
     * Same reasoning as [offerAutoDetect]: enrollment lives behind the garage card, and a
     * feature discoverable from one slot on one tab is a feature most owners never meet.
     * True while the feature is available and not set up; gone the moment a bond exists
     * and tracking is on, so it is an offer rather than an advert. The card opens the
     * education screen — the same entry the garage card uses — never a permission.
     */
    val offerAutoOdometer: Boolean = false,

)

/** A loaded dashboard. */
/** Blanks the score's trend line, leaving everything else — see `ProFeature.SCORE_HISTORY`. */
internal fun HomeUiState.withoutScoreHistory(): HomeUiState = when (val c = content) {
    is Loadable.Ready -> copy(content = Loadable.Ready(c.value.copy(scoreDelta = null)))
    else -> this
}

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
    /**
     * The fuel card's data: distance since the last fill, what that fill was, and how far
     * this car usually goes between them.
     */
    val tank: TankStatus = TankStatus.Empty,
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
