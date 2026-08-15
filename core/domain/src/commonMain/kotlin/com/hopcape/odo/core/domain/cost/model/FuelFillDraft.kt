package com.hopcape.odo.core.domain.cost.model

import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.shared.Amount

/**
 * A fill that has been captured but not yet confirmed.
 *
 * This is the one thing every capture channel produces. A payment notification, a photo of
 * a pump display and the owner's own history all build a draft, and
 * the confirm step only ever reads this — it does not know which channel filled it in
 * beyond [source]. Adding a channel is therefore a new draft producer and nothing else.
 *
 * Every field is optional because every channel knows a different subset. A notification
 * carries an amount and a merchant; a pump photo carries three numbers and no merchant;
 * history carries a station and a rate but no amount. What is missing is what the confirm
 * step asks for.
 *
 * [FieldOrigin] travels per field rather than per draft for the same reason: on a detected
 * fill the amount came from the payment and the odometer was guessed, and the screen has to
 * be able to say so about one without saying it about the other.
 */
data class FuelFillDraft(
    val source: FillEntrySource,
    val unit: FuelUnit = FuelUnit.LITRE,
    val amount: Amount? = null,
    val amountOrigin: FieldOrigin = FieldOrigin.UNKNOWN,
    /** How much fuel went in, in thousandths of a unit. 15.84 litres is `15840`. */
    val quantityMilli: Long? = null,
    val quantityOrigin: FieldOrigin = FieldOrigin.UNKNOWN,
    /** The rate the pump charged, per [unit], in paise. */
    val pricePerUnit: Amount? = null,
    val priceOrigin: FieldOrigin = FieldOrigin.UNKNOWN,
    val odometerKm: Int? = null,
    val odometerOrigin: FieldOrigin = FieldOrigin.UNKNOWN,
    val stationName: String? = null,
    /** The bank reference, when a payment was watched. Null everywhere else. */
    val transactionRef: String? = null,
) {
    /**
     * The draft with whichever of the three money numbers can be worked out from the other
     * two filled in, marked [FieldOrigin.DERIVED].
     *
     * Only one is ever derived, and only when it is missing: a channel that read all three
     * off a pump display has nothing to compute, and overwriting a number that was actually
     * observed with one that was calculated would quietly discard the better value.
     *
     * A zero rate or a zero quantity divides into nothing, so both are left alone.
     */
    fun completed(): FuelFillDraft = when {
        quantityMilli == null && amount != null && pricePerUnit != null && pricePerUnit.paise > 0 ->
            copy(
                quantityMilli = (amount.paise * FuelFill.MILLI) / pricePerUnit.paise,
                quantityOrigin = FieldOrigin.DERIVED,
            )

        amount == null && quantityMilli != null && pricePerUnit != null ->
            copy(
                amount = Amount.of((pricePerUnit.paise * quantityMilli) / FuelFill.MILLI)
                    .getOrNull(),
                amountOrigin = FieldOrigin.DERIVED,
            )

        pricePerUnit == null && amount != null && quantityMilli != null && quantityMilli > 0 ->
            copy(
                pricePerUnit = Amount.of((amount.paise * FuelFill.MILLI) / quantityMilli)
                    .getOrNull(),
                priceOrigin = FieldOrigin.DERIVED,
            )

        else -> this
    }

    /**
     * Whether the draft has enough in it to be written as a fill once the owner confirms.
     *
     * Amount and quantity are what [FuelFill.create] insists on. The odometer is not: a fill
     * without one buys no measured mileage, but it is still a tank the owner really bought,
     * and a detected fill reaches them at the pump where the dashboard is out of reach.
     */
    val isComplete: Boolean
        get() = amount != null && (quantityMilli ?: 0) > 0
}

/**
 * Where one field's value came from. The confirm step shows this so the owner knows which
 * numbers to check.
 */
enum class FieldOrigin {

    /** Read from a payment notification. */
    PAYMENT,

    /** Read off a photo by OCR. Worth a second look — the digits may be misread. */
    OCR,

    /** Carried forward from the owner's history, or looked up in the price table. */
    HISTORY,

    /** Estimated by Odo, not observed. The screen has to say so. */
    PREDICTED,

    /** Worked out from the other two money numbers. */
    DERIVED,

    /** The owner typed it. */
    TYPED,

    /** Nothing filled this in. */
    UNKNOWN,
}
