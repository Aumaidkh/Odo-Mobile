package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount

/**
 * Judges a paid [amount] for a [category] against the city benchmark. Returns `null`
 * when no benchmark exists yet (UI shows nothing), a [FairnessVerdict.LowConfidence]
 * when the sample is too small (guardrail: no false precision), otherwise Fair/Over/Under.
 */
internal class CheckFairnessUseCase(
    private val fairness: FairnessRepository,
) {
    suspend operator fun invoke(category: ServiceCategory, amount: Amount, city: String): FairnessVerdict? {
        val estimate = fairness.estimates(setOf(category), city)[category] ?: return null
        return FairnessVerdict.of(amount, estimate)
    }
}
