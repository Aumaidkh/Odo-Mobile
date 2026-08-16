package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.policy.ServiceDueStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/** Light coverage for the remaining F3 use cases beyond the plan's explicit test list. */
class RemainingUseCasesTest {

    private fun testEntry(id: String, servicedOn: LocalDate, km: Int): ServiceLogEntry = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = TEST_CAR,
        ownerId = TEST_OWNER,
        serviceDate = servicedOn,
        odometerKm = km,
        totalAmountPaise = 300_000,
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = null,
    )

    @Test
    fun enrollTriggerDevice_stereo_savesTheBondWithTheDeviceId() = runTest {
        val bonds = FakeVehicleBondStore()
        val enroll = EnrollTriggerDevice(bonds)

        enroll(TEST_CAR, "AA:BB:CC:DD:EE:FF", TriggerMode.STEREO)

        assertEquals(TEST_CAR, bonds.current?.carId)
        assertEquals("AA:BB:CC:DD:EE:FF", bonds.current?.bluetoothId)
        assertEquals(TriggerMode.STEREO, bonds.current?.triggerMode)
    }

    @Test
    fun enrollTriggerDevice_noStereo_savesAnEmptyBluetoothId() = runTest {
        val bonds = FakeVehicleBondStore()
        val enroll = EnrollTriggerDevice(bonds)

        enroll(TEST_CAR, "", TriggerMode.NO_STEREO)

        assertEquals("", bonds.current?.bluetoothId)
        assertEquals(TriggerMode.NO_STEREO, bonds.current?.triggerMode)
    }

    @Test
    fun completeSetup_enablesTracking_persistsTheToggle_andStartsIfConnected() = runTest {
        val tracker = FakeTripTracker(enabled = false)
        val settings = FakeAppSettingsRepository()
        val complete = CompleteSetup(tracker, settings)

        complete()

        assertEquals(true, tracker.enabledFlow.value)
        assertEquals(1, tracker.startIfConnectedCalls)
        // The persisted flag is what armFromPersistedState reads after a process death.
        assertEquals(true, settings.settings.value.trackerEnabled)
    }

    @Test
    fun deleteAllTripData_delegatesToTheRepositoryForTheGivenCar() = runTest {
        val trips = FakeTripRepository(
            initial = listOf(
                testTrip(
                    id = "t1",
                    startedAt = Instant.parse("2026-01-01T08:00:00Z"),
                    endedAt = Instant.parse("2026-01-01T09:00:00Z"),
                    distanceMeters = 10_000,
                ),
            ),
        )
        val delete = DeleteAllTripData(trips)

        val result = delete(TEST_CAR)

        assertEquals(true, result.isRight())
        assertEquals(listOf(TEST_CAR), trips.deleteAllCalls)
        assertEquals(emptyList(), trips.trips.value)
    }

    @Test
    fun observeServiceDueNudge_reusesTheSharedServiceIntervalPolicy() = runTest {
        // Never serviced -> the shared kernel's NeverServiced case, same as the reminders
        // feed's ObserveRemindersUseCase would report for this car.
        val serviceLogs = FakeServiceLogRepository(entries = emptyList(), readings = emptyList())
        val useCase = ObserveServiceDueNudge(
            serviceLogs,
            currentOdometerFrom(serviceLogs),
            FixedClock(Instant.parse("2026-08-07T00:00:00Z")),
            TimeZone.UTC,
        )

        assertIs<ServiceDueStatus.NeverServiced>(useCase(TEST_CAR).first())
    }

    @Test
    fun observeServiceDueNudge_readsTheTripAwareAggregate_notJustTheRawReading() = runTest {
        // A service at 40,000 km, nine months ago; the raw reading still says 40,000 km,
        // but the provider already folds a counted auto-trip on top of it that pushes the
        // car past both the interval's time and distance halves.
        val serviceLogs = FakeServiceLogRepository(
            entries = listOf(
                testEntry(id = "s1", servicedOn = LocalDate(2025, 11, 1), km = 40_000),
            ),
            readings = listOf(testReading(date = LocalDate(2025, 11, 1), km = 40_000)),
        )
        val useCase = ObserveServiceDueNudge(
            serviceLogs,
            FakeCurrentOdometerProvider(testReading(date = LocalDate(2026, 8, 6), km = 50_500).odometer),
            FixedClock(Instant.parse("2026-08-07T00:00:00Z")),
            TimeZone.UTC,
        )

        val status = assertIs<ServiceDueStatus.Overdue>(useCase(TEST_CAR).first())
        assertEquals(500, status.kmOverdue)
    }
}
