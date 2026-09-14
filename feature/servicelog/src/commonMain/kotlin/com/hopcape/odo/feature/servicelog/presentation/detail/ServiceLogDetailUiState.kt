package com.hopcape.odo.feature.servicelog.presentation.detail

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.fairness.model.FairnessConfidence
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate

/**
 * One priced line of a service, display-ready ([Amount], not paise). Powers 1b's
 * resale-proof itemisation (Engine oil / Oil filter / Labour) and, where the line was
 * benchmarked, the 1a fairness comparison printed under it.
 *
 * [label] is the owner's own wording and may be absent, in which case the line is named by
 * its [category] — whose copy lives in `strings.xml`, so the UI resolves it.
 *
 * [cityAverage] and [verdict] are null together when the line has no benchmark behind it —
 * either nothing was ever checked, or the check found no city average for this category. An
 * unbenchmarked line is shown as what it is, never quietly rendered as "fair".
 */
internal data class ServiceLineItemUiState(
    val label: String?,
    val category: ServiceCategory,
    val amount: Amount,
    val cityAverage: Amount? = null,
    val verdict: FairnessVerdict? = null,
)

/**
 * The 1a "Fairness check" section, typed so the UI can't show a verdict without its
 * evidence. [NotAssessed] hides the section entirely (self-reported entry, or no city
 * benchmark for anything on it).
 */
internal sealed interface EntryFairnessUiState {
    data object NotAssessed : EntryFairnessUiState

    /**
     * A verdict and the evidence behind it, as it was **stored** when the check ran (see
     * `FairnessSnapshot`) — the figure the owner was shown stays the figure they see.
     *
     * [sampleSize] is the *weakest* comparison the report made, not a total: a report is
     * only as trustworthy as its thinnest line, and [confidence] is what the UI must
     * surface beside the verdict so the PRD's no-false-precision guardrail holds.
     *
     * The per-line comparison is not held here — it is attached to the billed lines
     * themselves ([ServiceLineItemUiState.cityAverage]), so the screen has one list of what
     * was charged rather than two lists of the same amounts.
     */
    data class Assessed(
        val overall: FairnessOutcome,
        val city: String,
        val cityAverageTotal: Amount,
        val sampleSize: Int,
        val confidence: FairnessConfidence,
    ) : EntryFairnessUiState
}

/**
 * The 1b "Resale proof" facet — the trust signals a buyer sees. Meaningful only for a
 * verified entry; [None] for self-reported ones.
 */
internal sealed interface ResaleProofUiState {
    data object None : ResaleProofUiState

    data class Verified(
        /** Points this entry contributes to the car's record score, e.g. "+4 to record". */
        val scoreUplift: Int,
        /** Whether the price was also fairness-checked ("Fair price checked"). */
        val fairPriceChecked: Boolean,
    ) : ResaleProofUiState
}

/**
 * The attached-bill provenance strip ("Scanned · read by Odo · verified"). Absent
 * ([ServiceEntryDetailUiState.bill] == null) for a manual, self-reported entry.
 */
internal data class BillAttachmentUiState(
    val scanned: Boolean,
    val verified: Boolean,
    /**
     * Where the bill photo is stored, so the card can draw it and open it.
     *
     * The rest of this state is booleans and ids on purpose, and a path here is the exception
     * rather than a new habit: a thumbnail has to name the file it is drawing, and nothing
     * else can do that for it. Null when the entry was verified before a photo was kept.
     */
    val photoRef: String?,
)

/**
 * Deleting an entry, as one state instead of a confirm/in-flight/error triple. A delete
 * that the owner hasn't confirmed, one that is running, and one that failed are three
 * different screens; only one of them can be true at a time.
 */
internal sealed interface DeleteUiState {
    data object Idle : DeleteUiState

    /** The confirmation is up — nothing has been written yet. */
    data object Confirming : DeleteUiState

    data object InFlight : DeleteUiState

    /** The soft delete failed; the entry is still there and the reason is worth showing. */
    data class Failed(val message: UiText) : DeleteUiState

    val isConfirming: Boolean get() = this is Confirming
    val isInFlight: Boolean get() = this is InFlight
    val error: UiText? get() = (this as? Failed)?.message
}

/**
 * Display view of one entry's detail — everything both directions render, built from typed
 * value objects rather than the raw `ServiceLogEntry`. 1a reads [fairness] + [bill]; 1b
 * reads [resale] + [bill]; [lineItems] is what was charged and is shown either way, since a
 * verdict about a bill is no reason to stop showing the bill.
 */
internal data class ServiceEntryDetailUiState(
    val id: ServiceLogId,
    val workshopName: String?,
    val serviceDate: LocalDate,
    val odometer: Distance,
    val verification: VerificationStatus,
    val totalPaid: Amount,
    val lineItems: List<ServiceLineItemUiState>,
    val fairness: EntryFairnessUiState,
    val resale: ResaleProofUiState,
    val bill: BillAttachmentUiState?,
    /**
     * Whether a fairness check on this entry would have anything to benchmark — the same
     * question `fairnessItems()` answers, carried onto the screen so the "Check fairness"
     * action is offered on the condition that actually decides it.
     *
     * False for an entry with no priced lines and more than one category tag: one total
     * covering two jobs cannot be split, so there is nothing to compare against the city.
     * Without this the button was drawn on verification alone and the tap was refused in
     * silence (issue #111).
     */
    val canCheckFairness: Boolean,
) {
    /** Whether this entry is over the city average — what offers the "report" action. */
    val isOvercharged: Boolean
        get() = (fairness as? EntryFairnessUiState.Assessed)?.overall is FairnessOutcome.Over
}

/**
 * Attaching a bill: the copy into app storage plus the write, as one visible step.
 *
 * It has a state of its own because it is the slowest thing this screen does and the one
 * that changes what the entry *is* — a failed attach that looked like nothing happened
 * would leave the owner tapping again on a photo they already picked.
 */
internal sealed interface AttachUiState {
    data object Idle : AttachUiState
    data object InFlight : AttachUiState
    data class Failed(val message: UiText) : AttachUiState

    val isInFlight: Boolean get() = this is InFlight
    val error: UiText? get() = (this as? Failed)?.message
}

/**
 * Detail render state. [content] is the mutually-exclusive load phase — a sealed type, so
 * illegal combinations ("loading, yet has an entry") can't be represented and the UI's
 * `when` is exhaustive. [delete], [attach] and [reported] are orthogonal to the load phase
 * (they persist across a re-emit of [content]), so they stay top-level.
 */
internal data class ServiceLogDetailUiState(
    val content: Content = Content.Loading,
    val delete: DeleteUiState = DeleteUiState.Idle,
    val attach: AttachUiState = AttachUiState.Idle,
    /** 1a: the user has filed the "Report this overcharge" action for this entry. */
    val reported: Boolean = false,
    /**
     * Whether the fairness coach mark is up (#230) — granted on a verified entry whose
     * price has never been checked, the owner's own bill being the only convincing
     * version of the pitch. Never allowed to delay a verdict (#217): it points at the
     * check, one tap clears it.
     */
    val fairnessShowcase: Boolean = false,
    /**
     * Whether the bill check is offered at all — `bill_check_enabled`.
     *
     * Off drops the button and the line under it together. Leaving a disabled button with
     * "nothing comparable on this entry" printed under it would blame the owner's bill for
     * a switch nobody has flipped yet.
     */
    val showBillCheck: Boolean = false,
) {
    sealed interface Content {
        data object Loading : Content

        /** No live entry with this id — never written, or deleted while it was open. */
        data object NotFound : Content
        data class Loaded(val entry: ServiceEntryDetailUiState) : Content
    }
}
