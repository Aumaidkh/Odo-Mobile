package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.core.triptracker.VehicleBond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Setup-state matrix: reading count x bond presence x enabled x readiness. */
class ObserveSetupStateTest {

    @Test
    fun noReadingsNoBondDisabled_reportsEmptyState() = runTest {
        val serviceLogs = FakeServiceLogRepository(readings = emptyList())
        val bonds = FakeVehicleBondStore(initial = null)
        val tracker = FakeTripTracker(enabled = false)
        val preconditions = FakeTrackingPreconditions(READY.copy(fineLocation = false, backgroundLocation = false))
        val useCase = ObserveSetupState(serviceLogs, bonds, tracker, preconditions)

        val state = useCase(TEST_CAR).first()

        assertEquals(0, state.readingCount)
        assertNull(state.bond)
        assertFalse(state.enabled)
        assertFalse(state.readiness.canTrack)
    }

    @Test
    fun twoOrMoreReadingsNoBond_reportsEligibleForCardWithNoBond() = runTest {
        val serviceLogs = FakeServiceLogRepository(
            readings = listOf(
                testReading(LocalDate(2026, 1, 1), 10_000),
                testReading(LocalDate(2026, 2, 1), 10_500),
                testReading(LocalDate(2026, 3, 1), 11_000),
            ),
        )
        val bonds = FakeVehicleBondStore(initial = null)
        val tracker = FakeTripTracker(enabled = false)
        val preconditions = FakeTrackingPreconditions(READY)
        val useCase = ObserveSetupState(serviceLogs, bonds, tracker, preconditions)

        val state = useCase(TEST_CAR).first()

        assertEquals(3, state.readingCount)
        assertNull(state.bond)
        assertFalse(state.enabled)
    }

    @Test
    fun stereoBondEnabledAndReady_reportsFullySetUp() = runTest {
        val bond = VehicleBond(TEST_CAR, "AA:BB:CC:DD:EE:FF", TriggerMode.STEREO)
        val serviceLogs = FakeServiceLogRepository(readings = emptyList())
        val bonds = FakeVehicleBondStore(initial = bond)
        val tracker = FakeTripTracker(enabled = true)
        val preconditions = FakeTrackingPreconditions(READY)
        val useCase = ObserveSetupState(serviceLogs, bonds, tracker, preconditions)

        val state = useCase(TEST_CAR).first()

        assertEquals(bond, state.bond)
        assertTrue(state.enabled)
        assertTrue(state.readiness.canTrack)
    }

    @Test
    fun noStereoBond_bluetoothIdIsEmpty() = runTest {
        val bond = VehicleBond(TEST_CAR, "", TriggerMode.NO_STEREO)
        val serviceLogs = FakeServiceLogRepository(readings = emptyList())
        val bonds = FakeVehicleBondStore(initial = bond)
        val tracker = FakeTripTracker(enabled = true)
        val preconditions = FakeTrackingPreconditions(READY)
        val useCase = ObserveSetupState(serviceLogs, bonds, tracker, preconditions)

        val state = useCase(TEST_CAR).first()

        assertEquals(TriggerMode.NO_STEREO, state.bond?.triggerMode)
        assertEquals("", state.bond?.bluetoothId)
    }
}
