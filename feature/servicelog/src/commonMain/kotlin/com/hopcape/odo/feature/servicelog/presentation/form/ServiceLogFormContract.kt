package com.hopcape.odo.feature.servicelog.presentation.form

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import kotlinx.datetime.LocalDate

/**
 * What the owner did on the add/edit form.
 *
 * The field edits are nested under [Field] so the form's many small events stay one group:
 * the ViewModel dispatches on the group and writes state, and adding a field to the form
 * leaves the rest of the contract alone.
 */
internal sealed interface ServiceLogFormEvent {

    sealed interface Field : ServiceLogFormEvent {
        data class WorkshopChanged(val value: String) : Field
        data class DateChanged(val date: LocalDate) : Field
        data class OdometerChanged(val value: String) : Field

        /** Km ⇄ Miles. The reading is stored in km either way (see [toAddCommand]). */
        data class AmountChanged(val value: String) : Field
        data class NotesChanged(val value: String) : Field
        data class CategoryToggled(val category: ServiceCategory) : Field
    }

    /** "Scan the bill instead" — the fast path into the AI Bill Scanner. */
    data object ScanBillClicked : ServiceLogFormEvent

    /** The optional "attach a bill photo" slot — what earns the entry its Verified badge. */
    data object AttachBillClicked : ServiceLogFormEvent

    data object SaveClicked : ServiceLogFormEvent
    data object CloseClicked : ServiceLogFormEvent
}

/** One-shot handoffs, performed by the route host. */
internal sealed interface ServiceLogFormEffect {

    /**
     * The entry is stored. Carries its id so a caller can go on to the entry itself; the
     * list behind the form observes the repository, so it needs nothing from here.
     */
    data class Saved(val id: ServiceLogId) : ServiceLogFormEffect

    data object OpenBillScanner : ServiceLogFormEffect

    /** Pick/capture a bill photo for this entry. */
    data object AttachBill : ServiceLogFormEffect

    data object NavigateBack : ServiceLogFormEffect
}
