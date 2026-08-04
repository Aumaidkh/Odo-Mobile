package com.hopcape.odo.core.domain.scan.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * How sure the extractor is about what it read, 0–100.
 *
 * The PRD's honesty rule turns on this number, so it is a value object rather than a loose
 * Int: nothing may present an extraction as fact when the model was guessing. Two thresholds,
 * both stated once here so every surface agrees:
 *
 *  - **[isHigh]** (≥ [HIGH]) — the review screen shows the fields as read and marks them
 *    green. This matches the threshold the review screen already used.
 *  - **[needsManualReview]** (< [REVIEW_FLOOR]) — the fields are shown but **never**
 *    auto-populated into a saved entry without the owner confirming each one.
 *
 * A model's self-estimate, not a measurement. It is worth exactly as much as a person saying
 * "I'm fairly sure", which is why the low band changes the flow rather than just the colour.
 */
@JvmInline
value class ExtractionConfidence private constructor(val percent: Int) {

    /** Read cleanly enough to show as extracted. */
    val isHigh: Boolean get() = percent >= HIGH

    /**
     * Too unsure to fill anything in on the owner's behalf. The review step becomes
     * mandatory field-by-field rather than a confirmation.
     */
    val needsManualReview: Boolean get() = percent < REVIEW_FLOOR

    companion object {
        /** At or above this, an extraction reads as trustworthy. */
        const val HIGH = 85

        /** Below this, nothing is auto-populated (TDD §7.2's confidence gate, as a percent). */
        const val REVIEW_FLOOR = 60

        /** Nothing could be read at all. */
        val NONE = ExtractionConfidence(0)

        fun of(percent: Int?): Either<DomainError, ExtractionConfidence> = when {
            percent == null -> NONE.right()
            percent !in 0..100 -> DomainError.ScanUnreadable.left()
            else -> ExtractionConfidence(percent).right()
        }

    }
}
