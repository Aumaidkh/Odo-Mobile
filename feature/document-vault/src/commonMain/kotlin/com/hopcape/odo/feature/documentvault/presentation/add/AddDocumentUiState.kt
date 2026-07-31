package com.hopcape.odo.feature.documentvault.presentation.add

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.documentvault.presentation.state.Submission

/**
 * Display state for the "Add document" screen.
 *
 * Only the type is a selection. The capture method is an action, so it is not held here.
 * [selectedType] can be pre-filled from the vault row the owner tapped "Add" on.
 *
 * [submission] covers the save that follows a capture: while it is in flight the capture
 * cards are disabled, so one tap cannot start two adds.
 */
@Immutable
internal data class AddDocumentUiState(
    val selectedType: DocumentType = DocumentType.INSURANCE,
    val submission: Submission = Submission.Idle,
) {
    val isSaving: Boolean get() = submission.isInFlight

    companion object {
        /**
         * The types the picker offers, in display order.
         *
         * Every one the vault can show, so no document the owner holds is unreachable from
         * here. `OTHER` last, because it is the fallback rather than a choice.
         */
        val OFFERED_TYPES = listOf(
            DocumentType.INSURANCE,
            DocumentType.PUC,
            DocumentType.RC,
            DocumentType.LICENCE,
            DocumentType.LOAN,
            DocumentType.OTHER,
        )
    }
}
