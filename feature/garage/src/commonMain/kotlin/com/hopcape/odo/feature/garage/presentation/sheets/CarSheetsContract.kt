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
    data object ValueTapped : CarActionsEvent

    /**
     * "Before you go in" — the pre-service checklist.
     *
     * Here as well as on Home because Home's two entries both depend on a service being
     * close. An owner walking into a workshop off-schedule has no other way in.
     */
    data object ChecklistTapped : CarActionsEvent
    data object ExportTapped : CarActionsEvent
    data object RemoveTapped : CarActionsEvent
}

internal sealed interface CarActionsEffect {
    data object OpenEdit : CarActionsEffect
    data object OpenCarValue : CarActionsEffect

    /** Open the pre-service checklist. */
    data object OpenServiceChecklist : CarActionsEffect
    data object OpenExport : CarActionsEffect
    data object OpenRemove : CarActionsEffect
}

@Immutable
internal data class CarActionsUiState(
    val car: Loadable<CarSummary> = Loadable.Loading,
    /** Whether the pre-service checklist row is offered — `service_checklist_enabled`. */
    val showChecklist: Boolean = false,
)

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

/** Which button asked for the document. Both send the same PDF; analytics tells them apart. */
internal enum class ExportVia { PDF, SHARE }

/** What the owner did on the export sheet. */
internal sealed interface ExportEvent {
    data object PdfTapped : ExportEvent
    data object ShareTapped : ExportEvent

    /**
     * The host finished rendering, with the document's bytes or `null` if it could not be
     * produced. Reported back rather than returned, because rendering needs a UI host and
     * the ViewModel does not have one.
     */
    data class Rendered(val bytes: ByteArray?, val via: ExportVia) : ExportEvent {
        // A ByteArray in a data class compares by identity, which would make two different
        // documents look equal and two reads of one look different. Nothing depends on
        // comparing these, so the generated versions are replaced with honest ones.
        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = bytes.contentHashCode() * 31 + via.hashCode()
    }
}

/**
 * One-shot handoffs, performed by the sheet's host — the two things a ViewModel cannot do
 * because both need whatever is hosting the UI.
 */
internal sealed interface ExportEffect {

    /** Lay [html] out and print it. The document arrives fully built. */
    data class RenderDocument(val html: String, val documentName: String, val via: ExportVia) : ExportEffect

    /** Hand the written file to the system share sheet. */
    data class ShareFile(val storageKey: String, val title: String) : ExportEffect
}

/** How far along producing the document is. */
internal sealed interface ExportProgress {

    /** Nothing in progress. Both buttons are tappable. */
    data object Idle : ExportProgress

    /** Rendering, for [via]. The sheet marks that button and disables both. */
    data class Rendering(val via: ExportVia) : ExportProgress

    /**
     * The document could not be produced. Kept in state rather than shown once and lost,
     * so the owner sees why nothing happened instead of a button that did nothing.
     */
    data object Failed : ExportProgress
}

@Immutable
internal data class ExportUiState(
    val car: Loadable<CarSummary> = Loadable.Loading,
    val export: ExportProgress = ExportProgress.Idle,
) {
    /** True while a document is being produced — both buttons are disabled. */
    val isBusy: Boolean get() = export is ExportProgress.Rendering
}
