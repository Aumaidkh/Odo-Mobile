package com.hopcape.odo.core.domain.shared

/**
 * Rupee rendering for [Amount] — the one place integer paise become a display string,
 * so every feature (service log, bill scanner, cost tracker) formats money the same
 * way. Odo is India-only (PRD), so `Rs.` + Indian digit grouping live here with the
 * value object; callers still keep money as paise until they render.
 */

/** "Rs. 66,000" — whole rupees, Indian digit grouping. */
fun Amount.formatRupees(): String = "Rs. ${groupIndianDigits(paise / 100)}"

/**
 * "Rs. 1,400–1,800" — a band, with the unit stated once.
 *
 * Formatting both ends separately reads as two prices rather than one range, which is what a
 * band is. The dash is an en dash because that is what a range takes.
 */
fun Amount.formatRupeeRangeTo(high: Amount): String =
    "${formatRupees()}\u2013${groupIndianDigits(high.paise / 100)}"

/**
 * "Rs. 4.6" / "Rs. 2.97" — rupees to their natural precision (drops a trailing zero,
 * keeps two places otherwise). For per-unit rates like cost/km. Integer math only.
 */
fun Amount.formatRupeesDecimal(): String {
    val whole = paise / 100
    val frac = (paise % 100).toInt()
    return when {
        frac == 0 -> "Rs. $whole"
        frac % 10 == 0 -> "Rs. $whole.${frac / 10}"
        else -> "Rs. $whole.${frac.toString().padStart(2, '0')}"
    }
}

/** Abbreviated "Rs. 40k" / "Rs. 1.5L" for tight spots (e.g. a resale hint). */
fun Amount.formatRupeesCompact(): String {
    val rupees = paise / 100
    return when {
        rupees >= 100_000 -> {
            val lakhs = rupees / 100_000.0
            "Rs. ${if (lakhs % 1.0 == 0.0) lakhs.toInt().toString() else ((lakhs * 10).toInt() / 10.0).toString()}L"
        }
        rupees >= 1_000 -> "Rs. ${rupees / 1_000}k"
        else -> "Rs. $rupees"
    }
}

/** Indian digit grouping: last 3, then 2s (28600 → "28,600", 102000 → "1,02,000"). */
internal fun groupIndianDigits(value: Long): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val last3 = s.takeLast(3)
    val rest = s.dropLast(3)
    val groups = mutableListOf<String>()
    var i = rest.length
    while (i > 0) {
        val start = maxOf(0, i - 2)
        groups.add(0, rest.substring(start, i))
        i = start
    }
    return groups.joinToString(",") + "," + last3
}
