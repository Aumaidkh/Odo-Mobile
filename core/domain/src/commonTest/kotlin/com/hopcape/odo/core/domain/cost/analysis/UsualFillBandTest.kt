package com.hopcape.odo.core.domain.cost.analysis

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsualFillBandTest {

    @Test
    fun theBandIsTheMiddleHalfOfPastFills() {
        val band = UsualFillBand.of(
            listOf(180_000L, 190_000L, 200_000L, 210_000L, 220_000L).map(::fill),
        )

        assertEquals(190_000L, band?.low?.paise)
        assertEquals(210_000L, band?.high?.paise)
    }

    @Test
    fun tooLittleHistoryMeansNoBand() {
        val band = UsualFillBand.of(listOf(180_000L, 200_000L, 220_000L).map(::fill))

        assertNull(band)
    }

    @Test
    fun oneUnusualVisitDoesNotWidenTheBand() {
        val band = UsualFillBand.of(
            listOf(190_000L, 200_000L, 205_000L, 210_000L, 900_000L).map(::fill),
        )

        // The ₹9,000 outlier sits outside the middle half and moves neither end far.
        assertEquals(200_000L, band?.low?.paise)
        assertEquals(210_000L, band?.high?.paise)
    }

    @Test
    fun aShopSizedPaymentIsFlaggedAsUnusuallySmall() {
        val band = UsualFillBand.of(
            listOf(180_000L, 190_000L, 200_000L, 210_000L, 220_000L).map(::fill),
        )!!

        assertTrue(band.isUnusuallySmall(amount(30_000)))
        assertFalse(band.contains(amount(30_000)))
    }

    @Test
    fun aSmallerThanUsualTopUpIsNotFlagged() {
        val band = UsualFillBand.of(
            listOf(180_000L, 190_000L, 200_000L, 210_000L, 220_000L).map(::fill),
        )!!

        // ₹1,500 against a ₹1,900–2,100 band: under the band, but nowhere near half of it.
        assertFalse(band.isUnusuallySmall(amount(150_000)))
    }

    @Test
    fun aPaymentFarAboveTheBandIsNeverQuestioned() {
        val band = UsualFillBand.of(
            listOf(180_000L, 190_000L, 200_000L, 210_000L, 220_000L).map(::fill),
        )!!

        assertFalse(band.isUnusuallySmall(amount(500_000)))
    }

    private fun amount(paise: Long) = Amount.of(paise).getOrNull()!!

    private fun fill(paise: Long): FuelFill = FuelFill.reconstitute(
        id = FuelFillId("fill-$paise"),
        carId = CarId("car"),
        ownerId = OwnerId("owner"),
        filledOn = LocalDate(2026, 3, 1),
        odometerKm = 30_000,
        quantityMilli = 20_000,
        unit = FuelUnit.LITRE,
        amountPaise = paise,
        stationName = null,
        transactionRef = null,
    )
}
