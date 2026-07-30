package com.hopcape.odo.feature.servicelog.presentation.list

import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.servicelog.presentation.list.model.ServiceLogDirection

/**
 * What the owner did on the list, as data.
 *
 * Grouped by what the tap is *about* rather than flattened: [View] changes how the same
 * entries are shown (and is answered by the ViewModel alone), while [Open] leaves for
 * somewhere else (and is answered by an effect). The split is why the ViewModel's `when`
 * is two lines instead of eight.
 */
internal sealed interface ServiceLogListEvent {

    /** How to look at the list — handled entirely in state. */
    sealed interface View : ServiceLogListEvent {
        data class FilterSelected(val filter: ServiceLogFilter) : View
        data class DirectionSelected(val direction: ServiceLogDirection) : View
    }

    /** Somewhere to go — each one becomes a [ServiceLogListEffect]. */
    sealed interface Open : ServiceLogListEvent {
        data class Entry(val id: ServiceLogId) : Open
        data object AddForm : Open
        data object BillScanner : Open
        data object ShareRecord : Open
        data object Filters : Open
        data object Back : Open
    }
}

/**
 * One-shot handoffs the route host performs. Every one is data — the ViewModel decides
 * *what* should happen and the route turns it into a navigation command, which is what
 * keeps presentation free of navigation types.
 */
internal sealed interface ServiceLogListEffect {
    data class OpenEntry(val id: ServiceLogId) : ServiceLogListEffect
    data object OpenAddForm : ServiceLogListEffect

    /** Into the AI Bill Scanner — the product's North Star action (PRD). */
    data object OpenBillScanner : ServiceLogListEffect
    data object OpenShareRecord : ServiceLogListEffect
    data object OpenFilters : ServiceLogListEffect
    data object NavigateBack : ServiceLogListEffect
}
