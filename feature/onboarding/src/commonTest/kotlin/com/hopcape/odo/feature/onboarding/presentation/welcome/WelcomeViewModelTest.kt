package com.hopcape.odo.feature.onboarding.presentation.welcome

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.feature.onboarding.presentation.NoopLogger
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry.Event
import com.hopcape.odo.feature.onboarding.presentation.RecordingAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun welcomeVm(analytics: AnalyticsTracker = RecordingAnalytics()) =
        WelcomeViewModel(
            WelcomeTelemetry(logger = NoopLogger, analytics = analytics, ids = IdGenerator { "welcome-trace" }),
        )

    @Test
    fun startsOnFirstSlide() {
        val vm = welcomeVm()
        val state = vm.state.value
        assertEquals(0, state.currentIndex)
        assertEquals(WelcomePage.OVERPAYING, state.currentPage)
        assertTrue(state.isFirstPage)
        assertTrue(!state.isLastPage)
    }

    @Test
    fun nextAdvancesThroughSlidesWithoutFinishing() = runTest(testDispatcher) {
        val vm = welcomeVm()
        val effects = mutableListOf<WelcomeEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }

        vm.onEvent(WelcomeEvent.NextClicked)
        assertEquals(WelcomePage.HIDDEN_CHARGES, vm.state.value.currentPage)

        vm.onEvent(WelcomeEvent.NextClicked)
        assertEquals(WelcomePage.FAIRNESS, vm.state.value.currentPage)
        assertTrue(vm.state.value.isLastPage)

        advanceUntilIdle()
        assertTrue(effects.isEmpty())
    }

    @Test
    fun nextOnLastSlide_emitsNavigateToOnboarding() = runTest(testDispatcher) {
        val vm = welcomeVm()
        vm.onEvent(WelcomeEvent.PageChanged(WelcomePage.entries.lastIndex))

        val effectDeferred = async { vm.effects.first() }
        runCurrent()

        vm.onEvent(WelcomeEvent.NextClicked)
        advanceUntilIdle()

        assertEquals(WelcomeEffect.NavigateToOnboarding, effectDeferred.await())
        // Finishing does not move the pager past the last slide.
        assertTrue(vm.state.value.isLastPage)
    }

    @Test
    fun skip_emitsNavigateToOnboardingFromAnySlide() = runTest(testDispatcher) {
        val vm = welcomeVm()

        val effectDeferred = async { vm.effects.first() }
        runCurrent()

        vm.onEvent(WelcomeEvent.SkipClicked)
        advanceUntilIdle()

        assertEquals(WelcomeEffect.NavigateToOnboarding, effectDeferred.await())
    }

    @Test
    fun finish_restsOnLastSlide_soBackReturnResumesThere() = runTest(testDispatcher) {
        // Skip from the very first slide — the carousel should still settle on the
        // last slide, so a later back-out of setup returns to "Get Started".
        val vm = welcomeVm()
        assertEquals(0, vm.state.value.currentIndex)

        vm.onEvent(WelcomeEvent.SkipClicked)
        advanceUntilIdle()

        assertTrue(vm.state.value.isLastPage)
        assertEquals(WelcomePage.entries.lastIndex, vm.state.value.currentIndex)
    }

    @Test
    fun back_goesToPreviousSlideAndClampsAtFirst() {
        val vm = welcomeVm()
        vm.onEvent(WelcomeEvent.NextClicked)
        vm.onEvent(WelcomeEvent.BackClicked)
        assertEquals(0, vm.state.value.currentIndex)

        // Already on the first slide — Back cannot underflow.
        vm.onEvent(WelcomeEvent.BackClicked)
        assertEquals(0, vm.state.value.currentIndex)
    }

    @Test
    fun pageChanged_clampsOutOfRangeIndices() {
        val vm = welcomeVm()
        val last = WelcomePage.entries.lastIndex

        vm.onEvent(WelcomeEvent.PageChanged(99))
        assertEquals(last, vm.state.value.currentIndex)

        vm.onEvent(WelcomeEvent.PageChanged(-5))
        assertEquals(0, vm.state.value.currentIndex)
    }

    @Test
    fun tracksWelcomeFunnel_shownViewedCompleted() = runTest(testDispatcher) {
        val analytics = RecordingAnalytics()
        val vm = welcomeVm(analytics)

        vm.onEvent(WelcomeEvent.NextClicked) // view slide 2
        vm.onEvent(WelcomeEvent.NextClicked) // view slide 3 (last)
        vm.onEvent(WelcomeEvent.NextClicked) // complete
        advanceUntilIdle()

        assertEquals(
            listOf(
                Event.WELCOME_SHOWN,
                Event.WELCOME_SLIDE_VIEWED,
                Event.WELCOME_SLIDE_VIEWED,
                Event.WELCOME_COMPLETED,
            ),
            analytics.names,
        )
    }

    @Test
    fun tracksSkip_withOriginSlide() = runTest(testDispatcher) {
        val analytics = RecordingAnalytics()
        val vm = welcomeVm(analytics)

        vm.onEvent(WelcomeEvent.SkipClicked)
        advanceUntilIdle()

        assertTrue(analytics.names.contains(Event.WELCOME_SKIPPED))
        assertEquals(WelcomePage.OVERPAYING.name, analytics.propsOf(Event.WELCOME_SKIPPED)?.get("from_slide"))
    }
}
