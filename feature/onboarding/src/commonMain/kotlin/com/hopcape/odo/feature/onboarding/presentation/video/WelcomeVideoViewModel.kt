package com.hopcape.odo.feature.onboarding.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.feature.onboarding.OnboardingConfig
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_video_refuel_body
import com.hopcape.odo.feature.onboarding.resources.onb_video_refuel_poster
import com.hopcape.odo.feature.onboarding.resources.onb_video_refuel_title
import com.hopcape.odo.feature.onboarding.resources.onb_video_scanner_body
import com.hopcape.odo.feature.onboarding.resources.onb_video_scanner_poster
import com.hopcape.odo.feature.onboarding.resources.onb_video_scanner_title
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The two pages, and where "Next" goes from the last one.
 *
 * The URLs come from config so the clips can be re-cut without a release. They are read once
 * here rather than per frame: a URL that changed mid-screen would restart the player under
 * the owner.
 */
internal class WelcomeVideoViewModel(
    config: OnboardingConfig,
    private val telemetry: WelcomeVideoTelemetry,
) : ViewModel() {

    val pages: List<VideoPage> = listOf(
        VideoPage(
            videoUrl = config.refuelVideoUrl,
            title = Res.string.onb_video_refuel_title,
            body = Res.string.onb_video_refuel_body,
            poster = Res.drawable.onb_video_refuel_poster,
        ),
        VideoPage(
            videoUrl = config.scannerVideoUrl,
            title = Res.string.onb_video_scanner_title,
            body = Res.string.onb_video_scanner_body,
            poster = Res.drawable.onb_video_scanner_poster,
        ),
    )

    private val _effects = Channel<WelcomeVideoEffect>(Channel.BUFFERED)
    val effects: Flow<WelcomeVideoEffect> = _effects.receiveAsFlow()

    /** Pages already counted, so a recomposition or a swipe back cannot report one twice. */
    private val viewedPages = mutableSetOf<Int>()
    private val failedClips = mutableSetOf<Int>()

    init {
        telemetry.shown(pages.size)
    }

    fun onEvent(event: WelcomeVideoEvent) {
        when (event) {
            // Both finish the same way. Skipping the intro is not skipping onboarding —
            // there is no version of first run that does not set up a car.
            WelcomeVideoEvent.NextClicked -> {
                telemetry.completed()
                openCarSetup()
            }

            is WelcomeVideoEvent.SkipClicked -> {
                telemetry.skipped(event.page)
                openCarSetup()
            }

            is WelcomeVideoEvent.PageSettled ->
                if (viewedPages.add(event.page)) telemetry.pageViewed(event.page)

            is WelcomeVideoEvent.ClipFailed ->
                if (failedClips.add(event.page)) telemetry.clipFailed(event.page)
        }
    }

    private fun openCarSetup() {
        viewModelScope.launch { _effects.send(WelcomeVideoEffect.OpenCarSetup) }
    }
}
