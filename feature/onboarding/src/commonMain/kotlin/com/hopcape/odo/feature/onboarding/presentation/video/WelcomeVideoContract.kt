package com.hopcape.odo.feature.onboarding.presentation.video

import org.jetbrains.compose.resources.StringResource

/**
 * One page of the video intro: a clip, a title and a line of copy.
 *
 * [videoUrl] can be blank. That is the ordinary case on a build with no clips configured,
 * and the page is required to work without it — see [WelcomeVideoScreen].
 */
internal data class VideoPage(
    val videoUrl: String,
    val title: StringResource,
    val body: StringResource,
)

internal sealed interface WelcomeVideoEvent {
    /** Advance, or finish on the last page. */
    data object NextClicked : WelcomeVideoEvent
    data object SkipClicked : WelcomeVideoEvent
}

internal sealed interface WelcomeVideoEffect {
    /** Into car setup — the same place the usual welcome page leads. */
    data object OpenCarSetup : WelcomeVideoEffect
}
