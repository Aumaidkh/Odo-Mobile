package com.hopcape.odo.core.domain.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Date rendering shared across features (service dates, document expiry, timeline
 * stamps). Lives in :core:domain alongside the money/distance formatters so every
 * feature formats the same way. English month abbreviations — Odo is India-only (PRD).
 */

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "12 Jun 2026". */
fun formatDate(date: LocalDate): String =
    "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]} ${date.year}"

/** "Jun 2026" — the coarser month stamp. */
fun formatMonthYear(date: LocalDate): String =
    "${MONTHS[date.monthNumber - 1]} ${date.year}"

/** "Jun" — the month on its own, for a chart axis where the year is already stated. */
fun formatMonth(date: LocalDate): String = MONTHS[date.monthNumber - 1]

/**
 * "12 Jun" — the day stamp for a feed already grouped by month (the timeline's rows),
 * where repeating the year on every line would be noise.
 */
fun formatDayMonth(date: LocalDate): String =
    "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"

/**
 * "10:12:32 AM" — a clock reading, twelve-hour with the seconds kept.
 *
 * Twelve-hour because that is how the time is written and said in India, and the seconds
 * because the only thing this formats so far is "last synced": a sync that ran a moment ago
 * and one that ran forty seconds ago look identical to the minute, and telling them apart is
 * the whole reason somebody is reading that line.
 *
 * The hour is not zero-padded (`9:05:00 AM`, not `09:05:00 AM`); minutes and seconds are,
 * because an unpadded `9:5:0` is not a time anybody writes.
 */
fun formatTimeOfDay(time: LocalTime): String {
    val hour = if (time.hour % 12 == 0) 12 else time.hour % 12
    val meridiem = if (time.hour < 12) "AM" else "PM"
    return "$hour:${time.minute.padded()}:${time.second.padded()} $meridiem"
}

private fun Int.padded(): String = if (this < 10) "0$this" else toString()
