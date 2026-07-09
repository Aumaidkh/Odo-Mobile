package com.hopcape.odo.feature.servicelog.presentation.list.components

import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogCardUiState
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogFairnessBadge

/** Coarse display tone for a fairness verdict — drives dot colour, pill tone, borders. */
internal enum class FairnessTone { GOOD, WARN, MUTED }

internal fun ServiceLogFairnessBadge.tone(): FairnessTone = when (this) {
    ServiceLogFairnessBadge.FairPrice, ServiceLogFairnessBadge.NotYetChecked -> FairnessTone.GOOD
    is ServiceLogFairnessBadge.Overcharged -> FairnessTone.WARN
    ServiceLogFairnessBadge.AddBillToVerify -> FairnessTone.MUTED
}

/** A card is "flagged" when its price is over the city average. */
internal val ServiceLogCardUiState.isFlagged: Boolean get() = fairness.tone() == FairnessTone.WARN
