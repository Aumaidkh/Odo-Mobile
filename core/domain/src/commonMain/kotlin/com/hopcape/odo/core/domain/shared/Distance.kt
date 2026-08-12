package com.hopcape.odo.core.domain.shared

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlin.jvm.JvmInline

/**
 * A distance in whole kilometres — guaranteed `>= 0`.
 *
 * Shared-kernel value object. Its primary use is the **odometer** reading (the Car
 * aggregate's current reading and every ServiceLog entry), hence the odometer-named
 * validation failures; it stays a general `Distance` so future km fields (service
 * intervals, trip distance) reuse it. Construct only via [of]; a negative distance
 * can never exist.
 */
@JvmInline
value class Distance private constructor(val km: Int) {
    companion object {
        fun of(km: Int?): Either<DomainError, Distance> = when {
            km == null -> DomainError.MissingOdometer.left()
            km < 0 -> DomainError.NegativeOdometer.left()
            else -> Distance(km).right()
        }
    }
}
