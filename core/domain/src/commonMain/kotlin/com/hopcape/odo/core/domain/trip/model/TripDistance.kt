package com.hopcape.odo.core.domain.trip.model

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.shared.Distance
import kotlin.jvm.JvmInline

/**
 * A distance in whole metres — guaranteed `>= 0`.
 *
 * The trip domain's distance unit, distinct from [Distance] (whole kilometres): the
 * validation ladder discriminates at 100 m resolution and the calibration ratio
 * compounds rounding, neither of which an Int-km type can represent. Converts to
 * [Distance] with [toDistance] exactly where a trip's distance meets the odometer.
 *
 * Always built from trusted, algorithm-computed values (the distance integrator, a
 * route estimate, or a stored row) rather than raw user input, so a negative value
 * here is a programming error, not something a caller recovers from — [of] asserts
 * rather than returning an [arrow.core.Either], the same choice [Distance] made with
 * the `require` guards on the money type it sits alongside.
 */
@JvmInline
value class TripDistance private constructor(val meters: Long) {

    /** Sum of two distances — both are non-negative, so the result always is too. */
    operator fun plus(other: TripDistance): TripDistance = TripDistance(meters + other.meters)

    /** Whole kilometres, floored — the odometer-facing unit. */
    fun toDistance(): Distance =
        Distance.of((meters / 1000).toInt())
            .getOrElse { error("trip distance produced an invalid odometer delta: $meters m") }

    /** Kilometres as a fraction — the calibration math needs sub-km precision. */
    val km: Double get() = meters / 1000.0

    companion object {
        val ZERO = TripDistance(0)

        fun of(meters: Long): TripDistance {
            require(meters >= 0) { "trip distance cannot be negative: $meters" }
            return TripDistance(meters)
        }
    }
}
