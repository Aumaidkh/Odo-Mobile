package com.hopcape.odo.feature.onboarding.presentation.welcome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_continue
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_fair_body
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_fair_title
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_get_started
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_hidden_body
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_hidden_title
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_overpay_body
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_overpay_title
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_skip
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue

/**
 * TODO(ui): The intro carousel — swipeable + parallax, but with placeholder slide
 * content.
 *
 * The pager/parallax mechanics and the ViewModel wiring are real; only each slide's
 * *artwork* is a placeholder (title + body). The designed version layers the full
 * visuals per [WelcomePage] (ODO logo ring + car, the overcharge testimonial card,
 * the 3-step how-it-works row) into [WelcomeSlide], reusing the same
 * `parallax(pageOffset, …)` helper so every layer keeps drifting at its own depth.
 *
 * There is intentionally **no** page-indicator here: two 3-dot indicators (this
 * carousel + the setup wizard) read as one confusing 6-step flow, so the intro's CTA
 * flipping to "Get Started" on the last slide is the only progress signal.
 *
 * State ↔ pager is a two-way binding: a swipe that settles reports
 * [WelcomeEvent.PageChanged]; a CTA tap moves [WelcomeUiState.currentIndex], which
 * animates the pager to match. Pure function of state + [onEvent].
 */
@Composable
internal fun WelcomeScreen(
    state: WelcomeUiState,
    onEvent: (WelcomeEvent) -> Unit,
) {
    // Initial page comes from state so a back-return (VM retained on the back stack)
    // resumes on the slide the user left on — the last one — with no scroll animation.
    val pagerState = rememberPagerState(
        initialPage = state.currentIndex,
        pageCount = { state.pageCount },
    )

    // A settled swipe is the user changing slides — mirror it into state.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { page -> onEvent(WelcomeEvent.PageChanged(page)) }
    }
    // A CTA tap advances state.currentIndex — bring the pager along to match.
    LaunchedEffect(state.currentIndex) {
        if (pagerState.currentPage != state.currentIndex) {
            pagerState.animateScrollToPage(state.currentIndex)
        }
    }

    OdoScreen(
        topBar = { WelcomeTopBar(onSkip = { onEvent(WelcomeEvent.SkipClicked) }) },
        bottomBar = { WelcomeFooter(state, onEvent) },
        contentPadding = PaddingValues(0.dp),
    ) { _ ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            // 0 for the settled page; ±fraction while its neighbours are dragged in.
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            WelcomeSlide(page = state.pages[page], pageOffset = pageOffset)
        }
    }
}

/** One intro slide. Each layer drifts at its own [parallax] depth as [pageOffset] changes. */
@Composable
private fun WelcomeSlide(page: WelcomePage, pageOffset: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(vertical = OdoTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // TODO(ui): per-slide artwork goes here, each layer with its own depth, e.g.
        //   ArtworkLayer(Modifier.parallax(pageOffset, depth = 0.05f))  // background
        //   ArtworkLayer(Modifier.parallax(pageOffset, depth = 0.20f))  // foreground
        OdoText(
            text = stringResource(page.titleRes),
            style = OdoTheme.typography.title,
            color = OdoTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().parallax(pageOffset, depth = 0.30f),
        )
        OdoText(
            text = stringResource(page.bodyRes),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().parallax(pageOffset, depth = 0.55f),
        )
    }
}

/** Top row: right-aligned "Skip" (per the design); slide branding is part of the artwork. */
@Composable
private fun WelcomeTopBar(onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(
            text = stringResource(Res.string.onb_welcome_skip),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(OdoTheme.spacing.xs),
        )
    }
}

/** Pinned CTA: "Continue" through the slides, "Get Started" on the last (finishes the intro). */
@Composable
private fun WelcomeFooter(
    state: WelcomeUiState,
    onEvent: (WelcomeEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.md, bottom = OdoTheme.spacing.lg),
    ) {
        OdoButton(
            text = stringResource(
                if (state.isLastPage) Res.string.onb_welcome_get_started else Res.string.onb_continue,
            ),
            onClick = { onEvent(WelcomeEvent.NextClicked) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Horizontal parallax: shift a layer by a fraction ([depth]) of its own width as the
 * page is dragged, and fade it as it leaves centre. Larger [depth] = more travel =
 * reads as nearer the viewer. Sized off the layer itself, so it composes anywhere.
 */
private fun Modifier.parallax(pageOffset: Float, depth: Float): Modifier =
    this.graphicsLayer {
        translationX = size.width * depth * pageOffset
        alpha = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
    }

/** Slide → headline string (the real UI adds per-slide artwork on top of this). */
private val WelcomePage.titleRes: StringResource
    get() = when (this) {
        WelcomePage.OVERPAYING -> Res.string.onb_welcome_overpay_title
        WelcomePage.HIDDEN_CHARGES -> Res.string.onb_welcome_hidden_title
        WelcomePage.FAIRNESS -> Res.string.onb_welcome_fair_title
    }

/** Slide → supporting-copy string. */
private val WelcomePage.bodyRes: StringResource
    get() = when (this) {
        WelcomePage.OVERPAYING -> Res.string.onb_welcome_overpay_body
        WelcomePage.HIDDEN_CHARGES -> Res.string.onb_welcome_hidden_body
        WelcomePage.FAIRNESS -> Res.string.onb_welcome_fair_body
    }
