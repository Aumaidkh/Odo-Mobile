package com.hopcape.odo.core.domain.car.model

import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelYearTest {

    @Test
    fun lowerBound_isAccepted() {
        assertEquals(1980, ModelYear.of(1980).getOrNull()?.value)
    }

    @Test
    fun upperBound_isAccepted() {
        assertEquals(2100, ModelYear.of(2100).getOrNull()?.value)
    }

    @Test
    fun belowRange_isRejected() {
        assertEquals(DomainError.YearOutOfRange("year", 1979), ModelYear.of(1979).leftOrNull())
    }

    @Test
    fun aboveRange_isRejected() {
        assertEquals(DomainError.YearOutOfRange("year", 2101), ModelYear.of(2101).leftOrNull())
    }

    @Test
    fun nullValue_isRejectedAsMissing() {
        assertEquals(DomainError.MissingYear, ModelYear.of(null).leftOrNull())
    }

    @Test
    fun purchaseYear_nullIsAcceptedAsAbsent() {
        val result = PurchaseYear.of(null)
        assertTrue(result.isRight())
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun purchaseYear_outOfRangeIsRejected() {
        assertEquals(
            DomainError.YearOutOfRange("purchaseYear", 1970),
            PurchaseYear.of(1970).leftOrNull(),
        )
    }
}
