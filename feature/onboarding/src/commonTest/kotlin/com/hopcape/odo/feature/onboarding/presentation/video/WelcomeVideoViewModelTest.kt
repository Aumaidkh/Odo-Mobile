package com.hopcape.odo.feature.onboarding.presentation.video

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
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
        val viewModel = WelcomeVideoViewModel(config, telemetry())

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

        viewModel.onEvent(WelcomeVideoEvent.SkipClicked(page = 0))

        // The same destination as Next. There is no version of first run that does not set
        // up a car, so Skip leaves the clips, not the flow.
        assertEquals(WelcomeVideoEffect.OpenCarSetup, viewModel.effects.first())
    }

    /* ------------------------------ Telemetry ------------------------------ */

    @Test
    fun theIntroReportsItselfShown_soAVideoBuildIsNotInvisible() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()

        viewModel(analytics = analytics)

        // The screen shipped with no analytics at all, which is why a build with the video
        // flag on showed installs arriving at car setup out of nowhere.
        val shown = analytics.events.single { it.first == WelcomeVideoTelemetry.Event.SHOWN }
        assertEquals(2, shown.second[WelcomeVideoTelemetry.Key.PAGES])
    }

    @Test
    fun eachPageIsCountedOnce_howeverOftenThePagerSettlesOnIt() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)

        viewModel.onEvent(WelcomeVideoEvent.PageSettled(0))
        viewModel.onEvent(WelcomeVideoEvent.PageSettled(1))
        // Swiping back re-settles page 0. A second event would double the page's reach.
        viewModel.onEvent(WelcomeVideoEvent.PageSettled(0))

        assertEquals(
            listOf(0, 1),
            analytics.events
                .filter { it.first == WelcomeVideoTelemetry.Event.PAGE_VIEWED }
                .map { it.second[WelcomeVideoTelemetry.Key.PAGE] },
        )
    }

    @Test
    fun skippingSaysWhichPageWasOnScreen() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)

        viewModel.onEvent(WelcomeVideoEvent.SkipClicked(page = 1))

        // Giving up on page 1 and giving up on page 0 are different stories about the pitch.
        val skipped = analytics.events.single { it.first == WelcomeVideoTelemetry.Event.SKIPPED }
        assertEquals(1, skipped.second[WelcomeVideoTelemetry.Key.PAGE])
    }

    @Test
    fun finishingAndSkipping_areNotTheSameEvent() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)

        viewModel.onEvent(WelcomeVideoEvent.NextClicked)

        assertEquals(1, analytics.events.count { it.first == WelcomeVideoTelemetry.Event.COMPLETED })
        assertEquals(0, analytics.events.count { it.first == WelcomeVideoTelemetry.Event.SKIPPED })
    }

    @Test
    fun aClipThatNeverLoads_isCountedOncePerPage() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)

        // Recomposition can report the same failure repeatedly; the rate has to stay per page.
        repeat(3) { viewModel.onEvent(WelcomeVideoEvent.ClipFailed(0)) }
        viewModel.onEvent(WelcomeVideoEvent.ClipFailed(1))

        assertEquals(2, analytics.events.count { it.first == WelcomeVideoTelemetry.Event.CLIP_FAILED })
    }

    private fun viewModel(
        refuelUrl: String = "",
        scannerUrl: String = "",
        analytics: AnalyticsTracker = RecordingAnalytics(),
    ) = WelcomeVideoViewModel(CountingConfig(refuelUrl, scannerUrl), telemetry(analytics))

    /** The real facade, so its own code runs under test; only the tracker is a fake. */
    private fun telemetry(analytics: AnalyticsTracker = RecordingAnalytics()) =
        WelcomeVideoTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            ids = IdGenerator { "trace-1" },
        )

    /** Records what was tracked, so the funnel can be asserted. */
    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) {
            events += eventName to properties
        }
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }

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
