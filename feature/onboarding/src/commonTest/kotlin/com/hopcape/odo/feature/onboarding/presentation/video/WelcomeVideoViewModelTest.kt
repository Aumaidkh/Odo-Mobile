package com.hopcape.odo.feature.onboarding.presentation.video

import com.hopcape.odo.feature.onboarding.OnboardingConfig
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_video_refuel_body
import com.hopcape.odo.feature.onboarding.resources.onb_video_refuel_title
import com.hopcape.odo.feature.onboarding.resources.onb_video_scanner_body
import com.hopcape.odo.feature.onboarding.resources.onb_video_scanner_title
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Pointing Dispatchers.Main at the test scheduler is still an experimental coroutines API.
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeVideoViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun thePagesArePitchedInOrder_smartRefuelThenBillScanner() = runTest(dispatcher) {
        val pages = viewModel().pages

        assertEquals(2, pages.size)
        assertEquals(Res.string.onb_video_refuel_title, pages[0].title)
        assertEquals(Res.string.onb_video_refuel_body, pages[0].body)
        assertEquals(Res.string.onb_video_scanner_title, pages[1].title)
        assertEquals(Res.string.onb_video_scanner_body, pages[1].body)
    }

    @Test
    fun eachPageTakesItsOwnClipFromConfig() = runTest(dispatcher) {
        val pages = viewModel(
            refuelUrl = "https://cdn.example/refuel.mp4",
            scannerUrl = "https://cdn.example/scanner.mp4",
        ).pages

        // Two keys, not one: the clips are re-cut separately, and a page showing the other
        // page's video would be a silent mix-up rather than a visible failure.
        assertEquals("https://cdn.example/refuel.mp4", pages[0].videoUrl)
        assertEquals("https://cdn.example/scanner.mp4", pages[1].videoUrl)
    }

    @Test
    fun withNoClipsConfigured_thePagesStillExistAndKeepTheirStills() = runTest(dispatcher) {
        // Blank URLs are the ordinary case until the clips are published, and the intro has
        // to survive it: the poster is then not a placeholder, it is the whole page.
        val pages = viewModel().pages

        assertTrue(pages.all { it.videoUrl.isEmpty() })
        pages.forEach { assertNotNull(it.poster) }
    }

    @Test
    fun theUrlsAreReadOnce_soAConfigFetchCannotRestartThePlayer() = runTest(dispatcher) {
        val config = CountingConfig(refuelUrl = "a", scannerUrl = "b")
        val viewModel = WelcomeVideoViewModel(config)

        repeat(3) { viewModel.pages }

        // One read per key, at construction. Reading per frame would swap the URL under a
        // playing clip the moment a fetch activated a new one.
        assertEquals(1, config.refuelReads)
        assertEquals(1, config.scannerReads)
    }

    @Test
    fun finishingTheIntro_goesIntoCarSetup() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeVideoEvent.NextClicked)

        assertEquals(WelcomeVideoEffect.OpenCarSetup, viewModel.effects.first())
    }

    @Test
    fun skippingTheIntro_isNotSkippingOnboarding() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeVideoEvent.SkipClicked)

        // The same destination as Next. There is no version of first run that does not set
        // up a car, so Skip leaves the clips, not the flow.
        assertEquals(WelcomeVideoEffect.OpenCarSetup, viewModel.effects.first())
    }

    private fun viewModel(refuelUrl: String = "", scannerUrl: String = "") =
        WelcomeVideoViewModel(CountingConfig(refuelUrl, scannerUrl))

    /** Answers like config, and counts the reads so "read once" can be asserted. */
    private class CountingConfig(
        private val refuelUrl: String,
        private val scannerUrl: String,
    ) : OnboardingConfig {
        var refuelReads = 0
            private set
        var scannerReads = 0
            private set

        override val videoEnabled = true
        override val refuelVideoUrl: String get() = refuelUrl.also { refuelReads++ }
        override val scannerVideoUrl: String get() = scannerUrl.also { scannerReads++ }
    }
}
