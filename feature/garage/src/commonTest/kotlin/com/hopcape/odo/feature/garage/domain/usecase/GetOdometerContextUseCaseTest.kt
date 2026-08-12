package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.currentReading
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class GetOdometerContextUseCaseTest {

    private val today = LocalDate(2026, 7, 28)

    private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad km=$value") }

    private fun reading(logId: String?, date: LocalDate, km: Int) =
        OdometerReading(logId = logId?.let(::ServiceLogId), date = date, odometer = km(km))

    private fun useCase(
        readings: List<OdometerReading>?,
        // Mirrors the pre-trip-aware behaviour by default: the latest manual reading, no
        // trips summed on top. `aggregateOverride` lets a test prove the trip-aware case.
        aggregateOverride: Distance? = null,
    ) = GetOdometerContextUseCase(
        logs = FakeServiceLogRepository(readings),
        currentOdometer = FakeCurrentOdometerProvider(
            aggregateOverride ?: readings?.currentReading()?.odometer,
        ),
        clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
        timeZone = TimeZone.UTC,
    )

    @Test
    fun theSheetStartsFromTheCarsOwnReading() = runTest {
        val result = useCase(
            listOf(
                reading(null, LocalDate(2026, 3, 2), 45_000),
                reading("log-1", LocalDate(2026, 1, 15), 40_000),
            ),
        )(TEST_CAR)

        val context = result.getOrNull()!!
        assertEquals(45_000, context.lastRecorded.odometer.km)
        assertEquals(LocalDate(2026, 3, 2), context.lastRecorded.date)
        assertEquals(today, context.today)
    }

    @Test
    fun aServiceNewerThanTheCarsOwnReading_seedsTheSheet() = runTest {
        // A service logged after onboarding carries the freshest reading; starting the
        // sheet below it would offer numbers the odometer rule then rejects.
        val result = useCase(
            listOf(
                reading(null, LocalDate(2026, 3, 2), 45_000),
                reading("log-1", LocalDate(2026, 6, 15), 52_000),
            ),
        )(TEST_CAR)

        val context = result.getOrNull()!!
        assertEquals(52_000, context.lastRecorded.odometer.km)
        assertEquals(LocalDate(2026, 6, 15), context.lastRecorded.date)
    }

    @Test
    fun theSheetStartsFromTheTripAwareAggregate_notTheRawReading() = runTest {
        // A counted auto-trip has moved the car past its last manual reading — the drum
        // must start from that aggregate, while the date it measures the monthly rate
        // against stays the manual reading's own day.
        val result = useCase(
            readings = listOf(reading(null, LocalDate(2026, 3, 2), 45_000)),
            aggregateOverride = km(45_120),
        )(TEST_CAR)

        val context = result.getOrNull()!!
        assertEquals(45_120, context.lastRecorded.odometer.km)
        assertEquals(LocalDate(2026, 3, 2), context.lastRecorded.date)
    }

    @Test
    fun noReadings_isCarNotFound() = runTest {
        assertIs<DomainError.CarNotFound>(useCase(null)(TEST_CAR).leftOrNull())
    }

    @Test
    fun readingsWithoutTheCarsOwn_isCarNotFound() = runTest {
        val result = useCase(listOf(reading("log-1", LocalDate(2026, 1, 15), 40_000)))(TEST_CAR)

        assertIs<DomainError.CarNotFound>(result.leftOrNull())
    }

    @Test
    fun distanceSince_isMeasuredAgainstTheRecordedReading() = runTest {
        val context = useCase(listOf(reading(null, LocalDate(2026, 3, 2), 45_000)))(TEST_CAR).getOrNull()!!

        assertEquals(3_500, context.kmSinceLastRecorded(48_500))
    }

    /** Dialling below what is on record is not distance travelled backwards. */
    @Test
    fun distanceSince_neverGoesNegative() = runTest {
        val context = useCase(listOf(reading(null, LocalDate(2026, 3, 2), 45_000)))(TEST_CAR).getOrNull()!!

        assertEquals(0, context.kmSinceLastRecorded(40_000))
    }

    @Test
    fun monthlyRate_comesFromTheTimeSinceTheReading() = runTest {
        // 148 days from 2 Mar to 28 Jul; 7,400 km over that is about 1,500 km a month.
        val context = useCase(listOf(reading(null, LocalDate(2026, 3, 2), 45_000)))(TEST_CAR).getOrNull()!!

        assertEquals(1_500, context.kmPerMonth(52_400))
    }

    /** A few days of driving cannot be turned into a confident monthly figure. */
    @Test
    fun monthlyRate_isWithheldWhenTooLittleTimeHasPassed() = runTest {
        val context = useCase(listOf(reading(null, LocalDate(2026, 7, 25), 45_000)))(TEST_CAR).getOrNull()!!

        assertNull(context.kmPerMonth(45_600))
    }
}
