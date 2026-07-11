package com.hopcape.odo.feature.billscanner.presentation.review

import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.component.OdoDistanceUnit
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate

/** A single line item read off the bill — its label and cost (integer paise). */
internal data class BillLineItem(
    val label: String,
    val amount: Amount,
)

/**
 * Display state for the "Review details" screen — the AI-extracted bill fields the
 * user confirms (and can correct) before saving.
 *
 * [confidence] is the extraction confidence surfaced honestly per the product
 * guardrail: high-confidence (printed thermal) reads green; low-confidence
 * (handwritten) reads amber and is flagged for manual review — never false
 * precision. Money stays in [Amount] (paise); rupees appear only in the UI.
 */
internal data class BillReviewUiState(
    val confidence: Int,
    val workshop: String,
    val serviceDate: LocalDate,
    val odometer: String,
    val odometerUnit: OdoDistanceUnit,
    val lineItems: List<BillLineItem>,
    val total: Amount,
) {
    /** Above this, the extraction reads as trustworthy (green); below, flag it (amber). */
    val highConfidence: Boolean get() = confidence >= HIGH_CONFIDENCE_THRESHOLD

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 85
    }
}

/** Sample extracted state for previews and the pre-ViewModel route host. */
internal fun sampleBillReviewState(): BillReviewUiState = BillReviewUiState(
    confidence = 88,
    workshop = "Sharma Motors",
    serviceDate = LocalDate(2026, 6, 12),
    odometer = "54000",
    odometerUnit = OdoDistanceUnit.KM,
    lineItems = listOf(
        BillLineItem("Oil change", amountOf(280_000)),
        BillLineItem("Air filter", amountOf(45_000)),
        BillLineItem("Labour", amountOf(30_000)),
    ),
    total = amountOf(355_000),
)

private fun amountOf(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
