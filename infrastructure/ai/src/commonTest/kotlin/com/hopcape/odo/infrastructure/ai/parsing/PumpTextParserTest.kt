package com.hopcape.odo.infrastructure.ai.parsing

import com.hopcape.odo.core.domain.scan.model.ScanId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PumpTextParserTest {

    private val parser = PumpTextParser()

    @Test
    fun readsTheThreeLabelledNumbersOffADisplay() {
        val reading = parser.parse(
            ScanId("scan-1"),
            listOf("AMOUNT 1500.00", "VOLUME 15.84", "RATE 94.70"),
        )

        assertEquals(150_000L, reading.amountPaise)
        assertEquals(15_840L, reading.quantityMilli)
        assertEquals(9_470L, reading.pricePerUnitPaise)
        assertTrue(reading.crossChecked)
        assertEquals(3, reading.readCount)
    }

    @Test
    fun aMissingVolumeIsRecoveredFromTheOtherTwo() {
        val reading = parser.parse(
            ScanId("scan-1"),
            listOf("AMOUNT 2000.00", "RATE 94.70"),
        )

        // 2000 ÷ 94.70 is 21.119 litres.
        assertEquals(21_119L, reading.quantityMilli)
        assertTrue(reading.crossChecked, "two known values imply the third exactly")
    }

    @Test
    fun aMissingAmountIsRecoveredFromVolumeAndRate() {
        val reading = parser.parse(
            ScanId("scan-1"),
            listOf("VOLUME 15.84", "RATE 94.70"),
        )

        // 15.84 × 94.70 is 1500.048, which is what the pump would have charged before it
        // rounded the figure it printed.
        assertEquals(150_005L, reading.amountPaise)
        assertEquals(15_840L, reading.quantityMilli)
    }

    @Test
    fun threeNumbersThatDisagreeAreNotPassedOffAsCrossChecked() {
        // A misread digit: the volume says 15.84 but a 1 read as a 7 gives 75.84.
        val reading = parser.parse(
            ScanId("scan-1"),
            listOf("AMOUNT 1500.00", "VOLUME 75.84", "RATE 94.70"),
        )

        assertFalse(reading.crossChecked)
        // Nothing is silently corrected — the confirm step is what asks the owner to look.
        assertEquals(75_840L, reading.quantityMilli)
    }

    @Test
    fun roundingOnTheDisplaysOwnFiguresStillCountsAsAgreement() {
        // 15.84 × 94.70 is 1499.98, which the pump prints as 1500.00.
        val reading = parser.parse(
            ScanId("scan-1"),
            listOf("AMOUNT 1500.00", "VOLUME 15.84", "RATE 94.70"),
        )

        assertTrue(reading.crossChecked)
    }

    @Test
    fun anUnlabelledDisplayIsReadByPosition() {
        val reading = parser.parse(
            ScanId("scan-1"),
            listOf("1500.00", "15.84", "94.70"),
        )

        assertEquals(150_000L, reading.amountPaise)
        assertEquals(15_840L, reading.quantityMilli)
        assertEquals(9_470L, reading.pricePerUnitPaise)
    }

    @Test
    fun thousandsSeparatorsAreNotReadAsDecimalPoints() {
        val reading = parser.parse(ScanId("scan-1"), listOf("AMOUNT 1,500.00"))

        assertEquals(150_000L, reading.amountPaise)
    }

    @Test
    fun aFrameWithNoNumbersReadsAsEmpty() {
        val reading = parser.parse(ScanId("scan-1"), listOf("BHARAT PETROLEUM", "PUMP", "THANK YOU"))

        assertTrue(reading.isEmpty)
        assertEquals(0, reading.readCount)
    }

    @Test
    fun headingsWithoutNumbersDoNotBecomeValues() {
        val reading = parser.parse(
            ScanId("scan-1"),
            listOf("BHARAT PETROLEUM", "AMOUNT 1500.00", "THANK YOU", "RATE 94.70"),
        )

        assertEquals(150_000L, reading.amountPaise)
        assertEquals(9_470L, reading.pricePerUnitPaise)
    }

    @Test
    fun aDisplayThatSaysTotalRatherThanAmountIsStillRead() {
        val reading = parser.parse(
            ScanId("scan-1"),
            listOf("TOTAL 1500.00", "LITRES 15.84", "UNIT PRICE 94.70"),
        )

        assertEquals(150_000L, reading.amountPaise)
        assertEquals(15_840L, reading.quantityMilli)
        assertEquals(9_470L, reading.pricePerUnitPaise)
    }

    @Test
    fun aSingleReadableNumberIsStillWorthReturning() {
        val reading = parser.parse(ScanId("scan-1"), listOf("AMOUNT 1500.00"))

        assertEquals(150_000L, reading.amountPaise)
        assertNull(reading.quantityMilli)
        assertFalse(reading.crossChecked)
        assertFalse(reading.isEmpty)
    }

    @Test
    fun aZeroRateIsNotDividedBy() {
        val reading = parser.parse(ScanId("scan-1"), listOf("AMOUNT 1500.00", "RATE 0.00"))

        // A zero never reads as a value at all, so this is the amount-only case.
        assertEquals(150_000L, reading.amountPaise)
        assertNull(reading.pricePerUnitPaise)
        assertNull(reading.quantityMilli)
    }
}
