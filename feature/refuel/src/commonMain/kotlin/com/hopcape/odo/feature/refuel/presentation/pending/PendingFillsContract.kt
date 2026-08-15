package com.hopcape.odo.feature.refuel.presentation.pending

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.navigation.FuelFillDraftInput

/** What the owner did on the "fills you haven't confirmed" sheet. */
internal sealed interface PendingFillsEvent {

    /** Open one of them in the confirm step, to check the numbers before it is written. */
    data class ReviewTapped(val id: String) : PendingFillsEvent

    /** "That wasn't fuel" on one row. Nothing is written, and it stops being asked about. */
    data class DismissTapped(val id: String) : PendingFillsEvent

    /** Close the sheet, leaving everything unanswered for next time. */
    data object CloseTapped : PendingFillsEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface PendingFillsEffect {

    /** Carry one pending detection into the confirm surface. */
    data class Review(val draft: FuelFillDraftInput) : PendingFillsEffect

    data object Dismiss : PendingFillsEffect
}

/**
 * The sheet's state.
 *
 * It exists for one situation: a fill was detected while the owner was not looking, and the
 * notification is long gone. Every row here is a question Odo could not get an answer to at
 * the time, held until it can.
 *
 * The sheet closes itself once the list empties — answering the last one is the end of the
 * conversation, and a sheet that stayed open on nothing would need a second dismissal.
 */
@Immutable
internal data class PendingFillsUiState(
    val loading: Boolean = true,
    val fills: List<PendingFillRow> = emptyList(),
)

/**
 * One unanswered detection, already rendered.
 *
 * The payload travels with it so reviewing a row needs no second read: the sheet may be open
 * for a while, and the row the owner taps is the one they were shown.
 */
@Immutable
internal data class PendingFillRow(
    val id: String,
    val merchant: String,
    val amountLabel: String,
    val whenLabel: String,
    val draftPayload: String,
)
