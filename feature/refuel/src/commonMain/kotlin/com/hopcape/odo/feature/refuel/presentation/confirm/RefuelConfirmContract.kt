package com.hopcape.odo.feature.refuel.presentation.confirm

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource

/** What the owner did on the confirm surface. */
internal sealed interface RefuelConfirmEvent {

    /** The amount field changed. Rupees as typed; the ViewModel turns them into paise. */
    data class AmountChanged(val text: String) : RefuelConfirmEvent

    /** The rate field changed, in rupees per unit. */
    data class RateChanged(val text: String) : RefuelConfirmEvent

    /** The quantity field changed, in whole units as typed. */
    data class QuantityChanged(val text: String) : RefuelConfirmEvent

    /** The odometer drum was rolled or typed into. */
    data class OdometerChanged(val km: Long) : RefuelConfirmEvent

    /**
     * "Set fuel price" on the prompt, or the info affordance beside a rate that is set.
     *
     * One event for both because they lead to the same screen and mean the same thing —
     * take me to where this number comes from.
     */
    data object FuelRateTapped : RefuelConfirmEvent

    data object ConfirmTapped : RefuelConfirmEvent

    /**
     * "This wasn't fuel" — the owner rejecting a capture outright.
     *
     * Distinct from backing out. Backing out leaves the draft alone; this says the capture
     * was wrong, and for a detected fill it is also what teaches the classifier to stop
     * asking about that merchant.
     */
    data object RejectTapped : RefuelConfirmEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface RefuelConfirmEffect {

    /** Written. The id is what the success screen reads its numbers back from. */
    data class Logged(val fillId: String) : RefuelConfirmEffect

    /** Rejected or backed out — either way this surface is done. */
    data object Dismiss : RefuelConfirmEffect

    /** Open the cost tracker's fuel-rate sheet, where the owner's own rate is set. */
    data object OpenFuelRate : RefuelConfirmEffect
}

/**
 * The confirm surface's state.
 *
 * Every money value is text rather than a number because the owner is editing it, and a
 * field that reformats itself between keystrokes is a field that fights them. The paise
 * conversion happens once, on confirm.
 *
 * The `*Origin` values are what make this screen honest. A number Odo predicted and a number
 * a machine read off a pump look the same once they are in a field, and the owner has to be
 * able to tell which is which before they agree to it.
 */
@Immutable
internal data class RefuelConfirmUiState(
    val source: FillEntrySource = FillEntrySource.MANUAL,
    val stationName: String? = null,
    val amount: String = "",
    val amountOrigin: FieldOrigin = FieldOrigin.UNKNOWN,
    val rate: String = "",
    val rateOrigin: FieldOrigin = FieldOrigin.UNKNOWN,
    val quantity: String = "",
    val quantityOrigin: FieldOrigin = FieldOrigin.UNKNOWN,
    val odometerKm: Long? = null,
    val odometerOrigin: FieldOrigin = FieldOrigin.UNKNOWN,
    val unitLabel: UiText? = null,
    /**
     * Set when the amount is far below what this owner usually spends on a tank.
     *
     * It turns the surface from a confirmation into a question — "was this a fuel fill?" —
     * because a ₹300 payment at a fuel brand is more often the shop than the pump.
     */
    val smallAmountQuery: SmallAmountQuery? = null,
    val error: UiText? = null,
    val saving: Boolean = false,
) {
    /** Whether the odometer is Odo's guess, which is the only field the screen flags. */
    val odometerPredicted: Boolean get() = odometerOrigin == FieldOrigin.PREDICTED

    /**
     * Confirm is enabled only when there is something to write.
     *
     * The check is deliberately shallow — non-empty, not valid. The use case owns what a
     * plausible fill is, and a button that disables itself on rules the domain would accept
     * is a screen inventing its own.
     */
    val canConfirm: Boolean
        get() = !saving && amount.isNotBlank() && quantity.isNotBlank()

    /**
     * No rate to divide the amount by, so no quantity can be worked out.
     *
     * Only ever true because the owner has not set a fuel price. Odo deliberately does not
     * fall back to the seeded city table here: a quantity is the one figure on this screen
     * nobody observed, and deriving it from a price that shipped with the app would put a
     * confident number in front of the owner that neither of them chose.
     */
    val fuelRateUnset: Boolean get() = rate.isBlank()
}

/**
 * What to tell the owner when a detected payment looks too small to be a tank.
 *
 * Carries both ends of their usual band so the question can show its reasoning rather than
 * just doubting them.
 */
@Immutable
internal data class SmallAmountQuery(
    val paidLabel: String,
    val usualRangeLabel: String,
)
