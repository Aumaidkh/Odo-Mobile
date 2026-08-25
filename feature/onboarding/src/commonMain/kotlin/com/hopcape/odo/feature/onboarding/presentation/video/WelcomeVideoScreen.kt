package com.hopcape.odo.feature.onboarding.presentation.video

import com.hopcape.odo.feature.onboarding.presentation.components.accentGlow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.platform.video.OdoVideoPlayer
import com.hopcape.odo.core.platform.video.rememberOdoVideoState
import com.hopcape.odo.core.platform.window.OdoLightSystemBars
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_video_refuel_body
import com.hopcape.odo.feature.onboarding.resources.onb_video_refuel_title
import com.hopcape.odo.feature.onboarding.resources.onb_video_scanner_body
import com.hopcape.odo.feature.onboarding.resources.onb_video_scanner_title
import com.hopcape.odo.feature.onboarding.resources.onb_video_cta
import com.hopcape.odo.feature.onboarding.resources.onb_video_next
import com.hopcape.odo.feature.onboarding.resources.onb_video_skip
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The video intro: one page per feature, each a looping clip over the pitch for it.
 *
 * **The clip is decoration.** It is streamed from a remote URL, so a first launch with no
 * network — or a build with no clips configured — has nothing to show. Every page keeps its
 * title, its copy and a working button in that case; the video area collapses to the
 * accent-tinted panel it would have sat on. Nothing about reaching car setup depends on a
 * download.
 */
@Composable
internal fun WelcomeVideoScreen(
    pages: List<VideoPage>,
    onEvent: (WelcomeVideoEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val onLastPage = pagerState.currentPage == pages.lastIndex

    OdoLightSystemBars()

    // A Box, not a Column with a header row: the clip has to start at y=0 and run up under
    // the status bar, so nothing above it may consume the top inset. Skip is drawn over it
    // instead of above it, and takes the inset itself.
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Only the bottom. A CTA under the gesture pill cannot be pressed; the top
                // is deliberately left alone.
                .navigationBarsPadding(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { index ->
                PageClip(pages[index])
            }

            PageCopy(pages[pagerState.settledPage])

            Spacer(Modifier.height(OdoTheme.spacing.lg))

            PageDots(count = pages.size, selected = pagerState.currentPage)

            Spacer(Modifier.height(OdoTheme.spacing.md))

            OdoButton(
                text = stringResource(
                    if (onLastPage) Res.string.onb_video_cta else Res.string.onb_video_next,
                ),
                onClick = {
                    if (onLastPage) {
                        onEvent(WelcomeVideoEvent.NextClicked)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = OdoTheme.spacing.screenEdge
                ).padding(
                    top = OdoTheme.spacing.lg,
                    bottom = OdoTheme.spacing.lg
                ).accentGlow(),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = OdoTheme.spacing.screenEdge),
        ) {
            OdoButton(
                text = stringResource(Res.string.onb_video_skip),
                onClick = { onEvent(WelcomeVideoEvent.SkipClicked) },
                variant = OdoButtonVariant.Tertiary,
            )
        }
    }
}

@Composable
private fun PageClip(page: VideoPage) {
    val videoState = rememberOdoVideoState()

    Box(
        // A share of the page rather than "whatever is left". Left to a weight the clip grew
        // to fill the screen and squeezed the copy against the bottom.
        modifier = Modifier.fillMaxWidth().fillMaxHeight(VideoHeightFraction),
    ) {
        // Not "if the URL is blank, skip the player": the player answers for a blank URL
        // too, and routing both cases through it keeps one definition of "there is no clip"
        // instead of two that can disagree.
        if (!videoState.hasFailed) {
            OdoVideoPlayer(
                url = page.videoUrl,
                state = videoState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Title and body, laid out the way every other onboarding step lays them out —
 * screen-edge padding, `sm` between the two.
 */
@Composable
private fun PageCopy(page: VideoPage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = OdoTheme.spacing.screenEdge,
                vertical = OdoTheme.spacing.xxl
            ),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(
            modifier = Modifier
                .height(48.dp)
        )
        OdoText(
            stringResource(page.title),
            style = OdoTheme.typography.title,
            color = OdoTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        OdoText(
            stringResource(page.body),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = OdoTheme.spacing.xs)
                    .size(if (index == selected) DotSelected else DotIdle)
                    .clip(CircleShape)
                    .background(
                        if (index == selected) OdoTheme.colors.accent else OdoTheme.colors.textMuted,
                    ),
            )
        }
    }
}


/**
 * How much of the page the clip takes. Roughly a quarter less than filling the space left
 * over, which is what it did before — that left the copy squeezed against the bottom.
 */
private const val VideoHeightFraction = 0.55f

private val DotSelected = 10.dp
private val DotIdle = 6.dp

/**
 * The layout with no clips, which is what a preview can actually render — a preview has no
 * player and no network, so a real URL would draw nothing here and say nothing true.
 *
 * It is also the state worth looking at most often: the empty frame is what a first launch
 * with no network gets, and it has to look deliberate rather than broken.
 *
 * Both themes are rendered, and both should look identical: this screen commits to black
 * whatever the theme says.
 */
@OdoThemePreviews
@Composable
private fun WelcomeVideoScreenPreview() = OdoPreview(padded = false) {
    WelcomeVideoScreen(
        pages = listOf(
            VideoPage(
                videoUrl = "",
                title = Res.string.onb_video_refuel_title,
                body = Res.string.onb_video_refuel_body,
            ),
            VideoPage(
                videoUrl = "",
                title = Res.string.onb_video_scanner_title,
                body = Res.string.onb_video_scanner_body,
            ),
        ),
        onEvent = {},
    )
}
