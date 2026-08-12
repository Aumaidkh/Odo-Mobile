package com.hopcape.odo.core.domain.car.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * Manufacturing (model) year — mandatory, constrained to [RANGE] to mirror the
 * DB CHECK on `cars.year`.
 */
@JvmInline
value class ModelYear private constructor(val value: Int) {
    companion object {
        val RANGE = 1980..2100

        fun of(value: Int?): Either<DomainError, ModelYear> = when {
            value == null -> DomainError.MissingYear.left()
            value !in RANGE -> DomainError.YearOutOfRange("year", value).left()
            else -> ModelYear(value).right()
        }
    }
}
