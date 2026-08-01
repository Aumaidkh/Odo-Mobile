package com.hopcape.odo.feature.healthscore.domain.usecase

import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecordHealthScoreUseCaseTest {

    private val now = Instant.parse("2026-08-01T10:00:00Z")

    private fun useCase(snapshots: FakeHealthScoreRepository) = RecordHealthScoreUseCase(
        snapshots = snapshots,
        owners = CurrentOwnerProvider { TEST_OWNER },
        ids = SequentialIds(),
        clock = FixedClock(now),
    )

    @Test
    fun aFirstScoreIsAlwaysStored() = runTest {
        val snapshots = FakeHealthScoreRepository()

        val result = useCase(snapshots).invoke(TEST_CAR, testScore())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(1, snapshots.recorded.size)
        val stored = snapshots.recorded.single()
        assertEquals("snap-0", stored.id.value)
        assertEquals(TEST_CAR, stored.carId)
        assertEquals(TEST_OWNER, stored.ownerId)
        assertEquals(now, stored.computedAt)
        assertEquals(74, stored.score.total)
    }

    @Test
    fun aScoreThatHasNotMovedIsNotStoredAgain() = runTest {
        val snapshots = FakeHealthScoreRepository(mutableListOf(testSnapshot(score = testScore())))

        val result = useCase(snapshots).invoke(TEST_CAR, testScore())

        assertNull(result.getOrNull(), "nothing was written, so there is no snapshot to return")
        assertTrue(snapshots.recorded.isEmpty())
    }

    @Test
    fun aScoreThatMovedIsStored() = runTest {
        val snapshots = FakeHealthScoreRepository(mutableListOf(testSnapshot(score = testScore())))

        useCase(snapshots).invoke(TEST_CAR, testScore(maintenance = 30))

        assertEquals(1, snapshots.recorded.size)
        assertEquals(76, snapshots.recorded.single().score.total)
    }

    @Test
    fun aBreakdownThatMovedIsStoredEvenWhenTheTotalDidNot() = runTest {
        val snapshots = FakeHealthScoreRepository(mutableListOf(testSnapshot(score = testScore())))

        // 28/24 becomes 24/28 — the same 74, a different car.
        useCase(snapshots).invoke(TEST_CAR, testScore(maintenance = 24, documentation = 28))

        assertEquals(1, snapshots.recorded.size)
    }

    @Test
    fun itComparesAgainstTheLatestSnapshotNotAnOlderOne() = runTest {
        val snapshots = FakeHealthScoreRepository(
            mutableListOf(
                testSnapshot(id = "older", score = testScore(maintenance = 10), computedAt = "2026-05-01T10:00:00Z"),
                testSnapshot(id = "newer", score = testScore(), computedAt = "2026-07-01T10:00:00Z"),
            ),
        )

        val result = useCase(snapshots).invoke(TEST_CAR, testScore())

        assertNull(result.getOrNull())
        assertTrue(snapshots.recorded.isEmpty())
    }

    @Test
    fun aFailedWriteIsReportedRatherThanThrown() = runTest {
        val snapshots = FakeHealthScoreRepository(failing = true)

        val result = useCase(snapshots).invoke(TEST_CAR, testScore())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }
}
