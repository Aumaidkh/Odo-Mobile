package com.hopcape.odo.core.domain.scan.model

import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate

/**
 * What kind of bill the photo turned out to be. Drives the honesty rule, not the parsing:
 * a handwritten bill is flagged for review whatever confidence the model claims, because
 * the PRD says handwritten reads are never auto-populated.
 */
enum class BillType {

    /** The common case: a printed thermal slip from a workshop's machine. */
    PRINTED_THERMAL,

    /** Written out by hand. Read at much lower accuracy — always reviewed. */
    HANDWRITTEN,

    /** Could not be told apart. Treated with the same caution as handwritten. */
    UNKNOWN,
}

/**
 * One priced line the extractor read off a bill.
 *
 * [label] is the workshop's own wording ("Engine Oil Replacement"), kept as written so the
 * owner recognises their bill. Mapping it to a `ServiceCategory` for the fairness pool is a
 * later, separate step — a guess about meaning must not overwrite what the paper said.
 */
data class ExtractedLineItem(
    val label: String,
    val amount: Amount,
    /** True when this particular line was read poorly and needs the owner's eyes. */
    val needsCheck: Boolean = false,
)

/**
 * What an extractor managed to read off a bill photo. A **read result**, not a service entry.
 *
 * Every field is nullable on purpose. A bill photographed at an angle, in bad light, with the
 * odometer box left empty by the mechanic, is the normal case rather than the exception, and
 * a type that cannot express "the date was not readable" forces the layer above to invent
 * one. Turning this into a `ServiceLogEntry` is a use case's job, and it happens only after
 * the owner has confirmed what is here.
 *
 * [requiresManualReview] is the gate the PRD demands, decided here rather than at each call
 * site so that no surface can forget it.
 */
data class ExtractedBill(
    val scanId: ScanId,
    val billType: BillType,
    val confidence: ExtractionConfidence,
    val serviceDate: LocalDate? = null,
    val odometerKm: Int? = null,
    val workshopName: String? = null,
    val lineItems: List<ExtractedLineItem> = emptyList(),
    /**
     * The total printed on the bill. Deliberately not derived from [lineItems]: a bill's
     * own total includes taxes and discounts the lines do not, and quietly replacing it with
     * a sum would change what the owner paid.
     */
    val total: Amount? = null,
    /**
     * Whether the photo itself measured blurry — a fact about the capture, not the model's
     * opinion of its own reading. Set by extractors that measure sharpness; the review
     * screen uses it to say "retake" instead of implying the bill was the problem.
     */
    val photoBlurry: Boolean = false,
) {
    /**
     * Whether the owner must check every field before anything is saved.
     *
     * True for a handwritten or unidentified bill regardless of the score, because the
     * model's confidence in reading handwriting is not the same as being right about it.
     */
    val requiresManualReview: Boolean
        get() = billType != BillType.PRINTED_THERMAL || confidence.needsManualReview

    /** Nothing usable came back. The screen offers a retake or manual entry rather than a review. */
    val isEmpty: Boolean
        get() = lineItems.isEmpty() && total == null && serviceDate == null && workshopName == null
}
