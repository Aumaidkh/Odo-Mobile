package com.hopcape.odo.feature.onboarding.presentation.welcome

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Pointing Dispatchers.Main at the test scheduler is still an experimental coroutines API.
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun continuing_goesStraightIntoCarSetup() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeEvent.ContinueClicked)

        // No sign-in first: first run has to reach a working car without an account.
        assertEquals(WelcomeEffect.OpenCarSetup, viewModel.effects.first())
    }

    @Test
    fun theLegalLinks_areOffered() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeEvent.TermsClicked)
        assertEquals(WelcomeEffect.OpenTerms, viewModel.effects.first())

        viewModel.onEvent(WelcomeEvent.PrivacyClicked)
        assertEquals(WelcomeEffect.OpenPrivacy, viewModel.effects.first())
    }

    @Test
    fun thePitch_marksTheTopOfTheFunnel() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()

        val viewModel = viewModel(analytics)
        viewModel.onEvent(WelcomeEvent.ContinueClicked)

        // Shown-then-continued is what makes the first-run funnel measurable at all.
        assertEquals(
            listOf(OnboardingTelemetry.Event.WELCOME_SHOWN, OnboardingTelemetry.Event.WELCOME_COMPLETED),
            analytics.names,
        )
    }

    private fun viewModel(analytics: AnalyticsTracker = RecordingAnalytics()) = WelcomeViewModel(
        telemetry = OnboardingTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            ids = IdGenerator { "trace-1" },
        ),
    )

    private class RecordingAnalytics : AnalyticsTracker {
        val names = mutableListOf<String>()

        override fun track(eventName: String, properties: Map<String, Any?>) {
            names += eventName
        }

        override fun identify(traits: UserTraits) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }
}
