package com.hopcape.odo.feature.billscanner.presentation

import kotlinx.datetime.LocalDate

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "12 Jun 2026". Money + distance formatting live on Amount/Distance in :core:domain. */
internal fun formatDate(date: LocalDate): String =
    "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]} ${date.year}"
