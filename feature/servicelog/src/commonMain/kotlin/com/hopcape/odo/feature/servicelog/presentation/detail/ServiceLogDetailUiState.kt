package com.hopcape.odo.feature.servicelog.presentation.detail

import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate

/**
 * One priced line of a service, display-ready ([Amount], not paise). Powers 1b's
 * resale-proof itemisation (Engine oil / Oil filter / Labour) and the 1a fairness
 * breakdown's paid column.
 */
internal data class ServiceLineItemUiState(
    val label: String,
    val amount: Amount,
)

/**
 * One row of the 1a fairness breakdown — a job/labour/consumables line compared to the
 * city average. [cityAverage] is null when there's no benchmark for the line (e.g. the
 * "Consumables" row is judged [FairnessVerdict.Fair] with no average shown); [note] is
 * the optional sub-label ("brake fluid, clips").
 */
internal data class FairnessBreakdownRow(
    val label: String,
    val note: String?,
    val paid: Amount,
    val cityAverage: Amount?,
    val verdict: FairnessVerdict,
)

/**
 * The 1a "Fairness check" section, typed so the UI can't show a verdict without its
 * evidence. [NotAssessed] hides the section entirely (self-reported entry, or no city
 * benchmark). [Assessed] carries the headline [overall] verdict, the [estimate] behind
 * it (city average + sample size → "based on 240 verified bills"), and the per-line
 * [breakdown].
 */
internal sealed interface EntryFairnessUiState {
    data object NotAssessed : EntryFairnessUiState

    data class Assessed(
        val overall: FairnessVerdict,
        val estimate: FairnessEstimate,
        val breakdown: List<FairnessBreakdownRow>,
    ) : EntryFairnessUiState
}

/**
 * The 1b "Resale proof" facet — the trust signals a buyer sees. Meaningful only for a
 * verified entry; [ResaleProofUiState.None] for self-reported ones.
 */
internal sealed interface ResaleProofUiState {
    data object None : ResaleProofUiState

    data class Verified(
        /** Contribution to the record score, e.g. "+4 resale score". */
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
)

/**
 * Display view of one entry's detail — everything both directions render, built from
 * typed value objects rather than the raw `ServiceLogEntry`. 1a reads [fairness] +
 * [bill]; 1b reads [lineItems] + [resale] + [bill]; the header fields are shared.
 */
internal data class ServiceEntryDetailUiState(
    val id: ServiceLogId,
    val workshopName: String?,
    val serviceDate: LocalDate,
    val odometer: Distance,
    val categories: Set<ServiceCategory>,
    /** One-line "what was done" for the header ("Front brake pads"). */
    val workDone: String?,
    val verification: VerificationStatus,
    val totalPaid: Amount,
    val lineItems: List<ServiceLineItemUiState>,
    val fairness: EntryFairnessUiState,
    val resale: ResaleProofUiState,
    val bill: BillAttachmentUiState?,
)

/**
 * Detail render state. [content] is the mutually-exclusive load phase — a sealed type,
 * so illegal combinations ("loading, yet has an entry") can't be represented and the
 * UI's `when` is exhaustive. The delete-overlay and [reported] flags are orthogonal to
 * the load phase (they persist across a re-emit of [content]), so they stay top-level.
 */
internal data class ServiceLogDetailUiState(
    val content: Content = Content.Loading,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    /** 1a: the user has filed the "Report this overcharge" action for this entry. */
    val reported: Boolean = false,
) {
    sealed interface Content {
        data object Loading : Content
        data object NotFound : Content
        data class Loaded(val entry: ServiceEntryDetailUiState) : Content
    }
}
