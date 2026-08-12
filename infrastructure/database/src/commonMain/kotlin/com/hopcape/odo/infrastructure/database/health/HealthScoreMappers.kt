package com.hopcape.odo.infrastructure.database.health

import com.hopcape.odo.infrastructure.database.db.Health_scores
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlin.time.Instant

/**
 * DB row → domain. The boundary where a generated [Health_scores] row becomes a
 * [HealthSnapshot]; the domain never sees the row type.
 *
 * The stored `score` column is deliberately *not* read: the total is derived from the four
 * component points, so a row whose columns disagree with its total resolves to the
 * breakdown rather than to a headline nothing adds up to. The column stays because the
 * server's schema has it and the sync push needs a value to send.
 *
 * `algo_version` travels with the snapshot, because two scores are only comparable when it
 * matches: the timeline drops a move whose two snapshots were produced by different rules.
 *
 * The sync columns are read and ignored: they exist for the engine, and letting them reach
 * the domain is what the layering forbids.
 */
internal fun Health_scores.toDomain(): HealthSnapshot = HealthSnapshot(
    id = HealthSnapshotId(id),
    carId = CarId(car_id),
    ownerId = OwnerId(owner_id),
    score = HealthScore(
        factors = listOf(
            HealthFactor.of(HealthFactorKind.MAINTENANCE, maintenance_pts.toInt()),
            HealthFactor.of(HealthFactorKind.DOCUMENTATION, documentation_pts.toInt()),
            HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, cost_efficiency_pts.toInt()),
            HealthFactor.of(HealthFactorKind.HISTORY, history_pts.toInt()),
        ),
    ),
    computedAt = Instant.parse(computed_at),
    algoVersion = algo_version,
)

/** A factor's earned points for the write, or 0 for a kind the score somehow lacks. */
internal fun HealthScore.pointsFor(kind: HealthFactorKind): Long =
    (factorFor(kind)?.earned ?: 0).toLong()
