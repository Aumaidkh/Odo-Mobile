package com.hopcape.odo.feature.billscanner.presentation.document

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.billscanner.presentation.state.Submission
import kotlinx.datetime.LocalDate

/**
 * Display state for the step that confirms a scanned paper before it is filed.
 *
 * This screen exists because of what the document vault could not do: its add flow had no way
 * to capture an expiry date at all, so a filed paper produced no reminder. Reading the date
 * off the photo is what closes that, and confirming it is what keeps a misread date from
 * quietly becoming the day someone believes they are covered until.
 */
@Immutable
internal data class DocumentReviewUiState(
    val submission: Submission = Submission.InFlight,
    val type: DocumentType = DocumentType.INSURANCE,
    val title: String = "",
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val photoKey: String? = null,
) {
    /** Whether the screen is still waiting on the extraction. */
    val isReading: Boolean get() = submission.isInFlight && expiresOn == null

    /**
     * A document with no expiry cannot produce a reminder, which is most of what the vault is
     * for — so the save waits until there is one.
     */
    val canSave: Boolean get() = !submission.isInFlight && expiresOn != null
}

/** What the owner did on the confirm screen, as data. */
internal sealed interface DocumentReviewEvent {
    data class TypeChanged(val type: DocumentType) : DocumentReviewEvent
    data class TitleChanged(val value: String) : DocumentReviewEvent
    data class IssuedOnChanged(val value: LocalDate) : DocumentReviewEvent
    data class ExpiresOnChanged(val value: LocalDate) : DocumentReviewEvent
    data object SaveTapped : DocumentReviewEvent
    data object BackTapped : DocumentReviewEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface DocumentReviewEffect {

    /** The paper was filed; the vault shows it. */
    data class OpenDocument(val documentId: String) : DocumentReviewEffect

    data object NavigateBack : DocumentReviewEffect
}
