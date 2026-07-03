package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.shared.Amount

/**
 * One fairness verdict for a whole entry — the ledger card badge and the detail
 * "fairness check". Only **Verified** (bill-backed) entries are judged; a self-reported
 * entry has no bill to trust, so the UI prompts "add a bill" instead of a verdict.
 *
 * From line items when present ([FairnessVerdict.Over] if any line is over, its amounts
 * summed); otherwise from the entry's single category. `null` when there's no city, no
 * benchmark, or nothing comparable.
 */
internal class ResolveEntryFairnessUseCase(
    private val fairness: FairnessRepository,
) {
    suspend operator fun invoke(entry: ServiceLogEntry, city: String?): FairnessVerdict? {
        if (city == null || entry.verification != VerificationStatus.VERIFIED) return null

        val lines = entry.lineItems
        if (lines.isNotEmpty()) {
            val verdicts = lines.map { verdictFor(it.category, it.amount, city) }
            val overBy = verdicts.filterIsInstance<FairnessVerdict.Over>().fold(Amount.ZERO) { acc, v -> acc + v.by }
            return when {
                verdicts.any { it is FairnessVerdict.Over } -> FairnessVerdict.Over(overBy)
                verdicts.any { it is FairnessVerdict.Fair } -> FairnessVerdict.Fair
                else -> verdicts.filterIsInstance<FairnessVerdict.LowConfidence>().firstOrNull()
            }
        }
        val category = entry.categories.singleOrNull() ?: return null
        return verdictFor(category, entry.totalAmount, city)
    }

    private suspend fun verdictFor(category: ServiceCategory, amount: Amount, city: String): FairnessVerdict? {
        val estimate = fairness.estimate(category, city) ?: return null
        return FairnessVerdict.of(amount, estimate)
    }
}
