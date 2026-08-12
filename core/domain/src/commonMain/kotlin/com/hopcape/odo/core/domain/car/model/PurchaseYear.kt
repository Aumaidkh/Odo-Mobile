package com.hopcape.odo.core.domain.car.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * Year the car was purchased — optional. `null` input is valid (absent); a
 * present value must fall within [ModelYear.RANGE].
 */
@JvmInline
value class PurchaseYear private constructor(val value: Int) {
    companion object {
        fun of(value: Int?): Either<DomainError, PurchaseYear?> = when {
            value == null -> Either.Right(null)
            value !in ModelYear.RANGE -> DomainError.YearOutOfRange("purchaseYear", value).left()
            else -> PurchaseYear(value).right()
        }
    }
}
