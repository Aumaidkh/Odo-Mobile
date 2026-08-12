package com.hopcape.odo.feature.garage.presentation

import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.garage.domain.usecase.FakeCarRepository
import com.hopcape.odo.feature.garage.domain.usecase.FakeServiceLogRepository
import com.hopcape.odo.feature.garage.domain.usecase.FixedClock
import com.hopcape.odo.feature.garage.domain.usecase.GetOdometerContextUseCase
import com.hopcape.odo.feature.garage.domain.usecase.OdometerContext
import com.hopcape.odo.feature.garage.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.garage.domain.usecase.UpdateOdometerUseCase
import com.hopcape.odo.feature.garage.domain.usecase.currentOdometerFrom
import com.hopcape.odo.feature.garage.domain.usecase.testCar
import com.hopcape.odo.feature.garage.presentation.sheets.UpdateOdometerEffect
import com.hopcape.odo.feature.garage.presentation.sheets.UpdateOdometerEvent
import com.hopcape.odo.feature.garage.presentation.sheets.UpdateOdometerViewModel
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.Submission
import arrow.core.getOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class UpdateOdometerViewModelTest {

    private class FakeActiveCar(carId: CarId?) : ActiveCarProvider {
        private val _id = MutableStateFlow(carId)
        override val activeCarId: StateFlow<CarId?> = _id
    }

    private val clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun baseline(km: Int) = OdometerReading(
        logId = null,
        date = LocalDate(2026, 3, 2),
        odometer = Distance.of(km).getOrElse { error("bad km") },
    )

    private fun viewModel(
        carId: CarId? = TEST_CAR,
        cars: FakeCarRepository = FakeCarRepository(testCar(odometerKm = 45_000)),
        readings: List<OdometerReading>? = listOf(baseline(45_000)),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ): Pair<UpdateOdometerViewModel, RecordingAnalytics> {
        val logs = FakeServiceLogRepository(readings)
        return UpdateOdometerViewModel(
            activeCar = FakeActiveCar(carId),
            getContext = GetOdometerContextUseCase(logs, currentOdometerFrom(logs), clock, TimeZone.UTC),
            updateOdometer = UpdateOdometerUseCase(cars, logs, clock, TimeZone.UTC),
            telemetry = testTelemetry(analytics),
        ) to analytics
    }

    @Test
    fun theSheetOpensOnTheReadingOnRecord() = runTest {
        val (viewModel, _) = viewModel()

        val context = assertIs<Loadable.Ready<OdometerContext>>(viewModel.state.value.context)
        assertEquals(45_000, context.value.lastRecorded.odometer.km)
    }

    @Test
    fun aHigherReading_isSavedAndClosesTheSheet() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000))
        val (viewModel, _) = viewModel(cars = cars)

        viewModel.onEvent(UpdateOdometerEvent.Save(48_500))

        assertIs<UpdateOdometerEffect.Saved>(viewModel.effects.first())
        assertEquals(48_500, cars.updated.single().odometer.km)
    }

    /**
     * A refused reading keeps the sheet open with the reason on it. The owner has a number
     * in front of them that the history contradicts, and closing would throw away both.
     */
    @Test
    fun aReadingThatGoesBackwards_isRefusedWithoutClosing() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000))
        val (viewModel, analytics) = viewModel(cars = cars)

        viewModel.onEvent(UpdateOdometerEvent.Save(30_000))

        val submission = assertIs<Submission.Failed>(viewModel.state.value.submission)
        assertNotNull(submission.message)
        assertTrue(cars.updated.isEmpty(), "a refused reading must not be stored")
        assertTrue(
            analytics.events.any { it.first == GarageTelemetry.Event.ODOMETER_REFUSED },
            "a refusal is its own event, not a generic failure: ${analytics.events.map { it.first }}",
        )
    }

    @Test
    fun noCar_saysSoInsteadOfSeedingTheDrums() = runTest {
        val (viewModel, _) = viewModel(carId = null)

        assertIs<Loadable.Failed>(viewModel.state.value.context)
    }

    @Test
    fun openingTheSheet_isCounted() = runTest {
        val (_, analytics) = viewModel()

        assertTrue(analytics.events.any { it.first == GarageTelemetry.Event.ODOMETER_OPENED })
    }
}
