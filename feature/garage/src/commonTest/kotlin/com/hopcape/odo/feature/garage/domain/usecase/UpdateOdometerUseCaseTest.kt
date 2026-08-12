package com.hopcape.odo.feature.garage.domain.usecase

import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import arrow.core.getOrElse
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class UpdateOdometerUseCaseTest {

    /** Midday UTC on 28 Jul 2026, read in UTC so the date cannot drift. */
    private val clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z"))

    private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("bad km=$value") }

    private fun reading(logId: String?, date: LocalDate, km: Int) =
        OdometerReading(logId = logId?.let(::ServiceLogId), date = date, odometer = km(km))

    /** The car's own reading, dated when it was written down. */
    private fun baseline(km: Int, on: LocalDate = LocalDate(2026, 3, 2)) = reading(null, on, km)

    private fun useCase(cars: FakeCarRepository, logs: FakeServiceLogRepository) =
        UpdateOdometerUseCase(cars = cars, logs = logs, clock = clock, timeZone = TimeZone.UTC)

    @Test
    fun aHigherReading_isStored() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000))
        val result = useCase(cars, FakeServiceLogRepository(listOf(baseline(45_000))))(TEST_CAR, 48_500)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(48_500, result.getOrNull()?.odometer?.km)
        assertEquals(48_500, cars.updated.single().odometer.km)
    }

    @Test
    fun everythingElseAboutTheCar_isLeftAlone() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000, nickname = "Chhoti"))

        useCase(cars, FakeServiceLogRepository(listOf(baseline(45_000))))(TEST_CAR, 48_500)

        val saved = cars.updated.single()
        assertEquals("Chhoti", saved.nickname)
        assertEquals("MH12AB1234", saved.registrationNumber?.value)
        assertTrue(saved.isPrimary)
    }

    @Test
    fun aReadingBelowTheLastOne_isRefused() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000))

        val result = useCase(cars, FakeServiceLogRepository(listOf(baseline(45_000))))(TEST_CAR, 30_000)

        val error = assertIs<DomainError.OdometerRegression>(result.leftOrNull())
        assertEquals(45_000, error.previousKm)
        assertEquals(30_000, error.attemptedKm)
        assertTrue(cars.updated.isEmpty(), "a refused reading must not be stored")
    }

    /** A service logged last month still counts — the check is against the whole timeline. */
    @Test
    fun aReadingBelowALoggedService_isRefused() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000))
        val logs = FakeServiceLogRepository(
            listOf(
                baseline(45_000, on = LocalDate(2026, 1, 1)),
                reading("log-1", LocalDate(2026, 6, 15), 52_000),
            ),
        )

        val result = useCase(cars, logs)(TEST_CAR, 50_000)

        assertIs<DomainError.OdometerRegression>(result.leftOrNull())
    }

    /** A service logged earlier today binds too — the reading cannot dip below it. */
    @Test
    fun aReadingBelowASameDayService_isRefused() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000))
        val logs = FakeServiceLogRepository(
            listOf(
                baseline(45_000, on = LocalDate(2026, 1, 1)),
                reading("log-1", LocalDate(2026, 7, 28), 52_000),
            ),
        )

        val result = useCase(cars, logs)(TEST_CAR, 50_000)

        assertIs<DomainError.OdometerRegression>(result.leftOrNull())
        assertTrue(cars.updated.isEmpty(), "a refused reading must not be stored")
    }

    /**
     * A reading written down today can be corrected downwards on the same day: the new
     * value replaces the car's own same-day reading, so its stored value does not bind.
     */
    @Test
    fun aTypoFixedOnTheSameDay_isAllowed() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 450_000))
        val logs = FakeServiceLogRepository(listOf(baseline(450_000, on = LocalDate(2026, 7, 28))))

        val result = useCase(cars, logs)(TEST_CAR, 45_000)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(45_000, cars.updated.single().odometer.km)
    }

    @Test
    fun aMissingReading_isRefusedBeforeAnythingIsRead() = runTest {
        val cars = FakeCarRepository(testCar())

        val result = useCase(cars, FakeServiceLogRepository(listOf(baseline(45_000))))(TEST_CAR, null)

        assertIs<DomainError.MissingOdometer>(result.leftOrNull())
        assertTrue(cars.updated.isEmpty())
    }

    @Test
    fun aNegativeReading_isRefused() = runTest {
        val result = useCase(
            FakeCarRepository(testCar()),
            FakeServiceLogRepository(listOf(baseline(45_000))),
        )(TEST_CAR, -1)

        assertIs<DomainError.NegativeOdometer>(result.leftOrNull())
    }

    @Test
    fun noCar_isCarNotFound() = runTest {
        val result = useCase(FakeCarRepository(car = null), FakeServiceLogRepository())(TEST_CAR, 48_500)

        assertIs<DomainError.CarNotFound>(result.leftOrNull())
    }

    /** No readings at all means the car does not exist for this owner. */
    @Test
    fun noReadings_isCarNotFound() = runTest {
        val result = useCase(
            FakeCarRepository(testCar()),
            FakeServiceLogRepository(readings = null),
        )(TEST_CAR, 48_500)

        assertIs<DomainError.CarNotFound>(result.leftOrNull())
    }

    @Test
    fun aFailedWrite_reachesTheCaller() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000), failing = true)

        val result = useCase(cars, FakeServiceLogRepository(listOf(baseline(45_000))))(TEST_CAR, 48_500)

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }
}
