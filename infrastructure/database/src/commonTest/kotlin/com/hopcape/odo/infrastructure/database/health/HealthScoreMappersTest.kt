package com.hopcape.odo.infrastructure.database.health

import com.hopcape.odo.infrastructure.database.db.Health_scores
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HealthScoreMappersTest {

    private fun row(
        maintenance: Long = 28,
        documentation: Long = 24,
        cost: Long = 14,
        history: Long = 8,
        score: Long = 74,
    ) = Health_scores(
        id = "snap-1",
        car_id = "car-1",
        owner_id = "owner-1",
        score = score,
        maintenance_pts = maintenance,
        documentation_pts = documentation,
        cost_efficiency_pts = cost,
        history_pts = history,
        algo_version = "rule-v1",
        computed_at = "2026-07-01T09:30:00Z",
        created_at = "2026-07-01T09:30:00Z",
        updated_at = "2026-07-01T09:30:00Z",
        deleted_at = null,
        remote_version = null,
        sync_status = SyncStatus.PENDING.name,
    )

    @Test
    fun toDomain_mapsEveryField() {
        val snapshot = row().toDomain()

        assertEquals("snap-1", snapshot.id.value)
        assertEquals("car-1", snapshot.carId.value)
        assertEquals("owner-1", snapshot.ownerId.value)
        assertEquals(Instant.parse("2026-07-01T09:30:00Z"), snapshot.computedAt)
        assertEquals(74, snapshot.score.total)
        assertEquals(HealthBand.GOOD, snapshot.score.band)
    }

    @Test
    fun toDomain_rebuildsTheFourFactors() {
        val factors = row().toDomain().score.factors

        assertEquals(HealthFactorKind.entries, factors.map { it.kind })
        assertEquals(listOf(28, 24, 14, 8), factors.map { it.earned })
        assertEquals(listOf(35, 30, 20, 15), factors.map { it.max })
    }

    @Test
    fun toDomain_trustsTheBreakdownOverTheStoredTotal() {
        // A row whose `score` column disagrees with its components resolves to the
        // components, so the headline always adds up to the breakdown beside it.
        val snapshot = row(score = 99).toDomain()

        assertEquals(74, snapshot.score.total)
    }

    @Test
    fun toDomain_clampsAComponentThatIsOutOfRange() {
        // A newer build's rules, or a corrupt row: the factor cannot exceed its weight.
        val snapshot = row(maintenance = 99).toDomain()

        assertEquals(35, snapshot.score.factorFor(HealthFactorKind.MAINTENANCE)?.earned)
    }

    @Test
    fun pointsFor_readsEachFactorBackForTheWrite() {
        val score = row().toDomain().score

        assertEquals(28L, score.pointsFor(HealthFactorKind.MAINTENANCE))
        assertEquals(24L, score.pointsFor(HealthFactorKind.DOCUMENTATION))
        assertEquals(14L, score.pointsFor(HealthFactorKind.COST_EFFICIENCY))
        assertEquals(8L, score.pointsFor(HealthFactorKind.HISTORY))
    }
}
