package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

import androidx.compose.runtime.Immutable

/**
 * The last thing asked of the owner: a name to greet them by, and why they came.
 *
 * [goals] holds the stored constant names rather than a presentation enum. The cards come
 * from `QuestionRegistry`, which already keeps the copy and the stored value apart, so a
 * second mapping here would be a third name for the same thing.
 */
@Immutable
internal data class ProfileState(
    val name: FormField<String> = FormField(""),
    val goals: Set<String> = emptySet(),
) {
    /** A name that is actually a name — a lone space shouldn't unlock Continue. */
    val isNameValid: Boolean get() = name.text.trim().length >= MIN_NAME_LENGTH

    val isAnswered: Boolean get() = isNameValid && goals.isNotEmpty()

    private companion object {
        const val MIN_NAME_LENGTH = 2
    }
}
