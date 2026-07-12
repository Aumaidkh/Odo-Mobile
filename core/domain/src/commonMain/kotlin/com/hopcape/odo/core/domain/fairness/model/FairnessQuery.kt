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
)

/** One line to benchmark — its [category] keys the city average; [label] is for display. */
data class FairnessQueryItem(
    val label: String,
    val category: ServiceCategory?,
    val amount: Amount,
)
