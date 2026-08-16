package com.hopcape.odo.feature.refuel.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The one conversion between what the owner types and what the app stores.
 *
 * Every editable money and fuel field in the feature goes through these, and the confirm
 * surface divides its three numbers into each other — so a rounding difference here shows up
 * as a quantity that does not match the amount beside it.
 */
class MoneyTextTest {

    @Test
    fun rupeesBecomePaiseHoweverTheyAreWritten() {
        assertEquals(10_440L, toPaise("104.4"))
        assertEquals(10_440L, toPaise("104.40"))
        assertEquals(200_000L, toPaise("2000"))
        assertEquals(200_000L, toPaise("2,000"))
        assertEquals(10_400L, toPaise(" 104 "))
    }

    @Test
    fun anythingThatIsNotAnAmountIsRefused() {
        assertNull(toPaise(""))
        assertNull(toPaise("abc"))
        assertNull(toPaise("10.4.4"))
        assertNull(toPaise("104.404"))
        assertNull(toPaise("-104"))
    }

    @Test
    fun paiseComeBackAsTheOwnerWouldWriteThem() {
        assertEquals("104.40", rupeeText(10_440))
        assertEquals("2000", rupeeText(200_000))
        assertEquals("104.05", rupeeText(10_405))
        assertEquals("", rupeeText(null))
    }

    @Test
    fun theRoundTripHoldsForFieldsTheOwnerEdits() {
        listOf("104.40", "2000", "1.05").forEach { typed ->
            assertEquals(typed, rupeeText(toPaise(typed)))
        }
    }

    @Test
    fun unitsBecomeThousandths() {
        assertEquals(15_840L, toMilli("15.84"))
        assertEquals(20_000L, toMilli("20"))
        assertEquals(15_845L, toMilli("15.845"))
    }

    @Test
    fun aQuantityIsShownToTwoPlacesEvenWhenItIsStoredToThree() {
        // The stored value keeps its resolution; a pump prints two, and so does the screen.
        assertEquals("15.84", unitText(15_845))
        assertEquals("20", unitText(20_000))
        assertEquals("", unitText(null))
    }
}
