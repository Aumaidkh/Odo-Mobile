package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.analysis.SavingsCalculator
import com.hopcape.odo.core.domain.fairness.model.FairnessSavings
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceRecordSummary
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Everything the service-log list renders, from **one** read of the car's entries: the rows
 * themselves, the header stats, and the filter-chip counts.
 *
 * One use case rather than three, because the alternative is three flows over the same
 * table whose emissions interleave — a header that says "12 services" above 11 rows, or a
 * "2 flagged" chip that filters to three. Header and rows are one fact about the car and
 * must be read as one.
 *
 * Every number here is derived from the entries in memory: the fairness verdicts were
 * stored when they were taken ([ServiceLogEntry.fairness]), so a list that scrolls, filters
 * or re-emits never re-queries the benchmark pool.
 */
internal class ObserveServiceLogFeedUseCase(
    private val logs: ServiceLogRepository,
) {
    operator fun invoke(carId: CarId): Flow<ServiceLogFeed> = logs.observe(carId).map(::feedOf)

    private fun feedOf(entries: List<ServiceLogEntry>): ServiceLogFeed = ServiceLogFeed(
        entries = entries,
        summary = ServiceRecordSummary.of(entries),
        // Shared with Home's stat card, so the two can never quote different totals.
        savings = SavingsCalculator.of(entries),
    )
}

/**
 * The list's whole world at one instant. [savings] powers the ledger's "Saved so far · N
 * overcharges caught"; [summary] powers both the ledger's spend header and the timeline's
 * record ring, so the two directions can never disagree about the same car.
 */
internal data class ServiceLogFeed(
    val entries: List<ServiceLogEntry>,
    val summary: ServiceRecordSummary,
    val savings: FairnessSavings,
) {
    /** The Flagged chip's count — entries whose stored verdict came back over. */
    val flaggedCount: Int get() = entries.count { it.fairness?.outcome is FairnessOutcome.Over }

    /** The Verified chip's count, straight off the record summary. */
    val verifiedCount: Int get() = summary.verifiedCount
}
