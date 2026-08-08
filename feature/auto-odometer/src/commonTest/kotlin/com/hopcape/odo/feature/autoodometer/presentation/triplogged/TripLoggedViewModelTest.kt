package com.hopcape.odo.feature.autoodometer.presentation.triplogged

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.feature.autoodometer.domain.usecase.AcknowledgeTrip
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeAppSettingsRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeServiceLogRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTrackingPreconditions
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTripRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FixedClock
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveDerivedOdometer
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveServiceDueNudge
import com.hopcape.odo.feature.autoodometer.domain.usecase.READY
import com.hopcape.odo.feature.autoodometer.domain.usecase.RejectTrip
import com.hopcape.odo.feature.autoodometer.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.autoodometer.domain.usecase.TEST_OWNER
import com.hopcape.odo.feature.autoodometer.domain.usecase.currentOdometerFrom
import com.hopcape.odo.feature.autoodometer.domain.usecase.testReading
import com.hopcape.odo.feature.autoodometer.domain.usecase.testTrip
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import com.hopcape.odo.feature.autoodometer.presentation.FakeActiveCarProvider
import com.hopcape.odo.feature.autoodometer.presentation.RecordingAnalytics
import com.hopcape.odo.feature.autoodometer.presentation.testTelemetry
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_nudge_due_days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TripLoggedViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val anchor = testReading(date = LocalDate(2026, 1, 1), km = 1000)

    // Contributes to "before" (started before targetTrip) — 5 km.
    private val earlierTrip = testTrip(
        id = "t0",
        startedAt = Instant.parse("2026-01-02T08:00:00Z"),
        endedAt = Instant.parse("2026-01-02T08:30:00Z"),
        distanceMeters = 5_000,
    )

    // The trip this screen is showing — 64 km, 24-minute drive.
    private val targetTripId = TripId("t1")
    private val targetTrip = testTrip(
        id = "t1",
        startedAt = Instant.parse("2026-01-03T08:00:00Z"),
        endedAt = Instant.parse("2026-01-03T08:24:00Z"),
        distanceMeters = 64_000,
    )

    private class Harness(
        val vm: TripLoggedViewModel,
        val trips: FakeTripRepository,
        val settings: FakeAppSettingsRepository,
        val analytics: RecordingAnalytics,
    )

    private fun harness(
        tripId: TripId = targetTripId,
        trips: FakeTripRepository = FakeTripRepository(initial = listOf(earlierTrip, targetTrip)),
        serviceLogs: FakeServiceLogRepository = FakeServiceLogRepository(entries = emptyList(), readings = listOf(anchor)),
        settings: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        readiness: TrackingReadiness = READY.copy(backgroundLocation = false),
        carId: CarId? = TEST_CAR,
        clock: FixedClock = FixedClock(Instant.parse("2026-08-07T00:00:00Z")),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ): Harness {
        val vm = TripLoggedViewModel(
            tripId = tripId,
            trips = trips,
            observeDerivedOdometer = ObserveDerivedOdometer(serviceLogs = serviceLogs, trips = trips, timeZone = TimeZone.UTC),
            observeServiceDueNudge = ObserveServiceDueNudge(
                serviceLogs = serviceLogs,
                currentOdometer = currentOdometerFrom(serviceLogs),
                clock = clock,
                timeZone = TimeZone.UTC,
            ),
            acknowledgeTrip = AcknowledgeTrip(settings = settings),
            rejectTrip = RejectTrip(trips = trips),
            activeCar = FakeActiveCarProvider(carId),
            preconditions = FakeTrackingPreconditions(readiness),
            settings = settings,
            telemetry = testTelemetry(analytics),
        )
        return Harness(vm, trips, settings, analytics)
    }

    @Test
    fun doneTapped_acknowledgesWithTheTripsEndedAt_andPopsBack() = runTest {
        val h = harness()

        h.vm.onEvent(TripLoggedEvent.DoneTapped)

        assertEquals(targetTrip.endedAt, h.settings.saved.single().aoLastAckedTripEndedAt)
        assertIs<TripLoggedEffect.NavigateBack>(h.vm.effects.first())
    }

    /** The marker starts null on a fresh device — this "Done" is the very first one ever. */
    @Test
    fun doneTapped_withNoPriorAcknowledgement_firesTheFunnelsTerminalEvent() = runTest {
        val h = harness(settings = FakeAppSettingsRepository(AppSettings.Default))

        h.vm.onEvent(TripLoggedEvent.DoneTapped)

        assertTrue(h.analytics.events.any { it.first == AutoOdometerTelemetry.Event.FIRST_TRIP_LOGGED })
    }

    /** A device that has already acknowledged a trip must not re-fire the terminal event. */
    @Test
    fun doneTapped_withAPriorAcknowledgement_doesNotRefireTheFunnelsTerminalEvent() = runTest {
        val alreadyAcked = Instant.parse("2026-01-01T00:00:00Z")
        val h = harness(settings = FakeAppSettingsRepository(AppSettings.Default.copy(aoLastAckedTripEndedAt = alreadyAcked)))

        h.vm.onEvent(TripLoggedEvent.DoneTapped)

        assertTrue(h.analytics.events.none { it.first == AutoOdometerTelemetry.Event.FIRST_TRIP_LOGGED })
    }

    @Test
    fun doneTapped_onANeedsConfirmationTrip_confirmsItSoItsDistanceCounts() = runTest {
        val pendingTrip = testTrip(
            id = "t1",
            startedAt = Instant.parse("2026-01-03T08:00:00Z"),
            endedAt = Instant.parse("2026-01-03T08:24:00Z"),
            distanceMeters = 64_000,
            status = TripStatus.NEEDS_CONFIRMATION,
        )
        val h = harness(trips = FakeTripRepository(initial = listOf(pendingTrip)))

        h.vm.onEvent(TripLoggedEvent.DoneTapped)

        assertEquals(TripStatus.CONFIRMED, h.trips.trips.value.single { it.id == targetTripId }.status)
    }

    @Test
    fun doneTapped_onAnAlreadyRecordedTrip_leavesItsStatusAlone() = runTest {
        val h = harness()

        h.vm.onEvent(TripLoggedEvent.DoneTapped)

        assertEquals(TripStatus.RECORDED, h.trips.trips.value.single { it.id == targetTripId }.status)
    }

    @Test
    fun rejectConfirmed_reflowsTheDrumToALowerValue() = runTest {
        val h = harness()
        assertEquals(1069, h.vm.state.value.odometerNowKm, "anchor 1000 + earlier 5km + target 64km")

        h.vm.onEvent(TripLoggedEvent.RejectTapped)
        assertTrue(h.vm.state.value.showRejectConfirm)
        h.vm.onEvent(TripLoggedEvent.RejectConfirmed)

        assertFalse(h.vm.state.value.showRejectConfirm)
        assertEquals(TripStatus.REJECTED, h.trips.trips.value.single { it.id == targetTripId }.status)
        assertEquals(1005, h.vm.state.value.odometerNowKm, "target's 64km drops out once rejected")
    }

    @Test
    fun backgroundUpgradeCard_visibleUntilGranted() = runTest {
        val h = harness(readiness = READY.copy(backgroundLocation = false))
        assertTrue(h.vm.state.value.backgroundUpgrade.visible)

        h.vm.onEvent(TripLoggedEvent.BackgroundLocationStatusObserved(PermissionStatus.Granted))

        assertFalse(h.vm.state.value.backgroundUpgrade.visible)
    }

    @Test
    fun backgroundUpgradeCard_hiddenFromTheStart_whenAlreadyGranted() = runTest {
        val h = harness(readiness = READY.copy(backgroundLocation = true))

        assertFalse(h.vm.state.value.backgroundUpgrade.visible)
    }

    @Test
    fun upgradeAnswered_firesTelemetry_onlyAfterAnExplicitAsk() = runTest {
        val h = harness(readiness = READY.copy(backgroundLocation = false))

        // A passive status re-read before any tap must not fire the funnel event.
        h.vm.onEvent(TripLoggedEvent.BackgroundLocationStatusObserved(PermissionStatus.Askable))
        assertTrue(h.analytics.events.none { it.first == AutoOdometerTelemetry.Event.BACKGROUND_UPGRADE_ANSWERED })

        h.vm.onEvent(TripLoggedEvent.UpgradeTapped)
        h.vm.onEvent(TripLoggedEvent.BackgroundLocationStatusObserved(PermissionStatus.Granted))

        assertEquals(
            true,
            h.analytics.last(AutoOdometerTelemetry.Event.BACKGROUND_UPGRADE_ANSWERED)?.get(AutoOdometerTelemetry.Key.GRANTED),
        )
    }

    @Test
    fun nudge_hiddenWhenNothingIsDue() = runTest {
        val h = harness(serviceLogs = FakeServiceLogRepository(entries = emptyList(), readings = listOf(anchor)))

        assertNull(h.vm.state.value.nudge)
    }

    @Test
    fun nudge_shownWithTheDueSoonCopy_whenSomethingIsDue() = runTest {
        val dueEntry = ServiceLogEntry.create(
            id = ServiceLogId("log-1"),
            carId = TEST_CAR,
            ownerId = TEST_OWNER,
            serviceDate = LocalDate(2026, 2, 10),
            odometerKm = 900,
            totalAmountPaise = 100_000L,
            today = LocalDate(2026, 8, 7),
            workshopName = "Test Workshop",
        ).getOrNull() ?: error("expected a valid entry")
        val h = harness(serviceLogs = FakeServiceLogRepository(entries = listOf(dueEntry), readings = emptyList()))

        assertEquals(Res.string.ao_trip_logged_nudge_due_days, h.vm.state.value.nudge?.id)
    }

    @Test
    fun noActiveCar_doesNotLoad_butStillRecordsTelemetry() = runTest {
        val h = harness(carId = null)

        assertFalse(h.vm.state.value.loading)
        assertTrue(h.analytics.events.any { it.first == AutoOdometerTelemetry.Event.NO_ACTIVE_CAR })
    }
}
