package com.hopcape.odo.feature.questionnaire.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.feature.questionnaire.Question
import com.hopcape.odo.feature.questionnaire.SelectionMode

/**
 * The questions being asked and what is picked so far.
 *
 * [answers] holds a set per question because a MULTI question has several. A SINGLE question
 * keeps a set of one, so the screen has one shape to render either way.
 */
@Immutable
data class QuestionnaireUiState(
    val questions: List<Question> = emptyList(),
    val answers: Map<QuestionKey, Set<String>> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
) {
    fun selected(key: QuestionKey): Set<String> = answers[key].orEmpty()

    /**
     * Every question has an answer.
     *
     * All questions are required for now. When an optional one is declared this becomes a
     * property of [Question] rather than a rule stated here.
     */
    val canContinue: Boolean
        get() = !isLoading && !isSaving &&
            questions.isNotEmpty() &&
            questions.all { selected(it.key).isNotEmpty() }
}

/** Picking on a SINGLE question replaces; on a MULTI one it adds or removes. */
internal fun Set<String>.toggle(value: String, mode: SelectionMode): Set<String> = when (mode) {
    SelectionMode.SINGLE -> setOf(value)
    SelectionMode.MULTI -> if (value in this) this - value else this + value
}
