package com.hopcape.odo.feature.billscanner.presentation.review

import androidx.compose.runtime.Immutable
import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.component.OdoDistanceUnit
import com.hopcape.odo.core.domain.scan.model.ExtractionConfidence
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.billscanner.presentation.state.Submission
import kotlinx.datetime.LocalDate

/**
 * A single line item read off the bill — its label and cost (integer paise).
 * [needsCheck] flags a low-confidence read the owner should verify before saving
 * (Odo never auto-fills what it can't read cleanly).
 */
@Immutable
internal data class BillLineItem(
    val label: String,
    val amount: Amount,
    val needsCheck: Boolean = false,
)

/**
 * Display state for the "Review details" screen — the extracted bill fields the owner
 * confirms (and can correct) before saving.
 *
 * [confidence] is surfaced honestly per the product guardrail: high-confidence (printed
 * thermal) reads green; low-confidence (handwritten) reads amber and is flagged for manual
 * review — never false precision. Money stays in [Amount] (paise); rupees appear only in the
 * UI.
 *
 * Every extracted field can be absent, because a bill photographed in bad light with the
 * odometer box left blank is the normal case. A missing value shows as empty for the owner to
 * fill in, never as a plausible-looking guess.
 */
@Immutable
internal data class BillReviewUiState(
    val submission: Submission = Submission.InFlight,
    val confidence: Int = 0,
    /** True when the owner must check each field — a handwritten bill or a poor read. */
    val requiresReview: Boolean = false,
    val workshop: String = "",
    val serviceDate: LocalDate? = null,
    val odometer: String = "",
    val odometerUnit: OdoDistanceUnit = OdoDistanceUnit.KM,
    val lineItems: List<BillLineItem> = emptyList(),
    val total: Amount = Amount.ZERO,
    /** Where the captured bill photo was stored, so the screen can show it. */
    val photoKey: String? = null,
    /** True when the capture itself measured blurry — the note says retake, not "bad bill". */
    val photoBlurry: Boolean = false,
) {
    /** Above this, the extraction reads as trustworthy (green); below, flag it (amber). */
    val highConfidence: Boolean get() = confidence >= ExtractionConfidence.HIGH

    /** Whether the fields can be saved yet. */
    val canSave: Boolean get() = !submission.isInFlight && serviceDate != null

    /** Whether the screen is still waiting on the extraction. */
    val isReading: Boolean get() = submission.isInFlight && lineItems.isEmpty()
}

/** Sample extracted state for previews. */
internal fun sampleBillReviewState(): BillReviewUiState = BillReviewUiState(
    submission = Submission.Idle,
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

/**
 * Low-confidence sample (handwritten / blurry bill): the banner warns, a caution note
 * shows, and the unsure line item ("Labour") is flagged for the owner to check.
 */
internal fun sampleBillReviewLowConfidence(): BillReviewUiState = sampleBillReviewState().copy(
    confidence = 62,
    requiresReview = true,
    photoBlurry = true,
    lineItems = listOf(
        BillLineItem("Oil change", amountOf(280_000)),
        BillLineItem("Air filter", amountOf(45_000)),
        BillLineItem("Labour", amountOf(30_000), needsCheck = true),
    ),
)

private fun amountOf(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
