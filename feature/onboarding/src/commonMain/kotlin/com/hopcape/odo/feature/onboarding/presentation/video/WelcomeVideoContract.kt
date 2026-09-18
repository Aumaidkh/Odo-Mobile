package com.hopcape.odo.feature.onboarding.presentation.video

import org.jetbrains.compose.resources.DrawableResource
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
    /**
     * A still from the clip, shown behind it.
     *
     * It covers the wait while the clip buffers, but that is the smaller half. The clip is
     * streamed, and onboarding runs exactly once — so on a first launch with no network the
     * poster is not a placeholder, it is the whole page, permanently. `null` means no still
     * has been exported for this page yet, and it falls back to an empty panel.
     */
    val poster: DrawableResource? = null,
)

internal sealed interface WelcomeVideoEvent {
    /** Advance, or finish on the last page. */
    data object NextClicked : WelcomeVideoEvent

    /** [page] is the one being looked at when they gave up, which is the point of counting it. */
    data class SkipClicked(val page: Int) : WelcomeVideoEvent

    /** The pager came to rest on [page]. Observed only; nothing about the flow depends on it. */
    data class PageSettled(val page: Int) : WelcomeVideoEvent

    /** [page]'s clip did not load. The page still works — see [VideoPage.poster]. */
    data class ClipFailed(val page: Int) : WelcomeVideoEvent
}

internal sealed interface WelcomeVideoEffect {
    /** Into car setup — the same place the usual welcome page leads. */
    data object OpenCarSetup : WelcomeVideoEffect
}
