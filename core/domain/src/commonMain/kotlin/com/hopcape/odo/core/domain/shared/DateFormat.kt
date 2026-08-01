package com.hopcape.odo.core.domain.shared

import kotlinx.datetime.LocalDate

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
