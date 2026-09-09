package com.hopcape.odo.feature.questionnaire.firstrun.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoOptionCard
import com.hopcape.odo.feature.questionnaire.Question
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.feature.questionnaire.SelectionMode
import com.hopcape.odo.feature.questionnaire.odoQuestions
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingEvent
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.OnboardingStepScaffold
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.StepHeadline
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.ProfileState
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.sampleProfile
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.text
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.onb_cd_goal_selected
import com.hopcape.odo.feature.questionnaire.resources.onb_cd_name_valid
import com.hopcape.odo.feature.questionnaire.resources.onb_continue
import com.hopcape.odo.feature.questionnaire.resources.onb_profile_goal_label
import com.hopcape.odo.feature.questionnaire.resources.onb_profile_name_label
import com.hopcape.odo.feature.questionnaire.resources.onb_profile_name_placeholder
import com.hopcape.odo.feature.questionnaire.resources.onb_profile_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_profile_title
import org.jetbrains.compose.resources.stringResource

/**
 * Step 3 — a name to greet the owner by, and the one thing they came for. No email, no
 * address: the subtitle says so out loud, because the usual sign-up form is exactly what
 * makes people abandon first-run.
 *
 * The goal is asked as three plain outcomes rather than features. It is stored on the
 * profile and read by whoever wants it; it no longer decides where the app opens.
 *
 * Stateless: renders [profile] and forwards [OnboardingEvent]s.
 */
@Composable
internal fun ProfileStepScreen(
    profile: ProfileState,
    canContinue: Boolean,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
    goalQuestion: Question = odoQuestions().require(QuestionKeys.Goal),
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
            modifier = Modifier.testTag(OnboardingTestTags.NAME_FIELD),
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
            goalQuestion.options.forEach { option ->
                OdoOptionCard(
                    label = stringResource(option.label),
                    icon = option.icon,
                    selected = option.value in profile.goals,
                    onClick = { onEvent(OnboardingEvent.Profile.GoalToggled(option.value)) },
                    multiSelect = goalQuestion.selection == SelectionMode.MULTI,
                    selectedContentDescription = stringResource(Res.string.onb_cd_goal_selected),
                )
            }
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
