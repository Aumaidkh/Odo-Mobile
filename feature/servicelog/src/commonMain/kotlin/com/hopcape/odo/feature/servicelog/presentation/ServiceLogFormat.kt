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
