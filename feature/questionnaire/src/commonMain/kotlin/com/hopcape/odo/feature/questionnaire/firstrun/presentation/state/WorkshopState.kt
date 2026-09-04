package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

import androidx.compose.runtime.Immutable

/**
 * Where the owner gets the car serviced.
 *
 * [tier] holds the stored constant name rather than a presentation enum, for the same
 * reason [ProfileState.goals] does: the cards come from `QuestionRegistry`, which already
 * keeps the copy and the stored value apart.
 */
@Immutable
internal data class WorkshopState(val tier: String? = null) {
    val isAnswered: Boolean get() = tier != null
}
