package com.hopcape.odo.feature.servicelog.presentation.form

import arrow.core.Either
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.servicelog.domain.usecase.AddServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetCurrentOdometerUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.UpdateServiceLogUseCase
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.currentOdometerFrom
import com.hopcape.odo.feature.servicelog.presentation.TEST_CAR
import com.hopcape.odo.feature.servicelog.presentation.TEST_CLOCK
import com.hopcape.odo.feature.servicelog.presentation.testEntry
import com.hopcape.odo.feature.servicelog.presentation.state.Submission
import com.hopcape.odo.feature.servicelog.presentation.state.text
import com.hopcape.performance.api.APM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The add/edit form's submission lifecycle.
 *
 * The case that matters is what the form settles on *after* a successful save. It used to
 * stay at [Submission.InFlight] forever, which `canSave` reads — so the Save button never
 * re-enabled and a second entry could not be added.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceLogFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aSuccessfulSaveLeavesInFlight_soTheFormCanBeSubmittedAgain() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository()
        val viewModel = viewModel(logs)
        advanceUntilIdle()

        viewModel.fillRequiredFields(km = "45000")
        viewModel.onEvent(ServiceLogFormEvent.SaveClicked)
        advanceUntilIdle()

        assertEquals(1, logs.addCount)
        assertEquals(Submission.Succeeded, viewModel.state.value.submission)
        assertTrue(
            viewModel.state.value.canSave,
            "Save must re-enable after a successful write — a form stuck InFlight cannot add a second entry",
        )
    }

    @Test
    fun aSecondSaveOnTheSameFormWritesASecondEntry() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository()
        val viewModel = viewModel(logs)
        advanceUntilIdle()

        viewModel.fillRequiredFields(km = "45000")
        viewModel.onEvent(ServiceLogFormEvent.SaveClicked)
        advanceUntilIdle()

        // Reaching the form again without the destination being popped — which is exactly
        // what happened while every ViewModel was scoped to the Activity.
        viewModel.onEvent(ServiceLogFormEvent.Field.OdometerChanged("46000"))
        viewModel.onEvent(ServiceLogFormEvent.SaveClicked)
        advanceUntilIdle()

        assertEquals(2, logs.addCount)
    }

    @Test
    fun aDoubleTapOnSaveStillWritesOnce() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository()
        val viewModel = viewModel(logs)
        advanceUntilIdle()

        viewModel.fillRequiredFields(km = "45000")
        // Both taps land before the write completes, which is what the in-flight job guards.
        viewModel.onEvent(ServiceLogFormEvent.SaveClicked)
        viewModel.onEvent(ServiceLogFormEvent.SaveClicked)
        advanceUntilIdle()

        assertEquals(1, logs.addCount)
    }

    @Test
    fun aFormWithNoOdometerCannotBeSaved() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository()
        val viewModel = viewModel(logs)
        advanceUntilIdle()

        // Odo's one mandatory field. The form opens prefilled, so the owner clearing it is
        // the empty case — and Save is refused by the state, not just by a disabled button.
        viewModel.onEvent(ServiceLogFormEvent.Field.OdometerChanged(""))
        viewModel.onEvent(ServiceLogFormEvent.SaveClicked)
        advanceUntilIdle()

        assertEquals(0, logs.addCount)
        assertEquals(Submission.Idle, viewModel.state.value.submission)
    }

    @Test
    fun theOdometerFieldStartsAtTheCarsLatestReading() = runTest(dispatcher) {
        // Onboarded at 45,000 km, then a service logged at 52,000 km — the form must start
        // from the newest number, not the onboarding baseline.
        val logs = FakeServiceLogRepository(
            initial = listOf(testEntry("log-1", km = 52_000, date = LocalDate(2026, 2, 1))),
            carBaselineKm = 45_000,
            carBaselineDate = LocalDate(2026, 1, 1),
        )

        val viewModel = viewModel(logs)
        advanceUntilIdle()

        assertEquals("52000", viewModel.state.value.odometer.text)
    }

    @Test
    fun thePrefilledReadingNeverOverwritesWhatWasTyped() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository(carBaselineKm = 45_000)
        val viewModel = viewModel(logs)

        // Typed before the prefill read lands.
        viewModel.onEvent(ServiceLogFormEvent.Field.OdometerChanged("47000"))
        advanceUntilIdle()

        assertEquals("47000", viewModel.state.value.odometer.text)
    }

    /**
     * Odometer and date — the two the form cannot be submitted without. The date has no
     * default (`FormField<LocalDate>()` starts null), so a form with only an odometer fails
     * validation with MissingServiceDate rather than writing anything.
     */
    private fun ServiceLogFormViewModel.fillRequiredFields(km: String) {
        onEvent(ServiceLogFormEvent.Field.OdometerChanged(km))
        onEvent(ServiceLogFormEvent.Field.DateChanged(LocalDate(2026, 1, 15)))
    }

    private fun viewModel(logs: FakeServiceLogRepository) = ServiceLogFormViewModel(
        carId = TEST_CAR,
        editLogId = null,
        addLog = AddServiceLogUseCase(logs = logs, idGenerator = SequentialIds(), clock = TEST_CLOCK),
        updateLog = UpdateServiceLogUseCase(logs = logs, clock = TEST_CLOCK),
        getLog = GetServiceLogUseCase(logs),
        currentOdometer = GetCurrentOdometerUseCase(currentOdometerFrom(logs)),
        currentOwner = CurrentOwnerProvider { OwnerId("owner-1") },
        settings = DefaultSettings,
        telemetry = ServiceLogTelemetry(
            logger = HLogger.asLogger(),
            analytics = SilentAnalytics,
            tracer = APM.asTracer(),
            ids = SequentialIds(),
        ),
    )

    /** Distinct ids per entry, so a second save cannot collide with the first. */
    private class SequentialIds : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }

    private object DefaultSettings : AppSettingsRepository {
        override fun observe(): Flow<AppSettings> = flowOf(AppSettings())
        override suspend fun save(settings: AppSettings): Either<DomainError, AppSettings> =
            settings.right()
    }

    private object SilentAnalytics : AnalyticsTracker {
        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }
}
