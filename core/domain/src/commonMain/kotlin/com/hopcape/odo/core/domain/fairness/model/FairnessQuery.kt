package com.hopcape.odo.core.domain.fairness.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount

/**
 * The input to a fairness check — what was paid (per job) and where. Deliberately
 * minimal: enough for the [analysis][com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer]
 * to benchmark against city averages, nothing more. Any caller (a scanned bill, a single
 * price check, a logged entry) builds one of these.
 */
data class FairnessQuery(
    val city: String,
    val items: List<FairnessQueryItem>,
) {
    /** The categories this query needs benchmarks for — what to ask the repository. */
    val categories: Set<ServiceCategory> get() = items.mapNotNull { it.category }.toSet()
}

/**
 * One line to benchmark — its [category] keys the city average. [label] is the line as the
 * owner would recognise it ("Engine oil (5W-30)") and is nullable, because a manually
 * logged line often carries only a category; naming it is the UI's job, not the domain's.
 */
data class FairnessQueryItem(
    val label: String?,
    val category: ServiceCategory?,
    val amount: Amount,
)
