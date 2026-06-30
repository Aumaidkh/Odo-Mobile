package com.hopcape.odo.core.domain.shared

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlin.jvm.JvmInline

/**
 * Odometer reading in kilometres — guaranteed `>= 0`.
 *
 * Shared-kernel value object: the Car aggregate holds the current reading now,
 * and the ServiceLog aggregate will reuse it later. Construct only via [of];
 * an invalid reading can never exist.
 */
@JvmInline
value class OdometerReading private constructor(val km: Int) {
    companion object {
        fun of(km: Int?): Either<DomainError, OdometerReading> = when {
            km == null -> DomainError.MissingOdometer.left()
            km < 0 -> DomainError.NegativeOdometer.left()
            else -> OdometerReading(km).right()
        }
    }
}
