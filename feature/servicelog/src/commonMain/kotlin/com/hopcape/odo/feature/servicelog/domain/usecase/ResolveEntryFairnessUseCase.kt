package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification

/**
 * The fairness check for one entry — the ledger card's badge and the detail screen's
 * per-line breakdown both read off the returned [FairnessReport].
 *
 * Only **Verified** (bill-backed) entries are judged. A self-reported entry has no bill to
 * trust, so the honest answer is no verdict at all (`null`) and the UI prompts for a bill
 * rather than grading an unproven number.
 *
 * The entry becomes a [FairnessQuery] — its priced lines when it has them, otherwise its
 * single category against the total — and the verdict math stays in [FairnessReport.of],
 * shared with every other fairness surface.
 */
internal class ResolveEntryFairnessUseCase(
    private val fairness: FairnessRepository,
) {
    suspend operator fun invoke(entry: ServiceLogEntry, city: String?): FairnessReport? {
        if (city == null || entry.verification != VerificationStatus.VERIFIED) return null

        val query = entry.toQuery(city) ?: return null
        return FairnessReport.of(query, fairness.estimates(query.categories, city))
    }
}

/**
 * The entry as something fairness can benchmark, or `null` when it has nothing comparable.
 *
 * A single total carrying *several* category tags is deliberately not benchmarked: there is
 * no way to know which share of one amount belongs to which job, and splitting it would
 * turn a guess into a confident-looking verdict.
 */
private fun ServiceLogEntry.toQuery(city: String): FairnessQuery? {
    val items = if (lineItems.isNotEmpty()) {
        lineItems.map { FairnessQueryItem(label = it.label, category = it.category, amount = it.amount) }
    } else {
        val category = categories.singleOrNull() ?: return null
        listOf(FairnessQueryItem(label = null, category = category, amount = totalAmount))
    }
    return FairnessQuery(city = city, items = items)
}
