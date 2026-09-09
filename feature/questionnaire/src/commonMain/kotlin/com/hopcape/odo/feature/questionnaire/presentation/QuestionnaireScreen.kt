package com.hopcape.odo.feature.questionnaire.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoOptionCard
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.questionnaire.Question
import com.hopcape.odo.feature.questionnaire.SelectionMode
import com.hopcape.odo.feature.questionnaire.odoQuestions
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.qn_continue
import com.hopcape.odo.feature.questionnaire.resources.qn_screen_title
import com.hopcape.odo.feature.questionnaire.resources.qn_selected
import org.jetbrains.compose.resources.stringResource

/**
 * The questions, one card per option. Stateless: renders [state] and forwards events.
 *
 * Every question is drawn the same way whether it takes one answer or several — the mode only
 * changes what a tap does and what a screen reader announces.
 */
@Composable
internal fun QuestionnaireScreen(
    state: QuestionnaireUiState,
    onEvent: (QuestionnaireEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        title = stringResource(Res.string.qn_screen_title),
        onBack = { onEvent(QuestionnaireEvent.BackClicked) },
        modifier = modifier.testTag(QuestionnaireTestTags.SCREEN),
        bottomBar = {
            OdoButton(
                text = stringResource(Res.string.qn_continue),
                onClick = { onEvent(QuestionnaireEvent.ContinueClicked) },
                enabled = state.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(OdoTheme.spacing.lg)
                    .testTag(QuestionnaireTestTags.CONTINUE),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            OdoLoadingIndicator(modifier = Modifier.fillMaxSize())
            return@OdoScreen
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xl),
        ) {
            state.questions.forEach { question ->
                QuestionBlock(
                    question = question,
                    selected = state.selected(question.key),
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    question: Question,
    selected: Set<String>,
    onEvent: (QuestionnaireEvent) -> Unit,
) {
    val selectedDescription = stringResource(Res.string.qn_selected)
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoText(
            text = stringResource(question.title),
            style = OdoTheme.typography.title,
            color = OdoTheme.colors.text,
        )
        question.subtitle?.let {
            OdoText(
                text = stringResource(it),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )
        }
        question.options.forEach { option ->
            OdoOptionCard(
                label = stringResource(option.label),
                icon = option.icon,
                selected = option.value in selected,
                onClick = { onEvent(QuestionnaireEvent.OptionToggled(question.key, option.value)) },
                multiSelect = question.selection == SelectionMode.MULTI,
                selectedContentDescription = selectedDescription,
                modifier = Modifier.testTag(QuestionnaireTestTags.option(option.value)),
            )
        }
    }
}

@OdoThemePreviews
@Composable
private fun QuestionnairePreview() = OdoPreview(padded = false) {
    val questions = odoQuestions().questions
    QuestionnaireScreen(
        state = QuestionnaireUiState(
            questions = questions,
            answers = mapOf(questions.first().key to setOf(questions.first().options.first().value)),
            isLoading = false,
        ),
        onEvent = {},
    )
}
