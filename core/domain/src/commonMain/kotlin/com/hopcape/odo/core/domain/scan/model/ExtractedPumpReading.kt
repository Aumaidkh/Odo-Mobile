package com.hopcape.odo.core.domain.scan.model

/**
 * What a reader could make out on a pump display.
 *
 * All three fields are optional, and any combination of them is a useful answer. A display
 * where only the amount reads still saves the owner the number that matters most, and the
 * rate can be filled in from the price table afterwards.
 *
 * Money is paise and fuel is thousandths of a unit, the same as everywhere else, so nothing
 * downstream has to convert a float that a camera produced.
 */
data class ExtractedPumpReading(
    val scanId: ScanId,
    val amountPaise: Long?,
    val quantityMilli: Long?,
    val pricePerUnitPaise: Long?,
    /**
     * Whether the three numbers agree with each other — amount ≈ quantity × rate.
     *
     * The single most useful signal a pump display gives, because it is the only capture
     * channel where the same fact is printed three ways. When they agree, a misread digit is
     * very unlikely; when they do not, one of them is wrong and the confirm step has to say
     * so rather than presenting all three as read.
     */
    val crossChecked: Boolean = false,
) {
    /** Nothing was read at all — the caller turns this into an unreadable frame. */
    val isEmpty: Boolean
        get() = amountPaise == null && quantityMilli == null && pricePerUnitPaise == null

    /** How many of the three were read, which is what the screen's progress chips show. */
    val readCount: Int
        get() = listOfNotNull(amountPaise, quantityMilli, pricePerUnitPaise).size
}
