package com.hopcape.odo.core.domain.health.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Instant

/**
 * Port for storing and reading a car's health-score history. The implementation lives in
 * `:core:data` (local DB as source of truth); the domain stays ignorant of it.
 *
 * Only history lives here. Today's score is computed from today's data by
 * [HealthScoreCalculator][com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator]
 * and is never read back from a table — a stored current score would go stale the moment a
 * document expired, with nothing to notice it had.
 */
interface HealthScoreRepository {

    /**
     * The most recent snapshot for the car, or `null` if none was ever taken.
     *
     * Read before writing: a snapshot is only worth storing when the score has actually
     * moved, otherwise the history fills with identical rows and the "what changed" push
     * has nothing to point at.
     */
    suspend fun latest(carId: CarId): HealthSnapshot?

    /**
     * The most recent snapshot taken on or before [instant] — the baseline the month
     * delta is measured against.
     *
     * `null` when the car has no score that old, which is the screen's cue to hide the
     * delta rather than compare against a number from last week and call it a month.
     */
    suspend fun latestOnOrBefore(carId: CarId, instant: Instant): HealthSnapshot?

    /**
     * Append [snapshot] to the car's history. Append-only: a snapshot records what was
     * true at a moment, so there is no update and no delete.
     */
    suspend fun record(snapshot: HealthSnapshot): Either<DomainError, HealthSnapshot>
}
