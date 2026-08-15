package com.hopcape.odo.feature.refuel.domain

import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.refuel.presentation.logged.shortLabel
import kotlin.math.roundToInt

/**
 * The words the detection notification uses.
 *
 * English literals rather than the feature's Compose resources, and deliberately so: a
 * notification is drawn by the operating system, which cannot reach a Compose resource, and
 * the worker that builds one runs with no composition to resolve from. The rest of the app's
 * copy stays in `strings.xml`; this is the one surface the system draws.
 */
internal fun detectionCopy(): DetectionCopy = DetectionCopy(
    title = "Fuel fill detected",
    confirmLabel = "Confirm",
    // Shown instead of Confirm when the draft has no quantity yet, which today means the
    // owner has no fuel price set. Nothing can log it, so the button does not pretend to.
    reviewLabel = "Review",
    editLabel = "Edit",
    body = ::detectionBody,
)

/**
 * Two lines: what the money bought, and what the odometer probably reads.
 *
 * The first line shows its own working — "Rs. 2,000 at Rs. 94.70/L ≈ 21.1 L" — rather than
 * only the litres. The owner is about to agree to a number Odo divided out of their payment,
 * and the rate it divided by is the part they can actually check against the board they just
 * drove past. `≈` is doing real work there: the litres are derived, not read.
 *
 * The second line ends in a question mark because it is a projection, not a reading. It is
 * the only figure in the notification nobody observed, and the one worth opening Edit for.
 *
 * The station is deliberately absent. The payment app's own notification sits directly above
 * this one saying where the money went, and repeating it costs a line that these two need.
 */
private fun detectionBody(draft: FuelFillDraft): String = listOfNotNull(
    fuelLine(draft),
    odometerLine(draft),
    blockedLine(draft),
).joinToString("\n").ifBlank { "A payment at a fuel station" }

/**
 * Why this one cannot be confirmed from the shade.
 *
 * Only appears when there are no litres to record, which today has one cause: no fuel price
 * is set, so nothing can divide the payment into a quantity and Odo will not guess a rate.
 * The button beside it reads Review rather than Confirm, and this is the line that says why —
 * without it the owner is looking at a notification whose main action does something other
 * than what every previous one did, for no visible reason.
 */
private fun blockedLine(draft: FuelFillDraft): String? =
    if (draft.quantityMilli == null) "Set your fuel price to log this" else null

/**
 * "Rs. 2,000 at Rs. 94.70/L ≈ 21.1 L", degrading a phrase at a time.
 *
 * Each part is dropped rather than filled with a placeholder when it is missing: a rate Odo
 * does not know becomes "Rs. 2,000 ≈ 21.1 L", and neither rate nor quantity leaves the amount
 * alone. A line reading "Rs. 2,000 at —/L" is one the owner cannot act on from a lock screen.
 */
private fun fuelLine(draft: FuelFillDraft): String? {
    val amount = draft.amount?.formatRupees() ?: return null
    val unit = draft.unit.shortLabel()
    val rate = draft.pricePerUnit?.let { " at ${rupeesToPaise(it.paise)}/$unit" }.orEmpty()
    val quantity = draft.quantityMilli?.let { " ≈ ${oneDecimal(it)} $unit" }.orEmpty()
    return "$amount$rate$quantity"
}

/**
 * A rate to the paise — "Rs. 94.70".
 *
 * Both places are kept even when the second is a zero, unlike `formatRupeesDecimal`. A pump
 * board prints two, and "Rs. 94.7" beside a board reading 94.70 reads as a different number
 * at a glance, which is exactly the check this line exists to let the owner make.
 */
private fun rupeesToPaise(paise: Long): String {
    val whole = grouped(paise / 100)
    val fraction = (paise % 100).toInt().toString().padStart(2, '0')
    return "Rs. $whole.$fraction"
}

/** "Odometer ~34,560?", or nothing when the car has no history to project from. */
private fun odometerLine(draft: FuelFillDraft): String? {
    val km = draft.odometerKm ?: return null
    // A reading the owner actually gave is not something to put a question mark on.
    val marker = if (draft.odometerOrigin == FieldOrigin.PREDICTED) "~" else ""
    val suffix = if (draft.odometerOrigin == FieldOrigin.PREDICTED) "?" else ""
    return "Odometer $marker${grouped(km.toLong())}$suffix"
}

/**
 * Thousandths to one decimal place — "21.1".
 *
 * One rather than the two the confirm sheet shows: this is a glanceable line on a lock
 * screen, and the second decimal of a figure Odo derived is precision it has not earned.
 */
private fun oneDecimal(milli: Long): String {
    val tenths = (milli / 100.0).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

/**
 * Indian digit grouping — 34560 reads as "34,560".
 *
 * The last three digits, then pairs. `Amount.formatRupees` does the same for money; an
 * odometer is not money, so it cannot borrow it.
 */
private fun grouped(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    return head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
}
