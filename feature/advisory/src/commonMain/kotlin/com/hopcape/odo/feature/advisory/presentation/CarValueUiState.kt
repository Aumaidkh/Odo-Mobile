package com.hopcape.odo.feature.advisory.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.AmountRange
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.shared.formatRupeesCompact
import com.hopcape.odo.feature.advisory.domain.CarValued

/** What the value screen holds: a car and its estimate, or neither. */
@Immutable
internal data class CarValueUiState(
    val isLoading: Boolean = true,
    val valued: CarValued? = null,
) {
    /** Nothing loading and nothing to value: no car has been added yet. */
    val isEmpty: Boolean get() = !isLoading && valued == null
}

/**
 * The estimate as the screen renders it.
 *
 * Built in the UI rather than the ViewModel because both inputs only exist in composition:
 * the reading is formatted in the owner's own unit, which is a `CompositionLocal`, and the
 * punctuation between the parts is copy.
 */
@Immutable
internal data class CarValueDisplay(
    /** "2022 Baleno Zeta · 38,400 km · Srinagar" */
    val carSummary: String,
    val today: String,
    val withFullRecord: String,
    val recordWorth: String,
    val hasNoRecord: Boolean,
    val isRecordComplete: Boolean,
)

internal fun CarValued.toDisplay(odometer: String, separator: String): CarValueDisplay =
    CarValueDisplay(
        carSummary = listOfNotNull(
            "${car.year.value} ${car.modelName}",
            odometer,
            cityName,
        ).joinToString(separator),
        today = value.today.formatRupeesCompact(),
        withFullRecord = value.withFullRecord.formatCompact(),
        recordWorth = "+${value.recordWorth.roundedToThousand().formatRupees()}",
        hasNoRecord = value.hasNoRecord,
        isRecordComplete = value.isRecordComplete,
    )

/**
 * "Rs. 6.4L–6.9L" — the currency is stated once, because both bounds carry the same one.
 *
 * The high bound is trimmed to its first digit rather than by stripping a known prefix, so
 * the currency symbol stays the money formatter's business and not this file's.
 */
private fun AmountRange.formatCompact(): String {
    val high = high.formatRupeesCompact().dropWhile { !it.isDigit() }
    return "${low.formatRupeesCompact()}$RANGE_DASH$high"
}

/**
 * To the nearest thousand rupees.
 *
 * "Rs. 34,712" would be a claim this estimate cannot support. The figure is built from
 * segment averages, and stating it to the rupee is the false precision the PRD forbids.
 */
private fun Amount.roundedToThousand(): Amount {
    val rupees = paise / 100
    val rounded = ((rupees + HALF_THOUSAND) / THOUSAND) * THOUSAND
    return Amount.of(rounded * 100).getOrNull() ?: this
}

private const val THOUSAND = 1_000L
private const val HALF_THOUSAND = 500L
private const val RANGE_DASH = "–"
