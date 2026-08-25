package com.hopcape.odo.feature.onboarding.presentation.video

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoDeviceFrame
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

    // Black whatever the theme says, so the phone in the frame is the only lit thing on
    // screen. That is also why the icons have to be forced light: the system would
    // otherwise draw dark ones in light mode, onto black.
    OdoLightSystemBars()

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(OdoTheme.spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OdoButton(
                text = stringResource(Res.string.onb_video_skip),
                onClick = { onEvent(WelcomeVideoEvent.SkipClicked) },
                variant = OdoButtonVariant.Tertiary,
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(0.5f),
        ) { index ->
            VideoPageContent(pages[index])
        }

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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VideoPageContent(page: VideoPage) {
    val videoState = rememberOdoVideoState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OdoDeviceFrame(
            modifier = Modifier
                .fillMaxWidth(DeviceWidthFraction)
                .weight(1f),
        ) {
            // Not "if the URL is blank, skip the player": the player answers for a blank URL
            // too, and routing both cases through it keeps one definition of "there is no
            // clip" instead of two that can disagree. When there is no clip the frame is
            // still a phone — an empty screen, which reads as deliberate.
            if (!videoState.hasFailed) {
                OdoVideoPlayer(
                    url = page.videoUrl,
                    state = videoState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(OdoTheme.spacing.lg))

        OdoText(
            stringResource(page.title),
            style = OdoTheme.typography.title,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(OdoTheme.spacing.sm))

        OdoText(
            stringResource(page.body),
            style = OdoTheme.typography.body,
            color = MutedOnBlack,
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
                        if (index == selected) OdoTheme.colors.accent else MutedOnBlack,
                    ),
            )
        }
    }
}


/** Narrower than the screen so the black frames it, the way a hero shot is framed. */
private const val DeviceWidthFraction = 0.66f

/** The theme's muted text is tuned for the theme's background, not for black. */
private val MutedOnBlack = Color(0xFFA0A0A6)

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
