package com.hopcape.odo.feature.servicelog.presentation.form

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import kotlinx.datetime.LocalDate

internal sealed interface ServiceLogFormEvent {
    data class WorkshopChanged(val value: String) : ServiceLogFormEvent
    data class DateChanged(val value: LocalDate) : ServiceLogFormEvent
    data class OdometerChanged(val value: String) : ServiceLogFormEvent
    data class NotesChanged(val value: String) : ServiceLogFormEvent
    data class AmountChanged(val value: String) : ServiceLogFormEvent
    /** Toggle a "what was done" category chip. */
    data class CategoryToggled(val category: ServiceCategory) : ServiceLogFormEvent
    data object ScanClicked : ServiceLogFormEvent // coming soon (M2)
    data object Save : ServiceLogFormEvent
    data object DeleteClicked : ServiceLogFormEvent
    data object ConfirmDelete : ServiceLogFormEvent
    data object DismissDelete : ServiceLogFormEvent
    data object Back : ServiceLogFormEvent
}

internal sealed interface ServiceLogFormEffect {
    data object Saved : ServiceLogFormEffect
    data object Deleted : ServiceLogFormEffect
    data object Back : ServiceLogFormEffect
}
