package com.hopcape.odo.feature.onboarding.presentation.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcCurrencyDollar
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.icons.IcTagFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.feature.onboarding.presentation.OnboardingEvent
import com.hopcape.odo.feature.onboarding.presentation.components.IconTile
import com.hopcape.odo.feature.onboarding.presentation.components.OnboardingStepScaffold
import com.hopcape.odo.feature.onboarding.presentation.components.StepHeadline
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingGoalOption
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.state.ProfileState
import com.hopcape.odo.feature.onboarding.presentation.state.sampleProfile
import com.hopcape.odo.feature.onboarding.presentation.state.text
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_cd_goal_selected
import com.hopcape.odo.feature.onboarding.resources.onb_cd_name_valid
import com.hopcape.odo.feature.onboarding.resources.onb_continue
import com.hopcape.odo.feature.onboarding.resources.onb_goal_healthy
import com.hopcape.odo.feature.onboarding.resources.onb_goal_overpay
import com.hopcape.odo.feature.onboarding.resources.onb_goal_resale
import com.hopcape.odo.feature.onboarding.resources.onb_profile_goal_label
import com.hopcape.odo.feature.onboarding.resources.onb_profile_name_label
import com.hopcape.odo.feature.onboarding.resources.onb_profile_name_placeholder
import com.hopcape.odo.feature.onboarding.resources.onb_profile_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_profile_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Step 3 — a name to greet the owner by, and the one thing they came for. No email, no
 * address: the subtitle says so out loud, because the usual sign-up form is exactly what
 * makes people abandon first-run.
 *
 * The goal is not a preference — it decides where the app opens after setup
 * (`StartDestinationMapping`), so it is asked as three plain outcomes rather than features.
 *
 * Stateless: renders [profile] and forwards [OnboardingEvent]s.
 */
@Composable
internal fun ProfileStepScreen(
    profile: ProfileState,
    canContinue: Boolean,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepScaffold(
        step = OnboardingStep.PROFILE.position,
        primaryLabel = stringResource(Res.string.onb_continue),
        onPrimary = { onEvent(OnboardingEvent.ContinueClicked) },
        modifier = modifier,
        onBack = { onEvent(OnboardingEvent.BackClicked) },
        primaryEnabled = canContinue,
    ) {
        StepHeadline(
            title = stringResource(Res.string.onb_profile_title),
            subtitle = stringResource(Res.string.onb_profile_subtitle),
        )

        OdoInputField(
            value = profile.name.text,
            onValueChange = { onEvent(OnboardingEvent.Profile.NameChanged(it)) },
            label = stringResource(Res.string.onb_profile_name_label),
            placeholder = stringResource(Res.string.onb_profile_name_placeholder),
            // Nothing sets a name error until the profile is actually saved; the slot is
            // wired now so that when it is, the message lands next to the field it describes.
            errorText = profile.name.error?.asString(),
            trailingIcon = if (profile.isNameValid) {
                {
                    OdoIcon(
                        IcCheck,
                        contentDescription = stringResource(Res.string.onb_cd_name_valid),
                        tint = OdoTheme.colors.accent,
                        size = OdoTheme.iconSizes.medium,
                    )
                }
            } else {
                null
            },
        )

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            OdoText(
                text = stringResource(Res.string.onb_profile_goal_label),
                style = OdoTheme.typography.label,
                color = OdoTheme.colors.textDim,
            )
            GOALS.forEach { goal ->
                GoalCard(
                    goal = goal,
                    selected = profile.goal.value == goal.option,
                    onClick = { onEvent(OnboardingEvent.Profile.GoalSelected(goal.option)) },
                )
            }
        }
    }
}

/* ------------------------------ Goals ------------------------------ */

private data class Goal(val option: OnboardingGoalOption, val icon: ImageVector, val label: StringResource)

private val GOALS = listOf(
    Goal(OnboardingGoalOption.STOP_OVERPAYING, IcCurrencyDollar, Res.string.onb_goal_overpay),
    Goal(OnboardingGoalOption.STAY_HEALTHY, IcSpeedometer, Res.string.onb_goal_healthy),
    Goal(OnboardingGoalOption.SELL_FOR_MORE, IcTagFilled, Res.string.onb_goal_resale),
)

/** One goal, as a full-width tappable card — selection is an accent wash plus a check. */
@Composable
private fun GoalCard(goal: Goal, selected: Boolean, onClick: () -> Unit) {
    val colors = OdoTheme.colors
    val shape = OdoTheme.shapes.card
    val border by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.border,
        animationSpec = tween(OdoTheme.motion.baseMillis, easing = OdoTheme.motion.easeStandard),
        label = "goalBorder",
    )
    val container by animateColorAsState(
        targetValue = if (selected) colors.accent.copy(alpha = 0.10f) else colors.surface,
        animationSpec = tween(OdoTheme.motion.baseMillis, easing = OdoTheme.motion.easeStandard),
        label = "goalContainer",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = goal.icon,
            tint = if (selected) colors.accent else colors.textDim,
        )
        OdoText(
            text = stringResource(goal.label),
            style = OdoTheme.typography.heading,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            OdoIcon(
                IcCheck,
                contentDescription = stringResource(Res.string.onb_cd_goal_selected),
                tint = colors.accent,
                size = OdoTheme.iconSizes.large,
            )
        }
    }
}

@OdoThemePreviews
@Composable
private fun ProfileStepPreview() = OdoPreview(padded = false) {
    ProfileStepScreen(profile = sampleProfile(), canContinue = true, onEvent = {})
}

@OdoThemePreviews
@Composable
private fun ProfileStepEmptyPreview() = OdoPreview(padded = false) {
    ProfileStepScreen(profile = ProfileState(), canContinue = false, onEvent = {})
}
