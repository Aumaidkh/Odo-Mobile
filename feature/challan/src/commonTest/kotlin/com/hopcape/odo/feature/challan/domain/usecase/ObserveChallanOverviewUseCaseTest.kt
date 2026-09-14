package com.hopcape.odo.feature.challan.domain.usecase

import com.hopcape.odo.core.domain.challan.model.ChallanStatus
import com.hopcape.odo.feature.challan.FakeChallanRepository
import com.hopcape.odo.feature.challan.FixedClock
import com.hopcape.odo.feature.challan.TEST_REG
import com.hopcape.odo.feature.challan.challan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ObserveChallanOverviewUseCaseTest {

    private val now = Instant.parse("2026-08-22T12:00:00Z")

    private fun useCase(repo: FakeChallanRepository) =
        ObserveChallanOverviewUseCase(challans = repo, clock = FixedClock(now))

    @Test
    fun courtCasesAreSplitOut_andNeverCounted() = runTest {
        val repo = FakeChallanRepository(
            challans = listOf(
                challan(id = "A", amountPaise = 1_000_00),
                challan(id = "B", amountPaise = 500_00),
                challan(
                    id = "C",
                    status = ChallanStatus.IN_COURT,
                    amountPaise = 5_000_00,
                    courtName = "Shivajinagar, Pune",
                    nextHearingOn = LocalDate(2026, 9, 4),
                ),
            ),
        )
        val overview = useCase(repo)(TEST_REG).first()

        assertEquals(2, overview.payable.size)
        assertEquals(1, overview.courtCases.size)
        // The court case's Rs. 5,000 must not inflate what can actually be paid online.
        assertEquals(1_500_00, overview.payableTotal.paise)
    }

    @Test
    fun paidChallansFromThisYear_becomeTheClearedLine() = runTest {
        val repo = FakeChallanRepository(
            challans = listOf(
                challan(id = "A", status = ChallanStatus.PAID, amountPaise = 1_000_00, issuedOn = LocalDate(2026, 3, 1)),
                challan(id = "B", status = ChallanStatus.PAID, amountPaise = 1_500_00, issuedOn = LocalDate(2026, 5, 1)),
                // Last year's cleared challan is history, not this year's line.
                challan(id = "C", status = ChallanStatus.PAID, amountPaise = 9_000_00, issuedOn = LocalDate(2025, 5, 1)),
            ),
        )
        val overview = useCase(repo)(TEST_REG).first()

        assertTrue(overview.isClean)
        assertEquals(2, overview.clearedThisYearCount)
        assertEquals(2_500_00, overview.clearedThisYearTotal.paise)
    }

    @Test
    fun anEmptyCache_isCleanWithNothingCleared() = runTest {
        val overview = useCase(FakeChallanRepository())(TEST_REG).first()

        assertTrue(overview.isClean)
        assertEquals(0, overview.clearedThisYearCount)
        assertNull(overview.lastCheckedAt)
    }
}
