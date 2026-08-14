package com.hopcape.odo.feature.servicelog.presentation.detail

import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.feature.servicelog.domain.usecase.ServiceEntryDetail
import com.hopcape.odo.feature.servicelog.domain.usecase.fairnessItems

/**
 * An entry (and its record context) → what the detail screen renders. A pure function kept
 * out of the ViewModel, so the ViewModel reads as "observe this entry, show it" and this
 * reads as the one place a domain entry becomes a detail view.
 */
internal fun ServiceEntryDetail.toUiState(): ServiceEntryDetailUiState = ServiceEntryDetailUiState(
    id = entry.id,
    workshopName = entry.workshopName?.value,
    serviceDate = entry.serviceDate,
    odometer = entry.odometer,
    verification = entry.verification,
    totalPaid = entry.totalAmount,
    lineItems = entry.billedLines(),
    fairness = entry.toFairnessUiState(),
    resale = entry.toResaleUiState(recordScoreUplift),
    bill = entry.toBillUiState(),
    // Asked of the same function the check itself uses, so the screen cannot offer an
    // action the ViewModel would refuse.
    canCheckFairness = entry.fairnessItems() != null,
)

/**
 * What the bill charged for, with the city comparison attached to the lines that got one.
 *
 * The lines come from the entry and nowhere else, so an assessed entry shows exactly what an
 * unassessed one does — a verdict adds a comparison to a line, it never removes the line.
 *
 * The benchmark is matched **by position**: `fairnessItems()` builds the query from
 * `lineItems` one-for-one and in order, and the stored snapshot keeps that order, so index
 * `i` on both sides is the same line. When the counts disagree the pairing is no longer
 * provable — an entry edited after its check, or a check taken on the total of a
 * single-category entry that has no lines at all — and then nothing is attached rather than
 * a comparison being invented for the wrong line.
 */
private fun ServiceLogEntry.billedLines(): List<ServiceLineItemUiState> {
    val report = fairness?.report
    val benchmarks = report?.items?.takeIf { it.size == lineItems.size }
    return lineItems.mapIndexed { index, item ->
        val benchmark = benchmarks?.get(index)
        ServiceLineItemUiState(
            label = item.label,
            category = item.category,
            amount = item.amount,
            cityAverage = benchmark?.cityAverage,
            verdict = benchmark?.verdict,
        )
    }
}

/**
 * The stored verdict, or nothing at all.
 *
 * Nothing is the honest answer twice over: a self-reported entry has no bill to trust, and
 * a report whose lines all came back unbenchmarked has judged nothing. Neither may render
 * as a reassuring "fair price".
 */
private fun ServiceLogEntry.toFairnessUiState(): EntryFairnessUiState {
    val report = fairness?.report ?: return EntryFairnessUiState.NotAssessed
    val outcome = report.outcome
    if (outcome == FairnessOutcome.NoBenchmark) return EntryFairnessUiState.NotAssessed
    return EntryFairnessUiState.Assessed(
        overall = outcome,
        city = report.city,
        cityAverageTotal = report.cityAverageTotal,
        sampleSize = report.sampleSize,
        confidence = report.confidence,
    )
}

/**
 * What this entry is worth as resale proof. Only a verified entry counts — the PRD's trust
 * model is that a bill is what makes a claim provable to a buyer.
 */
private fun ServiceLogEntry.toResaleUiState(scoreUplift: Int): ResaleProofUiState =
    if (verification != VerificationStatus.VERIFIED) {
        ResaleProofUiState.None
    } else {
        ResaleProofUiState.Verified(
            scoreUplift = scoreUplift,
            // Only a real comparison counts as checked. A stored snapshot that found no
            // benchmark judged nothing, and must not read as a price someone verified.
            fairPriceChecked = fairness?.outcome?.let { it != FairnessOutcome.NoBenchmark } == true,
        )
    }

/** The bill provenance strip, or `null` when there is no bill on the entry at all. */
private fun ServiceLogEntry.toBillUiState(): BillAttachmentUiState? =
    if (verification != VerificationStatus.VERIFIED) {
        null
    } else {
        BillAttachmentUiState(
            scanned = source == LogSource.SCANNED,
            verified = true,
            photoRef = billPhotoRef,
        )
    }
