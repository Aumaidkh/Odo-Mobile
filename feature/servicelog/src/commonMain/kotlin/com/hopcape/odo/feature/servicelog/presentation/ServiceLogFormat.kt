package com.hopcape.odo.feature.servicelog.presentation

import kotlinx.datetime.LocalDate

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "12 Jun 2026". */
internal fun formatDate(date: LocalDate): String =
    "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]} ${date.year}"

/** "54,000 km" (Indian digit grouping). */
internal fun formatKm(km: Int): String = "${groupIndian(km.toLong())} km"

/** Paise → "Rs. 3,200" (Indian digit grouping; rupees only). */
internal fun formatRupees(paise: Long): String = "Rs. ${groupIndian(paise / 100)}"

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

/** Parse a raw odometer field ("54,000 km") to whole km; null if it has no digits. */
internal fun parseOdometerKm(raw: String?): Int? =
    raw?.filter(Char::isDigit)?.ifEmpty { null }?.toIntOrNull()

/** Parse a raw rupees field ("3,200") to paise; null if no digits (→ command defaults to 0). */
internal fun parseAmountPaise(raw: String?): Long? =
    raw?.filter(Char::isDigit)?.ifEmpty { null }?.toLongOrNull()?.let { it * 100 }
