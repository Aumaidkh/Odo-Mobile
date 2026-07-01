package com.hopcape.odo.feature.onboarding.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoIconButton
import com.hopcape.odo.core.designsystem.component.OdoPageIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEvent
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingUiState
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_back
import com.hopcape.odo.feature.onboarding.resources.onb_continue
import com.hopcape.odo.feature.onboarding.resources.onb_finish
import com.hopcape.odo.feature.onboarding.resources.onb_scan_bill
import com.hopcape.odo.feature.onboarding.resources.onb_skip
import org.jetbrains.compose.resources.stringResource

/** The onboarding flow is exactly three sequential steps (PRD §5.1). */
internal const val ONBOARDING_STEPS = 3

/**
 * Stateless host for the whole 3-step onboarding flow. It owns the shared chrome —
 * the circular back button, the page dots (which carry step progress), and the
 * pinned bottom CTA — and swaps the body per [OnboardingUiState.step] with a
 * parallax slide. Pure function
 * of state + [onEvent], so it renders in a preview and drives from the ViewModel
 * identically.
 */
@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    // Read once here (composable) so the non-composable transitionSpec can capture it.
    val motion = OdoTheme.motion
    OdoScreen(
        topBar = {
            OnboardingHeader(onBack = { onEvent(OnboardingEvent.Back) })
        },
        bottomBar = { OnboardingFooter(state, onEvent) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(top = OdoTheme.spacing.sm, bottom = OdoTheme.spacing.xl),
        ) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    // Parallax: the incoming step slides fully in from the advancing
                    // side while the outgoing one drifts out a quarter as far — two
                    // layers at different speeds read as depth. Direction = step order.
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    val slide = tween<IntOffset>(motion.baseMillis, easing = motion.easeStandard)
                    val fade = tween<Float>(motion.baseMillis, easing = motion.easeStandard)
                    (slideInHorizontally(slide) { width -> dir * width } + fadeIn(fade))
                        .togetherWith(slideOutHorizontally(slide) { width -> -dir * width / 4 } + fadeOut(fade))
                },
                label = "onboardingStep",
                modifier = Modifier.fillMaxWidth().clipToBounds(),
            ) { step ->
                when (step) {
                    OnboardingStep.CAR_DETAILS -> CarDetailsStep(state, onEvent)
                    OnboardingStep.HISTORY_IMPORT -> HistoryImportStep(state, onEvent)
                    OnboardingStep.GOAL_SELECTION -> GoalSelectionStep(state, onEvent)
                }
            }
        }
    }
}

/** Big screen title + one-line subtitle every step opens with. */
@Composable
internal fun StepHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        OdoText(title, style = OdoTheme.typography.title, color = OdoTheme.colors.text)
        OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
    }
}

/**
 * The flow's custom top bar — just the circular back affordance. Step progress is
 * carried by the page dots in the footer, so no "STEP n OF 3" label is needed here.
 */
@Composable
private fun OnboardingHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm)
            .heightIn(min = OdoTheme.spacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularBackButton(onBack)
    }
}

/** iOS-style circular back button: a "‹" chevron inside a bordered circle. */
@Composable
private fun CircularBackButton(onBack: () -> Unit) {
    val label = stringResource(Res.string.onb_back)
    OdoIconButton(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(OdoTheme.colors.surface)
            .border(1.dp, OdoTheme.colors.border, CircleShape)
            .semantics { contentDescription = label },
        onClick = onBack
    ){
        OdoIcon(
            imageVector = IcArrowLeft,
            contentDescription = ""
        )
    }
}

/** Pinned bottom area: any submit error, the page dots, and the step's call-to-action(s). */
@Composable
private fun OnboardingFooter(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.md, bottom = OdoTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.submitError?.let { error ->
            OdoText(
                text = error.asString(),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OdoPageIndicator(pageCount = ONBOARDING_STEPS, selectedIndex = state.step.ordinal)
        when (state.step) {
            OnboardingStep.CAR_DETAILS -> OdoButton(
                text = stringResource(Res.string.onb_continue),
                onClick = { onEvent(OnboardingEvent.CarDetailsNext) },
                modifier = Modifier.fillMaxWidth(),
            )

            OnboardingStep.HISTORY_IMPORT -> {
                OdoButton(
                    text = stringResource(Res.string.onb_scan_bill),
                    onClick = { onEvent(OnboardingEvent.ScanHistoryClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OdoButton(
                    text = stringResource(Res.string.onb_skip),
                    onClick = { onEvent(OnboardingEvent.SkipHistory) },
                    variant = OdoButtonVariant.Tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OnboardingStep.GOAL_SELECTION -> OdoButton(
                text = stringResource(Res.string.onb_finish),
                onClick = { onEvent(OnboardingEvent.Finish) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.selectedGoal != null,
                loading = state.isSubmitting,
            )
        }
    }
}

@OdoThemePreviews
@Composable
private fun OnboardingScreenPreview() = OdoPreview(padded = false) {
    OnboardingScreen(state = previewCarDetailsState(), onEvent = {})
}
