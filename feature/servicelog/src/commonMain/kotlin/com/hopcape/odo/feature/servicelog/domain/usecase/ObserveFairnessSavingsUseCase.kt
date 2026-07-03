package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessSavings
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Streams the ledger's "Saved so far · N overcharges caught" — the sum of every
 * [FairnessVerdict.Over] across the car's **line items** (each line carries the category
 * fairness compares). Entries without a priced breakdown don't contribute (a single
 * category can't be attributed to a multi-item total).
 */
internal class ObserveFairnessSavingsUseCase(
    private val logs: ServiceLogRepository,
    private val fairness: FairnessRepository,
) {
    operator fun invoke(carId: CarId, city: String): Flow<FairnessSavings> =
        logs.observe(carId).map { entries -> savings(entries, city) }

    private suspend fun savings(entries: List<ServiceLogEntry>, city: String): FairnessSavings {
        var overchargeTotal = Amount.ZERO
        var count = 0
        for (entry in entries) {
            for (line in entry.lineItems) {
                val estimate = fairness.estimate(line.category, city) ?: continue
                val verdict = FairnessVerdict.of(line.amount, estimate)
                if (verdict is FairnessVerdict.Over) {
                    overchargeTotal += verdict.by // Amount arithmetic, never raw paise
                    count++
                }
            }
        }
        return if (count == 0) FairnessSavings.NONE else FairnessSavings(overchargeTotal, count)
    }
}
