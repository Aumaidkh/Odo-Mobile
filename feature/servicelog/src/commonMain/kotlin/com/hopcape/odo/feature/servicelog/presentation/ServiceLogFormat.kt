package com.hopcape.odo.feature.servicelog.presentation

import kotlinx.datetime.LocalDate

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "12 Jun 2026". */
internal fun formatDate(date: LocalDate): String =
    "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]} ${date.year}"

/** "Jun 2026" — the coarser timeline (1b) stamp. */
internal fun formatMonthYear(date: LocalDate): String =
    "${MONTHS[date.monthNumber - 1]} ${date.year}"

/** "54,000 km" (Indian digit grouping). */
internal fun formatKm(km: Int): String = "${groupIndian(km.toLong())} km"

/** Paise → "Rs. 3,200" (Indian digit grouping; rupees only). */
internal fun formatRupees(paise: Long): String = "Rs. ${groupIndian(paise / 100)}"

/** Paise → abbreviated "Rs. 40k" / "Rs. 1.5L" for tight spots (e.g. the resale hint). */
internal fun formatRupeesCompact(paise: Long): String {
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

/** Indian digit grouping: last 3, then 2s (28600 → "28,600", 140000 → "1,40,000"). */
private fun groupIndian(value: Long): String {
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
