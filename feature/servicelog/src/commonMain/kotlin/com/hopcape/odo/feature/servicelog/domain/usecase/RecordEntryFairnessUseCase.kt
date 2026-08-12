package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Clock

/**
 * Take the fairness verdict for an entry and **store** it on that entry.
 *
 * The write half of the fairness check. [ResolveEntryFairnessUseCase] works out the verdict;
 * this one freezes it with the moment it was taken, so the figure the owner was shown stays
 * the figure they see (see [FairnessSnapshot] — the city pool moves daily).
 *
 * Runs after [AttachBillPhotoUseCase], not inside it: benchmarking needs the server, and a
 * failure here must cost nothing more than a verdict the caller can ask for again later.
 * The entry stays verified either way.
 *
 * Takes the entry rather than an id because the caller has just written it — re-reading it
 * only to overwrite it would add a round trip and a race.
 */
internal class RecordEntryFairnessUseCase(
    private val logs: ServiceLogRepository,
    private val resolveFairness: ResolveEntryFairnessUseCase,
    private val currentCity: CurrentCityProvider,
    private val clock: Clock,
) {
    /**
     * Returns the entry with its verdict recorded, or **unchanged** when there is nothing
     * to record — no city, or no city average for anything on the entry. A snapshot exists
     * to freeze a comparison; there was none in those cases, and storing an empty one would
     * put a badge on screen with nothing behind it.
     *
     * A check that came back too thinly sampled *is* stored: it compared real prices, and
     * "only three bills so far" is a finding the owner should keep seeing.
     */
    suspend operator fun invoke(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> {
        val report = resolveFairness(entry, currentCity.currentCity())
            ?.takeIf { it.outcome != FairnessOutcome.NoBenchmark }
            ?: return entry.right()

        return logs.update(entry.withFairness(FairnessSnapshot(report, clock.now())))
    }
}
