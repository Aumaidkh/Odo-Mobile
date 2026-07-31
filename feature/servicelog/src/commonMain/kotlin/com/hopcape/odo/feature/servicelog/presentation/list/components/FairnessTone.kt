package com.hopcape.odo.feature.servicelog.presentation.list.components

import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogCardUiState
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogFairnessBadge

/** Coarse display tone for a fairness verdict — drives dot colour, pill tone, borders. */
internal enum class FairnessTone { GOOD, WARN, MUTED }

internal fun ServiceLogFairnessBadge.tone(): FairnessTone = when (this) {
    // Verified is the good signal here, whether or not a verdict was reached: the tone
    // tracks trust in the entry, and only an overcharge is worth warning about.
    ServiceLogFairnessBadge.FairPrice,
    ServiceLogFairnessBadge.NotYetChecked,
    is ServiceLogFairnessBadge.NotEnoughData,
    -> FairnessTone.GOOD
    is ServiceLogFairnessBadge.Overcharged -> FairnessTone.WARN
    ServiceLogFairnessBadge.AddBillToVerify -> FairnessTone.MUTED
}

/** A card is "flagged" when its price is over the city average. */
internal val ServiceLogCardUiState.isFlagged: Boolean get() = fairness.tone() == FairnessTone.WARN
