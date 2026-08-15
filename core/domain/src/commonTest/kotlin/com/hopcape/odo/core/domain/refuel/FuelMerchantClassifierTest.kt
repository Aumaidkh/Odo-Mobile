package com.hopcape.odo.core.domain.refuel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuelMerchantClassifierTest {

    @Test
    fun indianFuelBrandsAreRecognised() {
        listOf(
            "Bharat Petroleum, Karol Bagh",
            "BPCL Andheri East",
            "IndianOil COCO Outlet",
            "HPCL Fuel Station",
            "Nayara Energy",
            "Jio BP Mobility",
        ).forEach { merchant ->
            assertTrue(FuelMerchantClassifier.isFuelMerchant(merchant), "$merchant should read as fuel")
        }
    }

    @Test
    fun brandsFromOtherMarketsAreRecognisedToo() {
        listOf("Shell Lamar Blvd", "Chevron #2201", "TotalEnergies Paris 12", "Caltex Sydney")
            .forEach { merchant ->
                assertTrue(FuelMerchantClassifier.isFuelMerchant(merchant), "$merchant should read as fuel")
            }
    }

    @Test
    fun unbrandedForecourtsAreRecognisedByWhatTheyCallThemselves() {
        listOf(
            "Shree Petroleum Filling Station",
            "Gupta Petrol Pump",
            "Al Noor Fuel Station",
            "City Gas Station",
        ).forEach { merchant ->
            assertTrue(FuelMerchantClassifier.isFuelMerchant(merchant), "$merchant should read as fuel")
        }
    }

    @Test
    fun aForecourtShopIsNotAFill() {
        // The exact trap the small-amount question exists for: a fuel brand on a shop.
        assertFalse(FuelMerchantClassifier.isFuelMerchant("HP Retail Store, Karol Bagh"))
        assertFalse(FuelMerchantClassifier.isFuelMerchant("IndianOil XTRAREWARDS Mart"))
    }

    @Test
    fun aWorkshopAtAFuelBrandIsNotAFill() {
        assertFalse(FuelMerchantClassifier.isFuelMerchant("Shell Service Centre"))
        assertFalse(FuelMerchantClassifier.isFuelMerchant("Bharat Petroleum Car Wash"))
    }

    @Test
    fun ordinaryMerchantsAreNeverMistakenForFuel() {
        listOf("Swiggy", "Big Bazaar", "Dr Lal PathLabs", "Uber India", "Amazon Pay")
            .forEach { merchant ->
                assertFalse(FuelMerchantClassifier.isFuelMerchant(merchant), "$merchant is not fuel")
            }
    }

    @Test
    fun aCompanyThatSellsFarMoreThanFuelIsNotAssumedToBeAPump() {
        // Reliance sells groceries and phone plans under the same name. One wrong match is
        // worse than one missed fill, so the brand list leaves it out.
        assertFalse(FuelMerchantClassifier.isFuelMerchant("Reliance Digital"))
    }

    @Test
    fun theOwnersOwnRejectionOutranksEveryRule() {
        val merchant = "Bharat Petroleum, Karol Bagh"
        val ignored = setOf(FuelMerchantClassifier.keyFor(merchant))

        assertTrue(FuelMerchantClassifier.isFuelMerchant(merchant))
        assertFalse(FuelMerchantClassifier.isFuelMerchant(merchant, ignoredKeys = ignored))
    }

    @Test
    fun oneMerchantSpelledTwoWaysIsOneKey() {
        assertEquals(
            FuelMerchantClassifier.keyFor("H.P. Petrol Pump, Karol Bagh"),
            FuelMerchantClassifier.keyFor("HP PETROL PUMP KAROL BAGH"),
        )
    }

    @Test
    fun twoBranchesOfOneBrandAreDifferentMerchants() {
        // Rejecting the Karol Bagh shop must not silence the Andheri pump.
        assertTrue(
            FuelMerchantClassifier.keyFor("HP Retail, Karol Bagh") !=
                FuelMerchantClassifier.keyFor("HP Retail, Andheri"),
        )
    }

    @Test
    fun anExcludedWordInsideALongerWordDoesNotDisqualifyAStation() {
        // "shop" is excluded; "Bishop Road" contains it, and this station is still a station.
        assertTrue(FuelMerchantClassifier.isFuelMerchant("Bishop Road Petrol Pump"))
    }

    @Test
    fun anEmptyMerchantIsNeverFuel() {
        assertFalse(FuelMerchantClassifier.isFuelMerchant(""))
        assertFalse(FuelMerchantClassifier.isFuelMerchant("   "))
    }
}
