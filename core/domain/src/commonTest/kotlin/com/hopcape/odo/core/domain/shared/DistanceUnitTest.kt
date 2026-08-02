package com.hopcape.odo.core.domain.shared

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceUnitTest {

    private fun distance(km: Int): Distance = Distance.of(km).getOrElse { error("valid") }

    private fun amount(paise: Long): Amount = Amount.of(paise).getOrElse { error("valid") }

    @Test
    fun displayValue_convertsToMiles_androundsToWholeUnits() {
        assertEquals(54_000, distance(54_000).displayValue(DistanceUnit.KILOMETRE))
        assertEquals(33_554, distance(54_000).displayValue(DistanceUnit.MILE))
        assertEquals(0, distance(0).displayValue(DistanceUnit.MILE))
        // 1.6 km rounds to 1 mile, not 0.
        assertEquals(1, distance(2).displayValue(DistanceUnit.MILE))
    }

    @Test
    fun format_labelsTheUnit() {
        assertEquals("54,000 km", distance(54_000).format(DistanceUnit.KILOMETRE))
        assertEquals("33,554 mi", distance(54_000).format(DistanceUnit.MILE))
    }

    @Test
    fun of_convertsTypedMilesToKilometres() {
        val stored = Distance.of(33_554, DistanceUnit.MILE).getOrElse { error("valid") }
        assertEquals(54_000, stored.km)
        assertEquals(54_000, Distance.of(54_000, DistanceUnit.KILOMETRE).getOrElse { error("valid") }.km)
    }

    @Test
    fun of_keepsTheStoredReadingWhenTheTypedNumberIsUnchanged() {
        // 102 km shows as 63 mi, and 63 mi converts back to 101 km. Re-typing the number
        // already on screen must not rewrite the reading a kilometre lower, which the
        // odometer rules would reject as going backwards.
        val current = distance(102)
        assertEquals(63, current.displayValue(DistanceUnit.MILE))
        val stored = Distance.of(63, DistanceUnit.MILE, current).getOrElse { error("valid") }
        assertEquals(102, stored.km)
        // Without the reading on file, the same input converts on its own terms.
        assertEquals(101, Distance.of(63, DistanceUnit.MILE).getOrElse { error("valid") }.km)
    }

    @Test
    fun of_convertsWhenTheTypedNumberActuallyMoved() {
        val current = distance(54_000)
        val stored = Distance.of(33_600, DistanceUnit.MILE, current).getOrElse { error("valid") }
        assertTrue(stored.km > current.km, "a higher mileage must store a higher reading")
        assertEquals(54_074, stored.km)
    }

    @Test
    fun of_rejectsTheSameInputDistanceOfDoes() {
        assertTrue(Distance.of(null, DistanceUnit.MILE).isLeft())
        assertTrue(Distance.of(-1, DistanceUnit.KILOMETRE).isLeft())
    }

    @Test
    fun perDistanceUnit_restatesARateWithoutTouchingMoney() {
        // Rs. 12.30/km is Rs. 19.79/mi.
        assertEquals(1979, amount(1230).perDistanceUnit(DistanceUnit.MILE).paise)
        assertEquals(1230, amount(1230).perDistanceUnit(DistanceUnit.KILOMETRE).paise)
        assertEquals(0, Amount.ZERO.perDistanceUnit(DistanceUnit.MILE).paise)
    }
}
