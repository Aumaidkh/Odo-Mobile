package com.hopcape.odo.infrastructure.ai.parsing

import com.hopcape.odo.core.domain.scan.model.ExtractedPumpReading
import com.hopcape.odo.core.domain.scan.model.ScanId
import kotlin.math.abs

/**
 * Turns the text off a pump display into the three numbers a fill is made of.
 *
 * The tested half of the pump-scan path, and the half that has to survive a font no text
 * recogniser was trained on. A pump's seven-segment digits get misread in predictable ways —
 * a 1 read as a 7, an 8 as a 0 — so nothing here trusts a single number on its own.
 *
 * Two things make that safe. The labels are printed beside the values on every pump built
 * (AMOUNT, VOLUME, RATE, or the local equivalent), so a value can usually be tied to what it
 * means rather than guessed at by size. And the three numbers are one fact printed three
 * ways: amount = volume × rate. When they agree, a misread digit is very unlikely, and when
 * they do not, this says so instead of presenting all three as read.
 */
class PumpTextParser {

    /**
     * Read [rows] — the display's printed lines, in the order they appear.
     *
     * Rows are expected to have been composed back into printed lines already
     * ([OcrRowComposer]), because a label and its value sit in separate columns and a
     * recogniser reports them as separate runs. Read run by run they never meet.
     */
    fun parse(scanId: ScanId, rows: List<String>): ExtractedPumpReading {
        val labelled = rows.mapNotNull { it.toLabelledValue() }

        var amount = labelled.firstValueOf(Field.AMOUNT)
        var quantity = labelled.firstValueOf(Field.VOLUME)
        var rate = labelled.firstValueOf(Field.RATE)

        // Nothing was labelled — an unlabelled display, or the labels did not read. Fall
        // back to position: pumps print amount above volume above rate, largest first.
        if (amount == null && quantity == null && rate == null) {
            val numbers = rows.mapNotNull { it.firstDecimal() }
            amount = numbers.getOrNull(0)
            quantity = numbers.getOrNull(1)
            rate = numbers.getOrNull(2)
        }

        val recovered = recoverMissing(amount, quantity, rate)
        return ExtractedPumpReading(
            scanId = scanId,
            amountPaise = recovered.amount?.toPaise(),
            quantityMilli = recovered.quantity?.toMilli(),
            pricePerUnitPaise = recovered.rate?.toPaise(),
            crossChecked = recovered.agrees,
        )
    }

    /**
     * Work out a missing third value, and check the three against each other.
     *
     * A display where one number did not read is the common case, and it is recoverable: any
     * two of the three imply the third. A display where all three read and they disagree is
     * the dangerous case, and the only honest response is to leave them as they are and say
     * they were not cross-checked, so the confirm step asks the owner to look.
     */
    private fun recoverMissing(amount: Double?, quantity: Double?, rate: Double?): Recovered =
        when {
            amount != null && quantity != null && rate != null ->
                Recovered(amount, quantity, rate, agrees = agree(amount, quantity * rate))

            // Two known values imply the third exactly, so the result is internally
            // consistent by construction rather than by luck.
            amount != null && rate != null && rate > 0 ->
                Recovered(amount, amount / rate, rate, agrees = true)

            amount != null && quantity != null && quantity > 0 ->
                Recovered(amount, quantity, amount / quantity, agrees = true)

            quantity != null && rate != null ->
                Recovered(quantity * rate, quantity, rate, agrees = true)

            else -> Recovered(amount, quantity, rate, agrees = false)
        }

    /**
     * Whether two figures for the same money are close enough to be the same reading.
     *
     * The tolerance covers rounding on the pump's own display, which prints a volume to two
     * decimal places and computes the amount from more. It is not wide enough to absorb a
     * misread digit, which is the whole point.
     */
    private fun agree(amount: Double, computed: Double): Boolean {
        if (amount <= 0) return false
        return abs(amount - computed) / amount <= TOLERANCE
    }

    /**
     * Split a printed row into what it is and what it says.
     *
     * `null` when the row has no number in it — a header, a pump number, a brand name.
     */
    private fun String.toLabelledValue(): LabelledValue? {
        val value = firstDecimal() ?: return null
        val field = Field.entries.firstOrNull { field ->
            field.keywords.any { this.contains(it, ignoreCase = true) }
        } ?: return null
        return LabelledValue(field, value)
    }

    /**
     * The first number in a row, tolerating the separators a display uses.
     *
     * Commas are dropped as thousands separators. A row with several numbers gives up its
     * first, which is the value beside the label rather than a unit or a pump number
     * printed after it.
     */
    private fun String.firstDecimal(): Double? {
        val match = NUMBER.find(this) ?: return null
        return match.value.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun List<LabelledValue>.firstValueOf(field: Field): Double? =
        firstOrNull { it.field == field }?.value

    private fun Double.toPaise(): Long = (this * 100).roundHalfUp()

    private fun Double.toMilli(): Long = (this * 1_000).roundHalfUp()

    /**
     * Rounded away from the floating-point the camera path introduced.
     *
     * Every value here arrives as a `Double` because that is what parsing decimal text
     * gives, and it is converted to an integer at once so nothing downstream inherits the
     * drift.
     */
    private fun Double.roundHalfUp(): Long = (this + 0.5).toLong()

    private data class LabelledValue(val field: Field, val value: Double)

    private data class Recovered(
        val amount: Double?,
        val quantity: Double?,
        val rate: Double?,
        val agrees: Boolean,
    )

    /**
     * The three quantities a pump shows, and the words displays use for them.
     *
     * Order matters: [Field.entries] is scanned in declaration order, and "PRICE" appears on
     * some displays as the total and on others as the rate. Amount is checked first so a
     * display that says "PRICE" for the total is read the way it means it.
     */
    private enum class Field(val keywords: List<String>) {
        AMOUNT(listOf("amount", "total", "sale", "rs", "inr", "price to pay", "amt")),
        VOLUME(listOf("volume", "litre", "liter", "ltr", "qty", "quantity", "gallon", "vol")),
        RATE(listOf("rate", "per litre", "per liter", "unit price", "price/", "rate/", "ppl")),
    }

    private companion object {
        /**
         * A decimal number, with optional thousands separators.
         *
         * The leading run is `\d+` rather than `\d{1,3}`: bounding it to three would stop at
         * "150" in an unseparated "1500.00" and read fifteen hundred as one hundred and
         * fifty. Separators are still matched where a display prints them.
         */
        val NUMBER = Regex("""\d+(?:,\d{2,3})*(?:\.\d+)?""")

        /** How far the three numbers may disagree and still be one reading. */
        const val TOLERANCE = 0.02
    }
}
