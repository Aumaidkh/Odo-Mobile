package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.runtime.Immutable
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.Submission

/**
 * The car as the three car sheets need it: a name to put at the top, and what is on file
 * under it. All three read the same garage snapshot, so they cannot disagree about how much
 * a removal would cost.
 */
@Immutable
internal data class CarSummary(
    val displayName: String,
    /** The plate, or `null` when the car has none on record. */
    val registration: String?,
    val serviceCount: Int,
    val documentCount: Int,
)

/* ------------------------------ Car actions ------------------------------ */

/** What the owner did on the car-actions sheet (the ⋮ menu). */
internal sealed interface CarActionsEvent {
    data object EditTapped : CarActionsEvent
    data object ExportTapped : CarActionsEvent
    data object RemoveTapped : CarActionsEvent
}

internal sealed interface CarActionsEffect {
    data object OpenEdit : CarActionsEffect
    data object OpenExport : CarActionsEffect
    data object OpenRemove : CarActionsEffect
}

@Immutable
internal data class CarActionsUiState(val car: Loadable<CarSummary> = Loadable.Loading)

/* ------------------------------ Remove car ------------------------------ */

/** What the owner did on the remove-car confirmation. */
internal sealed interface RemoveCarEvent {
    data object ExportFirstTapped : RemoveCarEvent
    data object RemoveTapped : RemoveCarEvent
    data object CancelTapped : RemoveCarEvent
}

internal sealed interface RemoveCarEffect {
    data object OpenExport : RemoveCarEffect
    data object Removed : RemoveCarEffect
    data object Dismiss : RemoveCarEffect
}

@Immutable
internal data class RemoveCarUiState(
    val car: Loadable<CarSummary> = Loadable.Loading,
    val submission: Submission = Submission.Idle,
)

/* ------------------------------ Export ------------------------------ */

/** What the owner did on the export sheet. */
internal sealed interface ExportEvent {
    data object PdfTapped : ExportEvent
    data object ShareTapped : ExportEvent
}

/**
 * Export is the Resale Passport's job, and that is a paid feature that does not exist yet,
 * so both actions lead to the paywall rather than producing a half-record the owner would
 * reasonably treat as proof.
 */
internal sealed interface ExportEffect {
    data class OpenPaywall(val trigger: String) : ExportEffect
}

@Immutable
internal data class ExportUiState(val car: Loadable<CarSummary> = Loadable.Loading)
