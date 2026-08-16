package com.hopcape.odo.core.domain.cost.model

import com.hopcape.odo.core.domain.shared.Amount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelFillDraftTest {

    @Test
    fun quantityIsWorkedOutFromAmountAndRate() {
        val draft = FuelFillDraft(
            source = FillEntrySource.DETECTED,
            amount = amount(200_000),
            amountOrigin = FieldOrigin.PAYMENT,
            pricePerUnit = amount(9_470),
            priceOrigin = FieldOrigin.HISTORY,
        ).completed()

        // ₹2,000 at ₹94.70 a litre is 21.119 litres.
        assertEquals(21_119L, draft.quantityMilli)
        assertEquals(FieldOrigin.DERIVED, draft.quantityOrigin)
    }

    @Test
    fun theRateIsWorkedOutFromAmountAndQuantity() {
        val draft = FuelFillDraft(
            source = FillEntrySource.PUMP_OCR,
            amount = amount(150_000),
            amountOrigin = FieldOrigin.OCR,
            quantityMilli = 15_840,
            quantityOrigin = FieldOrigin.OCR,
        ).completed()

        assertEquals(9_469L, draft.pricePerUnit?.paise)
        assertEquals(FieldOrigin.DERIVED, draft.priceOrigin)
    }

    @Test
    fun theAmountIsWorkedOutFromQuantityAndRate() {
        val draft = FuelFillDraft(
            source = FillEntrySource.MANUAL,
            quantityMilli = 20_000,
            pricePerUnit = amount(10_000),
        ).completed()

        assertEquals(200_000L, draft.amount?.paise)
        assertEquals(FieldOrigin.DERIVED, draft.amountOrigin)
    }

    @Test
    fun anObservedNumberIsNeverOverwrittenByACalculatedOne() {
        val draft = FuelFillDraft(
            source = FillEntrySource.PUMP_OCR,
            amount = amount(150_000),
            amountOrigin = FieldOrigin.OCR,
            quantityMilli = 15_840,
            quantityOrigin = FieldOrigin.OCR,
            pricePerUnit = amount(9_470),
            priceOrigin = FieldOrigin.OCR,
        ).completed()

        // All three were read off the pump; the rate stays what the display said.
        assertEquals(9_470L, draft.pricePerUnit?.paise)
        assertEquals(FieldOrigin.OCR, draft.priceOrigin)
    }

    @Test
    fun aZeroRateIsNotDividedBy() {
        val draft = FuelFillDraft(
            source = FillEntrySource.DETECTED,
            amount = amount(200_000),
            pricePerUnit = Amount.ZERO,
        ).completed()

        assertNull(draft.quantityMilli)
    }

    @Test
    fun aDraftWithOneNumberHasNothingToComplete() {
        val draft = FuelFillDraft(source = FillEntrySource.MANUAL, amount = amount(200_000))

        assertEquals(draft, draft.completed())
    }

    @Test
    fun completenessNeedsMoneyAndFuelButNotAnOdometer() {
        val full = FuelFillDraft(
            source = FillEntrySource.PREFILLED,
            amount = amount(200_000),
            quantityMilli = 21_119,
            odometerKm = 34_612,
        )

        assertTrue(full.isComplete)
        assertFalse(full.copy(amount = null).isComplete)
        assertFalse(full.copy(quantityMilli = 0).isComplete)
    }

    @Test
    fun anOdometerIsNotWhatMakesAFillWritable() {
        // A fill without a reading buys no measured mileage, but it is still a tank that was
        // really bought — and a detected fill reaches the owner at the pump, where the
        // dashboard is the one number out of reach. Refusing it there loses the record.
        val noReading = FuelFillDraft(
            source = FillEntrySource.DETECTED,
            amount = amount(340_000),
            quantityMilli = 29_560,
            odometerKm = null,
        )

        assertTrue(noReading.isComplete)
    }

    private fun amount(paise: Long) = Amount.of(paise).getOrNull()!!
}
