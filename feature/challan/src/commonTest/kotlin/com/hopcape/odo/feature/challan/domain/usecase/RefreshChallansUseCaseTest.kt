package com.hopcape.odo.feature.challan.domain.usecase

import com.hopcape.odo.feature.challan.FakeChallanRepository
import com.hopcape.odo.feature.challan.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class RefreshChallansUseCaseTest {

    private val now = Instant.parse("2026-08-22T12:00:00Z")
    private val useCase = RefreshChallansUseCase(challans = FakeChallanRepository(), clock = FixedClock(now))

    @Test
    fun neverChecked_isStale() {
        assertTrue(useCase.isStale(null))
    }

    @Test
    fun checkedYesterday_isFresh() {
        assertFalse(useCase.isStale(now - 1.days))
    }

    @Test
    fun checkedOverAWeekAgo_isStale() {
        assertTrue(useCase.isStale(now - 8.days))
    }

    @Test
    fun nextCheck_countsDownFromAWeek() {
        assertEquals(6, useCase.daysUntilNextCheck(now - 1.days))
        assertEquals(0, useCase.daysUntilNextCheck(now - 9.days))
        assertEquals(0, useCase.daysUntilNextCheck(null))
        // A check six days and change ago still owes the owner the "in 1 day" honesty.
        assertEquals(1, useCase.daysUntilNextCheck(now - 6.days - 13.hours))
    }
}
