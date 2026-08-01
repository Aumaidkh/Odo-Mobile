package com.hopcape.odo.core.domain.health.model

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlin.jvm.JvmInline
import kotlin.time.Instant

/**
 * Typed identity for a [HealthSnapshot]. A `value class` so it can never be confused with
 * a car id, a service-log id, or a raw String.
 */
@JvmInline
value class HealthSnapshotId(val value: String) {
    companion object {
        /** Mint a fresh, client-side id (offline/optimistic insert). */
        fun new(ids: IdGenerator): HealthSnapshotId = HealthSnapshotId(ids.newId())
    }
}

/**
 * A health score as it stood at one moment — what gets stored, and the only way to say
 * how the score has moved.
 *
 * The score itself is computed on read and never persisted for display. This is kept for
 * a different reason: comparison. "Up 6 points this month" needs the number from a month
 * ago, and recomputing it from today's data cannot produce it — a policy uploaded today
 * with last year's start date would score backwards, so adding it would show a gain of
 * zero. The same history is what the PRD's "score dropped to 68 — see what changed" push
 * and the timeline's score events read.
 *
 * Append-only, like `health_scores` on the server (DB_SCHEMA §5.8): a snapshot is a record
 * of what was true, so it is never edited.
 */
data class HealthSnapshot(
    val id: HealthSnapshotId,
    val carId: CarId,
    val ownerId: OwnerId,
    val score: HealthScore,
    val computedAt: Instant,
    /**
     * Which version of the scoring rules produced [score] —
     * `HealthScoreCalculator.RULES_VERSION` as it stood when the snapshot was taken.
     *
     * Two snapshots are only comparable when this matches. A release that changes the point
     * rules moves every car's score without anything about the car changing, and a timeline
     * saying "rose 9 points" on the day of that release would be describing the release, not
     * the car.
     */
    val algoVersion: String,
)
