package com.hopcape.odo.feature.questionnaire.firstrun.presentation.workshop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoOptionCard
import com.hopcape.odo.core.designsystem.component.OdoOptionCardStyle
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.feature.questionnaire.Question
import com.hopcape.odo.feature.questionnaire.odoQuestions
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingEvent
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.OnboardingStepScaffold
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.StepHeadline
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.stepEyebrow
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.WorkshopState
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.onb_cd_workshop_selected
import com.hopcape.odo.feature.questionnaire.resources.onb_continue
import org.jetbrains.compose.resources.stringResource

/**
 * Step 3 — where the car is serviced.
 *
 * The highest value per unit of friction in the flow: one tap, and every price comparison
 * afterwards resolves its labour rate against the right kind of workshop. Without it an
 * authorised centre always reads "over" and a local garage always reads "under".
 *
 * The question comes from the registry rather than being written here, so changing the
 * wording never touches what gets stored.
 *
 * Stateless: renders [workshop] and forwards [OnboardingEvent]s.
 */
@Composable
internal fun WorkshopStepScreen(
    workshop: WorkshopState,
    canContinue: Boolean,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
    question: Question = odoQuestions().require(QuestionKeys.Workshop),
) {
    OnboardingStepScaffold(
        step = OnboardingStep.WORKSHOP.position,
        primaryLabel = stringResource(Res.string.onb_continue),
        onPrimary = { onEvent(OnboardingEvent.ContinueClicked) },
        modifier = modifier,
        onBack = { onEvent(OnboardingEvent.BackClicked) },
        primaryEnabled = canContinue,
    ) {
        StepHeadline(
            title = stringResource(question.title),
            subtitle = question.subtitle?.let { stringResource(it) }.orEmpty(),
            eyebrow = stepEyebrow(OnboardingStep.WORKSHOP),
        )

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            question.options.forEach { option ->
                OdoOptionCard(
                    label = stringResource(option.label),
                    selected = option.value == workshop.tier,
                    onClick = { onEvent(OnboardingEvent.Workshop.TierSelected(option.value)) },
                    description = option.description?.let { stringResource(it) },
                    style = OdoOptionCardStyle.Filled,
                    selectedContentDescription = stringResource(Res.string.onb_cd_workshop_selected),
                )
            }
        }
    }
}

@OdoThemePreviews
@Composable
private fun WorkshopStepPreview() = OdoPreview(padded = false) {
    WorkshopStepScreen(
        workshop = WorkshopState(tier = WorkshopTier.AUTHORISED.name),
        canContinue = true,
        onEvent = {},
    )
}

@OdoThemePreviews
@Composable
private fun WorkshopStepEmptyPreview() = OdoPreview(padded = false) {
    WorkshopStepScreen(workshop = WorkshopState(), canContinue = false, onEvent = {})
}
