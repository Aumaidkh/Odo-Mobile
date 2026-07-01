package com.hopcape.odo.feature.onboarding.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEvent
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingUiState
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_goal_remind_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_goal_remind_title
import com.hopcape.odo.feature.onboarding.resources.onb_goal_sell_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_goal_sell_title
import com.hopcape.odo.feature.onboarding.resources.onb_goal_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_goal_title
import com.hopcape.odo.feature.onboarding.resources.onb_goal_track_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_goal_track_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Step 3 — the "why are you here?" goal picker. Tapping a card only **highlights**
 * it ([OnboardingEvent.GoalSelected]); the footer's "Take me to my car" confirms
 * (`Finish`). The chosen goal routes the user to their starting surface (PRD §5.1).
 */
@Composable
internal fun GoalSelectionStep(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        StepHeading(
            title = stringResource(Res.string.onb_goal_title),
            subtitle = stringResource(Res.string.onb_goal_subtitle),
        )

        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.cardGap),
        ) {
            GoalOptions.forEach { option ->
                GoalCard(
                    icon = option.icon,
                    title = stringResource(option.title),
                    subtitle = stringResource(option.subtitle),
                    selected = state.selectedGoal == option.goal,
                    onClick = { onEvent(OnboardingEvent.GoalSelected(option.goal)) },
                )
            }
        }
    }
}

/** A single selectable goal card: icon tile, title + subtitle, trailing chevron. */
@Composable
private fun GoalCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        onClick = onClick,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else OdoTheme.colors.border,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(OdoTheme.shapes.small)
                    .background(if (selected) accent.copy(alpha = 0.16f) else OdoTheme.colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accent else OdoTheme.colors.textDim,
                    size = OdoTheme.iconSizes.large,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OdoText(title, style = OdoTheme.typography.heading, color = OdoTheme.colors.text)
                OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
            OdoIcon(
                imageVector = OnboardingIcons.ChevronRight,
                contentDescription = null,
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.small,
            )
        }
    }
}

/** Display copy + glyph for each goal, in the order the mockup lists them. */
private data class GoalOption(
    val goal: OnboardingGoal,
    val icon: ImageVector,
    val title: StringResource,
    val subtitle: StringResource,
)

private val GoalOptions: List<GoalOption> = listOf(
    GoalOption(OnboardingGoal.SELL_SOON, OnboardingIcons.Wallet, Res.string.onb_goal_sell_title, Res.string.onb_goal_sell_subtitle),
    GoalOption(OnboardingGoal.TRACK_COSTS, OnboardingIcons.Receipt, Res.string.onb_goal_track_title, Res.string.onb_goal_track_subtitle),
    GoalOption(OnboardingGoal.NEVER_MISS_RENEWAL, OnboardingIcons.Bell, Res.string.onb_goal_remind_title, Res.string.onb_goal_remind_subtitle),
)

@OdoThemePreviews
@Composable
private fun GoalSelectionStepPreview() = OdoPreview(padded = false) {
    OnboardingScreen(state = previewGoalSelectionState(), onEvent = {})
}
