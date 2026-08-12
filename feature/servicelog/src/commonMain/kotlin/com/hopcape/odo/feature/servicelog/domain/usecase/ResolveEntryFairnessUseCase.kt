package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
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
 * The entry becomes a [FairnessQuery] through [fairnessItems], and the verdict math stays in
 * [FairnessReport.of], shared with every other fairness surface.
 */
internal class ResolveEntryFairnessUseCase(
    private val fairness: FairnessRepository,
) {
    suspend operator fun invoke(entry: ServiceLogEntry, city: String?): FairnessReport? {
        if (city == null || entry.verification != VerificationStatus.VERIFIED) return null

        val items = entry.fairnessItems() ?: return null
        val query = FairnessQuery(city = city, items = items)
        return FairnessReport.of(query, fairness.estimates(query.categories, city))
    }
}
