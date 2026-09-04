package com.hopcape.odo.feature.advisory.domain

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.AmountRange
import arrow.core.getOrElse

/**
 * Works out what a car is worth today and what a full service record would add.
 *
 * Deterministic and pure — a table lookup and four multiplications, no model call and no
 * network. That is what lets it answer on the owner's first day, before any history exists,
 * which the advisory plan makes a hard rule: no advisory screen may require accumulated
 * history to produce its first answer.
 *
 * The estimate is stated as modelled wherever it is shown. It is a band built from segment
 * averages, not a valuation of this particular car, and the screen has to say so.
 */
internal object CarValueEstimator {

    /**
     * @param car the car being valued; its year, fuel, model and reading are the inputs.
     * @param logs its service history — only entries with a bill behind them count.
     * @param cityTier the owner's city tier, or null when no city is set.
     * @param currentYear today's year, injected because the domain owns no clock.
     */
    fun estimate(
        car: Car,
        logs: List<ServiceLogEntry>,
        cityTier: Int?,
        currentYear: Int,
    ): CarValue {
        val age = (currentYear - car.year.value).coerceAtLeast(0)
        val segment = SegmentCatalog.segmentOf(car.model)

        val bare = DepreciationCurve.newPricePaise(segment, car.fuelType) *
            DepreciationCurve.retentionAt(age) *
            DepreciationCurve.odometerFactor(car.odometer.km, age) *
            DepreciationCurve.cityFactor(cityTier)

        // Only a bill counts. A self-reported service is worth having in the app and worth
        // nothing to a buyer, and paying the premium for one would make the gap this screen
        // is built on disappear for owners who typed rather than scanned — which is exactly
        // the behaviour it exists to encourage.
        val proven = logs.count { it.verification == VerificationStatus.VERIFIED }
        val completeness = recordCompleteness(proven, age)
        val low = bare * (1 + DepreciationCurve.RECORD_PREMIUM_LOW)
        val high = bare * (1 + DepreciationCurve.RECORD_PREMIUM_HIGH)
        val middle = (low + high) / 2

        // Today's figure walks from "no record at all" up to the middle of the band, in
        // proportion to how much of the history is proven. Scaling it to the band's *low*
        // bound instead landed a complete record exactly on that bound, so the screen read
        // "Rs. 6.4L today" above "with a full record: Rs. 6.4L–6.9L" and still claimed a gap
        // that no further scanning could close.
        val today = bare + (middle - bare) * completeness

        return CarValue(
            today = amount(today),
            withFullRecord = AmountRange(low = amount(low), high = amount(high)),
            // Never negative, and exactly zero once the record is complete — there is
            // nothing left to earn, and the screen has to stop asking for it.
            recordWorth = amount(middle - today),
            recordCompleteness = completeness,
            provenServices = proven,
        )
    }

    /**
     * How much of the car's expected history is proven, from 0 to 1.
     *
     * A car in its first year is measured against one service rather than none. Treating it
     * as complete by default granted the premium to an owner holding no bills, which put
     * today's figure exactly on the low bound of the "with a full record" band — arithmetic
     * that is correct and reads on screen as a broken range.
     */
    private fun recordCompleteness(proven: Int, age: Int): Double {
        val expected = maxOf(age, 1) * DepreciationCurve.SERVICES_PER_YEAR
        return (proven.toDouble() / expected).coerceIn(0.0, 1.0)
    }

    /** Paise are whole, and a negative estimate is not a price. */
    private fun amount(paise: Double): Amount =
        Amount.of(paise.toLong().coerceAtLeast(0)).getOrElse { Amount.ZERO }
}
