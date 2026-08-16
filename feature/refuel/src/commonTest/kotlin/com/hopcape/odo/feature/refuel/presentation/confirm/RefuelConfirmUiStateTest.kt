package com.hopcape.odo.feature.refuel.presentation.confirm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the confirm sheet will and will not let through.
 *
 * Two rules changed here and both were wrong in the same direction — the screen demanded more
 * than the record needs. It refused to confirm without an odometer, which a detected fill
 * reaches the owner too early to have; and it filled the quantity in from a rate that shipped
 * with the app, which is a number nobody chose showing to two decimal places.
 */
class RefuelConfirmUiStateTest {

    @Test
    fun aFillWithNoOdometerCanStillBeConfirmed() {
        // The odometer is optional on a fill now. Standing at a pump, the dashboard reading is
        // the one number the owner cannot see, and refusing the whole record over it loses the
        // fill entirely.
        val state = RefuelConfirmUiState(amount = "3400", quantity = "29.56", odometerKm = null)

        assertTrue(state.canConfirm)
    }

    @Test
    fun anAmountWithNoQuantityStillCannotBeConfirmed() {
        // Quantity is what makes a fill a fill; without it the row is a payment, which Odo
        // already has somewhere to put.
        val state = RefuelConfirmUiState(amount = "3400", quantity = "", odometerKm = 45_000)

        assertFalse(state.canConfirm)
    }

    @Test
    fun aQuantityWithNoAmountStillCannotBeConfirmed() {
        val state = RefuelConfirmUiState(amount = "", quantity = "29.56", odometerKm = 45_000)

        assertFalse(state.canConfirm)
    }

    @Test
    fun nothingIsConfirmableWhileASaveIsInFlight() {
        val state = RefuelConfirmUiState(amount = "3400", quantity = "29.56", saving = true)

        assertFalse(state.canConfirm)
    }

    @Test
    fun anEmptyRateIsWhatRaisesTheFuelPricePrompt() {
        // The prompt stands in for the quantity field rather than sitting beside a guess.
        val state = RefuelConfirmUiState(amount = "3400", rate = "")

        assertTrue(state.fuelRateUnset)
    }

    @Test
    fun aRateThatIsSetShowsNoPrompt() {
        val state = RefuelConfirmUiState(amount = "3400", rate = "94.70", quantity = "35.90")

        assertFalse(state.fuelRateUnset)
    }
}
