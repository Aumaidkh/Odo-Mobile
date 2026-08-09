package com.hopcape.odo.feature.servicelog.presentation.detail

import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.model.FairnessReportItem
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.feature.servicelog.domain.usecase.ServiceEntryDetail
import com.hopcape.odo.feature.servicelog.presentation.state.workDone

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
    workDone = entry.workDone(),
    verification = entry.verification,
    totalPaid = entry.totalAmount,
    lineItems = entry.lineItems.map(ServiceLogLineItem::toUiState),
    fairness = entry.toFairnessUiState(),
    resale = entry.toResaleUiState(recordScoreUplift),
    bill = entry.toBillUiState(),
)

private fun ServiceLogLineItem.toUiState() = ServiceLineItemUiState(
    label = label,
    category = category,
    amount = amount,
)

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
        breakdown = report.items.map(FairnessReportItem::toBreakdownRow),
    )
}

private fun FairnessReportItem.toBreakdownRow() = FairnessBreakdownRow(
    label = label,
    category = category,
    paid = amount,
    cityAverage = cityAverage,
    verdict = verdict,
)

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
