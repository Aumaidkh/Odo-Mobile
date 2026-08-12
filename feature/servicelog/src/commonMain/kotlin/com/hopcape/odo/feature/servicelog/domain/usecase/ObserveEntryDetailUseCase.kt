package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceRecordSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One entry, **and what it is worth to the car's record** — everything the detail screen
 * shows about a single service.
 *
 * Read from the car's whole feed rather than from the entry alone, because the screen's
 * resale-proof claim ("+4 to record") is not a property of the entry: the record score is a
 * share of verified entries over history coverage, so the same bill is worth more on a thin
 * record than on a full one. Asking the repository for one row could never answer it.
 *
 * Emits `null` when the entry is absent — never written, or soft-deleted since it was opened.
 */
internal class ObserveEntryDetailUseCase(
    private val observeFeed: ObserveServiceLogFeedUseCase,
) {
    operator fun invoke(carId: CarId, id: ServiceLogId): Flow<ServiceEntryDetail?> =
        observeFeed(carId).map { feed -> feed.detailOf(id) }

    private fun ServiceLogFeed.detailOf(id: ServiceLogId): ServiceEntryDetail? {
        val entry = entries.firstOrNull { it.id == id } ?: return null
        return ServiceEntryDetail(entry = entry, recordScoreUplift = recordScoreUpliftOf(id))
    }

    /**
     * What the record score would lose if this entry were not there — the entry's actual
     * contribution, computed by the same rule that produced the score in the first place
     * ([ServiceRecordSummary]), so the two figures can never disagree.
     */
    private fun ServiceLogFeed.recordScoreUpliftOf(id: ServiceLogId): Int {
        val without = ServiceRecordSummary.of(entries.filterNot { it.id == id })
        return summary.score.value - without.score.value
    }
}

/** An entry together with the record context the detail screen judges it in. */
internal data class ServiceEntryDetail(
    val entry: ServiceLogEntry,
    /** Points this entry contributes to the car's 0–100 record score. */
    val recordScoreUplift: Int,
)
