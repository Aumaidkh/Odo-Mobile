package com.hopcape.odo.feature.healthscore.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Clock

/**
 * Keep a record of a score that has moved.
 *
 * Called after the score is computed, which is the only moment it is known. Nothing else
 * writes here: the score is never stored to be displayed, only to be compared against
 * later ("up 6 points this month", and the PRD's "your score dropped to 68" push).
 *
 * **Only a score that actually moved is stored.** Recomputing happens on every emission —
 * every logged service, every screen visit — and storing each one would fill the history
 * with identical rows, leaving the delta to subtract a number from itself and the "what
 * changed" push with nothing to point at.
 *
 * The whole breakdown is compared, not just the total: 30 maintenance points swapped for
 * 30 documentation points is the same headline and a different car.
 */
internal class RecordHealthScoreUseCase(
    private val snapshots: HealthScoreRepository,
    private val owners: CurrentOwnerProvider,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    /**
     * @return the stored snapshot, or `null` when the score had not moved and nothing was
     *   written. A failure to store is reported but is never the caller's problem to
     *   solve — the score on screen is correct either way.
     */
    suspend operator fun invoke(carId: CarId, score: HealthScore): Either<DomainError, HealthSnapshot?> {
        if (snapshots.latest(carId)?.score == score) return null.right()

        return snapshots.record(
            HealthSnapshot(
                id = HealthSnapshotId.new(ids),
                carId = carId,
                ownerId = owners.currentOwnerId(),
                score = score,
                computedAt = clock.now(),
            ),
        )
    }
}
