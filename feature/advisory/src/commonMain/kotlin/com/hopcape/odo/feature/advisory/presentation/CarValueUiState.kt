package com.hopcape.odo.feature.advisory.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.car.value.CarValue
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
    /**
     * No reading has ever been given, so the estimate assumed a typical one for the car's
     * age. The figure still shows — a wall here would be the screen refusing to pay for the
     * answers setup just took — but it is worth an extra word that a reading tightens it.
     */
    val odometerAssumed: Boolean = false,
    /**
     * Reached as step 3 of first run rather than from the garage.
     *
     * The estimate is the same; what changes is everything around it. First run is still
     * running, so the progress carries on, back returns to the car step, and the two ways
     * out are the scan and "not now" rather than share.
     */
    val firstRun: Boolean = false,
    /** Whether the first-run screen offers the auto-odometer card. */
    val offerAutoOdometer: Boolean = false,
    /** Progress segments on the first-run screen: 3 in v2, 4 in v1. */
    val firstRunSteps: Int = 4,
    /** The car's make logo; null shows the monogram. */
    val makeLogoUrl: String? = null,
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
    /** Just the model, for the first-run headline: "Your 2022 Swift is worth about". */
    val shortName: String,
    /** The make, for the brand row above that headline. */
    val make: String = "",
    /** The model year, for the same headline. */
    val year: Int,
    /** "2019 Swift · Petrol · hatchback segment" — what the estimate was actually read off. */
    val basis: String,
    /** How many services have a bill behind them. The record card counts them out loud. */
    val provenServices: Int,
    /** "2022 Baleno Zeta · 38,400 km · Srinagar" */
    val carSummary: String,
    val today: String,
    val recordWorth: String,
    val hasNoRecord: Boolean,
    val isRecordComplete: Boolean,
    /** Where today's band and the record premium sit on the first-run dial. */
    val gauge: ValueGauge = ValueGauge(),
)

/** Dial positions, 0 to 1. Illustrative only: the dial has no scale, so it claims no precision. */
@Immutable
internal data class ValueGauge(
    val bandStart: Float = 0.45f,
    val bandEnd: Float = 0.72f,
    val premiumEnd: Float = 0.8f,
)

internal fun CarValued.toDisplay(
    odometer: String,
    separator: String,
    fuelLabel: String,
    segmentLabel: String,
): CarValueDisplay =
    CarValueDisplay(
        shortName = car.model,
        make = car.make,
        year = car.year.value,
        basis = listOf("${car.year.value} ${car.model}", fuelLabel, segmentLabel).joinToString(separator),
        provenServices = value.provenServices,
        carSummary = listOfNotNull(
            "${car.year.value} ${car.modelName}",
            odometer,
            cityName,
        ).joinToString(separator),
        today = value.today.formatCompact(),
        recordWorth = "+${value.recordWorth.roundedToThousand().formatRupees()}",
        hasNoRecord = value.hasNoRecord,
        isRecordComplete = value.isRecordComplete,
        gauge = value.toGauge(),
    )

/** The dial runs from half the low bound to a little past the band plus the premium. */
private fun CarValue.toGauge(): ValueGauge {
    val low = today.low.paise.toFloat()
    val high = today.high.paise.toFloat()
    val top = high + recordWorth.paise
    val min = low * GAUGE_FLOOR
    val max = top * GAUGE_HEADROOM
    if (max <= min) return ValueGauge()
    fun at(v: Float) = ((v - min) / (max - min)).coerceIn(0f, 1f)
    return ValueGauge(bandStart = at(low), bandEnd = at(high), premiumEnd = at(top))
}

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

private const val GAUGE_FLOOR = 0.5f
private const val GAUGE_HEADROOM = 1.08f
private const val THOUSAND = 1_000L
private const val HALF_THOUSAND = 500L
private const val RANGE_DASH = "–"
