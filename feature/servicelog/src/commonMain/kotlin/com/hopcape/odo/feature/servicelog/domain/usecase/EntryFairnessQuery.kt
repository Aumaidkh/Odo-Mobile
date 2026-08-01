package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry

/**
 * The entry as lines fairness can benchmark, or `null` when it has nothing comparable.
 *
 * A single total carrying *several* category tags is deliberately not benchmarked: there is
 * no way to know which share of one amount belongs to which job, and splitting it would turn
 * a guess into a confident-looking verdict.
 *
 * Shared by the two callers that need the same lines — the check that stores a verdict on the
 * entry, and the "Check fairness" action that opens the report screen — so the screen can
 * never benchmark something different from what was recorded.
 */
internal fun ServiceLogEntry.fairnessItems(): List<FairnessQueryItem>? =
    if (lineItems.isNotEmpty()) {
        lineItems.map { FairnessQueryItem(label = it.label, category = it.category, amount = it.amount) }
    } else {
        val category = categories.singleOrNull() ?: return null
        listOf(FairnessQueryItem(label = null, category = category, amount = totalAmount))
    }
