package com.hopcape.odo.feature.questionnaire.presentation

import com.hopcape.odo.core.domain.owner.model.QuestionKey

/** What the questionnaire screen can report. */
sealed interface QuestionnaireEvent {
    /** The owner tapped an option. On a SINGLE question this replaces the answer. */
    data class OptionToggled(val key: QuestionKey, val value: String) : QuestionnaireEvent
    data object ContinueClicked : QuestionnaireEvent
    data object BackClicked : QuestionnaireEvent
}

/** What the route acts on. Carries data, never a nav key. */
sealed interface QuestionnaireEffect {
    /** Every answer is stored. The route decides where that leads. */
    data object Finished : QuestionnaireEffect
    data object NavigateBack : QuestionnaireEffect
    /** A write failed. The answers are still on screen, so the owner can retry. */
    data object SaveFailed : QuestionnaireEffect
}
