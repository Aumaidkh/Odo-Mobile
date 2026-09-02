package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.getOrElse
import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.feature.garage.domain.model.AutoOdometerCardState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Visibility matrix (reading count x setup state) for the garage's auto-odometer slot (F9).
 *
 * The matrix describes the slot with `auto_odometer_enabled` on. While it is off the slot
 * is [AutoOdometerCardState.Hidden] in every cell, which is what
 * [whileTheFeatureIsOff_theSlotIsHiddenNoMatterWhatIsOnRecord] asserts.
 *
 * Both states run in this one suite. The flag used to be a `const`, so a test could not set
 * it and every case guarded itself with an early return — meaning only whichever half the
 * build happened to be compiled for ever ran.
 */
class ObserveAutoOdometerCardStateTest {

    private val timeZone = TimeZone.UTC
    private val now = Instant.parse("2026-08-07T12:00:00Z")
    private val startOfMonth = Instant.parse("2026-08-01T00:00:00Z")

    private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad km=$value") }

    private fun reading(date: LocalDate, km: Int) = OdometerReading(logId = null, date = date, odometer = km(km))

    private fun useCase(
        readings: List<OdometerReading> = emptyList(),
        bond: VehicleBond? = null,
        enabled: Boolean = false,
        trips: List<Trip> = emptyList(),
        featureEnabled: Boolean = true,
    ) = ObserveAutoOdometerCardState(
        serviceLogs = FakeServiceLogRepository(readings = readings),
        bonds = FakeVehicleBondStore(initial = bond),
        tracker = FakeTripTracker(enabled = enabled),
        trips = FakeTripRepository(initial = trips),
        clock = FixedClock(now),
        config = featureConfig(autoOdometer = featureEnabled),
        timeZone = timeZone,
    )

    private fun featureConfig(autoOdometer: Boolean) = object : FeatureConfig {
        override val autoOdometerEnabled = autoOdometer
        override val refuelDetectEnabled = true
        override val challanEnabled = false
        override val plateLookupEnabled = false
    }

    @Test
    fun zeroReadings_notSetUp_isHidden() = runTest {
        val state = useCase(readings = emptyList())(TEST_CAR).first()

        assertIs<AutoOdometerCardState.Hidden>(state)
    }

    @Test
    fun oneReading_notSetUp_isHidden() = runTest {
        val state = useCase(readings = listOf(reading(LocalDate(2026, 7, 1), 10_000)))(TEST_CAR).first()

        assertIs<AutoOdometerCardState.Hidden>(state)
    }

    @Test
    fun twoOrMoreReadings_notSetUp_showsThePitchCardWithTheLiveCount() = runTest {
        val readings = listOf(
            reading(LocalDate(2026, 6, 1), 10_000),
            reading(LocalDate(2026, 7, 1), 10_500),
            reading(LocalDate(2026, 8, 1), 11_000),
        )
        val state = useCase(readings = readings)(TEST_CAR).first()

        val notSetUp = assertIs<AutoOdometerCardState.NotSetUp>(state)
        assertEquals(3, notSetUp.readingCount)
    }

    @Test
    fun setUp_showsTheStatusTile_regardlessOfReadingCount() = runTest {
        val bond = VehicleBond(TEST_CAR, "AA:BB:CC:DD:EE:FF", TriggerMode.STEREO)
        val trip = testTrip(
            id = "t1",
            startedAt = startOfMonth + 10.minutes,
            endedAt = startOfMonth + 40.minutes,
            distanceMeters = 12_000,
        )
        // Only one reading — the "set up wins over the card" edge case: the tile shows even
        // for an owner who has barely typed a manual reading.
        val state = useCase(
            readings = listOf(reading(LocalDate(2026, 8, 1), 10_000)),
            bond = bond,
            enabled = true,
            trips = listOf(trip),
        )(TEST_CAR).first()

        val setUp = assertIs<AutoOdometerCardState.SetUp>(state)
        assertEquals(12L, setUp.monthlyKm)
    }

    @Test
    fun setUp_withNoReadingsAtAll_stillShowsTheTileNotHidden() = runTest {
        val bond = VehicleBond(TEST_CAR, "AA:BB:CC:DD:EE:FF", TriggerMode.STEREO)
        val state = useCase(readings = emptyList(), bond = bond, enabled = true)(TEST_CAR).first()

        assertIs<AutoOdometerCardState.SetUp>(state)
    }

    @Test
    fun bondWithoutTrackingEnabled_isNotConsideredSetUp() = runTest {
        val bond = VehicleBond(TEST_CAR, "AA:BB:CC:DD:EE:FF", TriggerMode.STEREO)
        val readings = listOf(
            reading(LocalDate(2026, 7, 1), 10_000),
            reading(LocalDate(2026, 8, 1), 10_500),
        )
        val state = useCase(readings = readings, bond = bond, enabled = false)(TEST_CAR).first()

        val notSetUp = assertIs<AutoOdometerCardState.NotSetUp>(state)
        assertEquals(2, notSetUp.readingCount)
    }

    /**
     * The 1.0 twin of the matrix above: the two states that would otherwise show something
     * — enrolled, and enough readings for the pitch — both collapse to hidden, so there is
     * no way into enrollment.
     */
    @Test
    fun whileTheFeatureIsOff_theSlotIsHiddenNoMatterWhatIsOnRecord() = runTest {
        val readings = listOf(
            reading(LocalDate(2026, 7, 1), 10_000),
            reading(LocalDate(2026, 8, 1), 10_500),
        )

        assertIs<AutoOdometerCardState.Hidden>(
            useCase(readings = readings, featureEnabled = false)(TEST_CAR).first(),
        )
        assertIs<AutoOdometerCardState.Hidden>(
            useCase(
                readings = readings,
                bond = VehicleBond(TEST_CAR, "AA:BB:CC:DD:EE:FF", TriggerMode.STEREO),
                enabled = true,
                featureEnabled = false,
            )(TEST_CAR).first(),
        )
    }
}
