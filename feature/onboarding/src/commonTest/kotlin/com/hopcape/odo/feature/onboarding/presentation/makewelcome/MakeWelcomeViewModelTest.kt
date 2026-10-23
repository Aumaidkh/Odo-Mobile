package com.hopcape.odo.feature.onboarding.presentation.makewelcome

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.onboarding.OnboardingConfig
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadMakeChoicesUseCase
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
import com.hopcape.performance.api.APM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MakeWelcomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theGridShowsTheFirstEightMakesInCatalogOrder() = runTest(dispatcher) {
        val viewModel = viewModel(catalog = FakeCatalog(MAKES))
        advanceUntilIdle()

        assertEquals(MAKES.take(8), viewModel.state.value.tiles)
        assertEquals(MAKES, viewModel.state.value.allMakes)
    }

    @Test
    fun aCatalogWithFewerThanEight_showsWhatItHas() = runTest(dispatcher) {
        val viewModel = viewModel(catalog = FakeCatalog(MAKES.take(3)))
        advanceUntilIdle()

        assertEquals(MAKES.take(3), viewModel.state.value.tiles)
    }

    @Test
    fun aFailedCatalogRead_leavesOnlyOther() = runTest(dispatcher) {
        val viewModel = viewModel(catalog = FakeCatalog(MAKES, failing = true))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.tiles.isEmpty())
    }

    @Test
    fun anUnsetRemoteTitle_fallsBackToTheBundledOne() = runTest(dispatcher) {
        assertNull(viewModel(title = "").state.value.title)
        assertNull(viewModel(title = "   ").state.value.title)
        assertEquals("Check your bill", viewModel(title = "Check your bill").state.value.title)
    }

    @Test
    fun aTileTap_opensSetupWithThatMake() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)

        viewModel.onEvent(MakeWelcomeEvent.MakeTapped("Hyundai"))

        assertEquals(MakeWelcomeEffect.OpenSetup("Hyundai"), viewModel.effects.first())
        val completed = analytics.events.last()
        assertEquals(OnboardingTelemetry.Event.WELCOME_COMPLETED, completed.first)
        assertEquals("tile", completed.second[OnboardingTelemetry.Key.MAKE_SOURCE])
        assertEquals("Hyundai", completed.second[OnboardingTelemetry.Key.MAKE])
    }

    @Test
    fun other_opensTheSheet_andAPickOpensSetup() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)

        viewModel.onEvent(MakeWelcomeEvent.OtherTapped)
        assertTrue(viewModel.state.value.otherOpen)

        viewModel.onEvent(MakeWelcomeEvent.OtherPicked(" Lexus "))

        assertEquals(false, viewModel.state.value.otherOpen)
        assertEquals(MakeWelcomeEffect.OpenSetup("Lexus"), viewModel.effects.first())
        assertEquals(
            listOf(
                OnboardingTelemetry.Event.WELCOME_SHOWN,
                OnboardingTelemetry.Event.MAKE_OTHER_OPENED,
                OnboardingTelemetry.Event.WELCOME_COMPLETED,
            ),
            analytics.events.map { it.first },
        )
        assertEquals("other", analytics.events.last().second[OnboardingTelemetry.Key.MAKE_SOURCE])
    }

    @Test
    fun theWelcomeIsReportedAsAFreshVisit() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        viewModel(analytics = analytics)

        val shown = analytics.events.single()
        assertEquals(OnboardingTelemetry.Event.WELCOME_SHOWN, shown.first)
        assertEquals("fresh", shown.second[OnboardingTelemetry.Key.VARIANT])
    }

    @Test
    fun dismissingOther_closesTheSheetWithoutLeaving() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(MakeWelcomeEvent.OtherTapped)
        viewModel.onEvent(MakeWelcomeEvent.OtherDismissed)

        assertEquals(false, viewModel.state.value.otherOpen)
    }

    private fun viewModel(
        catalog: VehicleCatalog = FakeCatalog(MAKES),
        title: String = "",
        analytics: AnalyticsTracker = RecordingAnalytics(),
    ) = MakeWelcomeViewModel(
        loadMakes = LoadMakeChoicesUseCase(catalog),
        config = FakeConfig(title),
        telemetry = OnboardingTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            tracer = APM.asTracer(),
            ids = IdGenerator { "trace-1" },
        ),
    )

    private class FakeCatalog(private val makes: List<String>, private val failing: Boolean = false) : VehicleCatalog {
        override suspend fun makes(): List<String> = read { makes }
        override suspend fun popularMakes(limit: Int): List<String> = read { makes.take(limit) }
        override suspend fun models(make: String): List<CarModel> = emptyList()
        override fun years(): List<Int> = emptyList()
        override fun fuelTypes(): List<FuelType> = FuelType.entries

        private fun <T> read(block: () -> T): T = if (failing) error("catalog unreadable") else block()
    }

    private class FakeConfig(override val welcomeTitle: String) : OnboardingConfig {
        override val videoEnabled = false
        override val firstRunV2Enabled = true
        override val refuelVideoUrl = ""
        override val scannerVideoUrl = ""
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()

        override fun track(eventName: String, properties: Map<String, Any?>) {
            events += eventName to properties
        }

        override fun identify(traits: UserTraits) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }

    private companion object {
        val MAKES = listOf(
            "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Toyota", "Honda", "Renault", "Skoda", "MG",
        )
    }
}
