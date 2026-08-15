package com.hopcape.odo.feature.refuel.domain

import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.domain.shared.Amount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two lines a detected fill shows on a lock screen.
 *
 * Worth pinning because this is the one surface in the app whose copy is not in `strings.xml`
 * — the system draws it, so it cannot reach a Compose resource — and because an owner is
 * asked to agree to it with one tap without opening anything.
 */
class DetectionCopyTest {

    private val copy = detectionCopy()

    @Test
    fun theFirstLineShowsItsOwnWorking() {
        val body = copy.body(draft())

        // The rate is what the owner can check against the board they just drove past, and
        // `≈` says the litres were divided out rather than read.
        assertEquals("Rs. 2,000 at Rs. 94.70/L ≈ 21.1 L", body.lines().first())
    }

    @Test
    fun aRateEndingInZeroKeepsBothPlaces() {
        val body = copy.body(draft(pricePaise = 10_000))

        // "Rs. 100.0" beside a board reading 100.00 reads as a different number at a glance.
        assertTrue(body.contains("Rs. 100.00/L"), body)
    }

    @Test
    fun aPredictedOdometerAsksRatherThanStates() {
        val body = copy.body(draft())

        assertEquals("Odometer ~34,560?", body.lines()[1])
    }

    @Test
    fun aReadingTheOwnerGaveIsNotQuestioned() {
        val body = copy.body(draft(odometerOrigin = FieldOrigin.TYPED))

        assertEquals("Odometer 34,560", body.lines()[1])
    }

    @Test
    fun withNoOdometerTheSecondLineIsDroppedRatherThanBlanked() {
        val body = copy.body(draft(odometerKm = null))

        assertEquals(1, body.lines().size)
        assertFalse(body.contains("Odometer"), body)
    }

    @Test
    fun withNoRateTheAmountAndLitresStillRead() {
        val body = copy.body(draft(pricePaise = null))

        assertEquals("Rs. 2,000 ≈ 21.1 L", body.lines().first())
    }

    @Test
    fun withOnlyAnAmountTheLineIsJustTheAmount() {
        val body = copy.body(draft(pricePaise = null, quantityMilli = null))

        assertEquals("Rs. 2,000", body.lines().first())
    }

    @Test
    fun anEmptyDraftStillSaysSomethingActionable() {
        val body = copy.body(FuelFillDraft(source = FillEntrySource.DETECTED))

        assertTrue(body.isNotBlank())
    }

    @Test
    fun theStationIsNotRepeated() {
        // The payment app's own notification sits directly above this one saying where the
        // money went; repeating it costs a line the two figures need.
        val body = copy.body(draft(station = "Bharat Petroleum, Karol Bagh"))

        assertFalse(body.contains("Bharat"), body)
    }

    private fun draft(
        amountPaise: Long? = 200_000,
        pricePaise: Long? = 9_470,
        quantityMilli: Long? = 21_119,
        odometerKm: Int? = 34_560,
        odometerOrigin: FieldOrigin = FieldOrigin.PREDICTED,
        station: String? = null,
    ) = FuelFillDraft(
        source = FillEntrySource.DETECTED,
        amount = amountPaise?.let { Amount.of(it).getOrNull() },
        quantityMilli = quantityMilli,
        pricePerUnit = pricePaise?.let { Amount.of(it).getOrNull() },
        odometerKm = odometerKm,
        odometerOrigin = odometerOrigin,
        stationName = station,
    )
}
