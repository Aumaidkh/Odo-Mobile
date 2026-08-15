package com.hopcape.odo.feature.refuel.presentation

import com.hopcape.odo.core.domain.cost.model.FuelFill

/**
 * The one place rupees-as-typed become paise, and paise become text again.
 *
 * Every editable money field in the feature goes through these. Two parsers would eventually
 * disagree about "1,500." or "94.7", and the confirm surface has three money fields whose
 * numbers are divided into each other — a disagreement between them shows up as a quantity
 * that does not match the amount.
 */

/**
 * Rupees as typed into paise: "104.4" and "104.40" both become 10,440.
 *
 * `null` for anything that is not a plain amount — letters, two dots, more than two decimal
 * places. The use case is still what decides whether a number is plausible; this only
 * decides whether it is a number.
 */
internal fun toPaise(text: String): Long? {
    val trimmed = text.trim().replace(",", "")
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split('.')
    if (parts.size > 2) return null
    val rupees = parts[0].toLongOrNull() ?: return null
    if (rupees < 0) return null
    val paise = when (val fraction = parts.getOrNull(1)) {
        null, "" -> 0L
        else -> {
            if (fraction.length > 2 || fraction.any { !it.isDigit() }) return null
            fraction.padEnd(2, '0').toLong()
        }
    }
    return rupees * 100 + paise
}

/** Paise back into a field's text — 10,440 reads as "104.40", 200,000 as "2000". */
internal fun rupeeText(paise: Long?): String {
    if (paise == null) return ""
    val whole = paise / 100
    val fraction = (paise % 100).toInt()
    return if (fraction == 0) "$whole" else "$whole.${fraction.toString().padStart(2, '0')}"
}

/**
 * Units as typed into thousandths: "15.84" becomes 15,840.
 *
 * Three decimal places are accepted because that is the resolution quantities are stored at,
 * even though no pump prints one — a value arriving from OCR or a calculation can carry it,
 * and truncating here would round the owner's fuel away.
 */
internal fun toMilli(text: String): Long? {
    val trimmed = text.trim().replace(",", "")
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split('.')
    if (parts.size > 2) return null
    val whole = parts[0].toLongOrNull() ?: return null
    if (whole < 0) return null
    val fraction = when (val f = parts.getOrNull(1)) {
        null, "" -> 0L
        else -> {
            if (f.length > 3 || f.any { !it.isDigit() }) return null
            f.padEnd(3, '0').toLong()
        }
    }
    return whole * FuelFill.MILLI + fraction
}

/**
 * Thousandths back into a field's text, trimmed to two decimal places.
 *
 * Two rather than three because that is what a pump shows and what an owner would recognise;
 * the stored value keeps its full resolution either way.
 */
internal fun unitText(milli: Long?): String {
    if (milli == null) return ""
    val whole = milli / FuelFill.MILLI
    val hundredths = ((milli % FuelFill.MILLI) / 10).toInt()
    return if (hundredths == 0) "$whole" else "$whole.${hundredths.toString().padStart(2, '0')}"
}
