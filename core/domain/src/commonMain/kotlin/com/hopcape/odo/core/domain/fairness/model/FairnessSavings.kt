package com.hopcape.odo.core.domain.fairness.model

import com.hopcape.odo.core.domain.shared.Amount

/**
 * Aggregate of the overcharges fairness has flagged across a car's log — powers the
 * ledger's "Saved so far · N overcharges caught" stat. [overchargeTotal] is the sum of
 * every [FairnessVerdict.Over] amount; [overchargesCaught] the count.
 */
data class FairnessSavings(
    val overchargeTotal: Amount,
    val overchargesCaught: Int,
) {
    companion object {
        val NONE = FairnessSavings(Amount.ZERO, 0)
    }
}
