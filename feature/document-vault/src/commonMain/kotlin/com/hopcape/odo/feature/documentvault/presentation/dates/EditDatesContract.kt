package com.hopcape.odo.feature.documentvault.presentation.dates

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy
import com.hopcape.odo.feature.documentvault.presentation.state.Submission
import kotlinx.datetime.LocalDate

/**
 * Display state for the sheet that corrects a document's dates.
 *
 * The sheet exists for documents already in the vault with no expiry on them — filed before
 * the app read dates, or filed from a file nothing could read. Without a date they produce no
 * reminder, and re-adding the document to fix that is not something an owner should have to
 * work out.
 */
@Immutable
internal data class EditDatesUiState(
    val type: DocumentType,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val submission: Submission = Submission.Idle,
) {
    /** Whether this kind of paper renews at all — an RC and a loan letter do not. */
    val needsExpiry: Boolean get() = DocumentReminderPolicy.leadDaysFor(type).isNotEmpty()

    /** The same rule the confirm step applies: a paper that renews saves with a date on it. */
    val canSave: Boolean get() = !submission.isInFlight && (expiresOn != null || !needsExpiry)

    val error: UiText? get() = submission.error
}

/** What the owner did on the sheet, as data. */
internal sealed interface EditDatesEvent {
    data class IssuedOnChanged(val value: LocalDate) : EditDatesEvent
    data class ExpiresOnChanged(val value: LocalDate) : EditDatesEvent
    data object SaveTapped : EditDatesEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface EditDatesEffect {

    /** The dates were saved; the sheet closes and the detail behind it updates itself. */
    data object Dismiss : EditDatesEffect
}
