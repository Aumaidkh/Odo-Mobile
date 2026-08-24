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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.platform.video.OdoVideoPlayer
import com.hopcape.odo.core.platform.video.rememberOdoVideoState
import com.hopcape.odo.feature.onboarding.resources.Res
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OdoTheme.colors.bg)
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
            modifier = Modifier.weight(1f),
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(OdoTheme.spacing.md))
                .background(OdoTheme.colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            // Not "if the URL is blank, skip the player": the player answers for a blank URL
            // too, and routing both cases through it keeps one definition of "there is no
            // clip" instead of two that can disagree.
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
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(OdoTheme.spacing.sm))

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

private val DotSelected = 10.dp
private val DotIdle = 6.dp
