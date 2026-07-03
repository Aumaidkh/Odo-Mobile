package com.hopcape.odo.feature.servicelog.presentation.detail

import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry

/**
 * Detail render state. [content] is the mutually-exclusive load phase — a sealed type, so
 * illegal combinations ("loading, yet has an entry") can't be represented and the UI's
 * `when` is exhaustive. The delete-overlay and [reported] flags are orthogonal to the load
 * phase (they persist across a re-emit of [content]), so they stay top-level.
 */
internal data class ServiceLogDetailUiState(
    val content: Content = Content.Loading,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    val reported: Boolean = false,
) {
    sealed interface Content {
        data object Loading : Content
        data object NotFound : Content

        /** [fairness] is the entry-level verdict (null for self-reported or no benchmark). */
        data class Loaded(val entry: ServiceLogEntry, val fairness: FairnessVerdict?) : Content
    }
}
