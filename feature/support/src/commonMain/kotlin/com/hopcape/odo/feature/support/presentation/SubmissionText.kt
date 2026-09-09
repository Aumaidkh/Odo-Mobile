package com.hopcape.odo.feature.support.presentation

import com.hopcape.odo.feature.support.presentation.flagprice.BandComplaint
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceUiState
import com.hopcape.odo.feature.support.presentation.report.ReportArea
import com.hopcape.odo.feature.support.presentation.report.ReportUiState

/**
 * A filled-in form as one block of text.
 *
 * What the forms collect is structured — an area, a complaint, a rupee figure — and a ticket
 * will carry it as fields. A mail body cannot, so until it is a ticket the structure is
 * flattened into labelled lines, which is what somebody reading the mail needs anyway.
 *
 * Enum names rather than the screen's own copy: this is not shown to the owner, and resolving
 * the display strings here would need a composition these are called from outside of.
 */
internal fun ReportUiState.asMessage(): String = buildString {
    appendLine("Area: ${area.wireName()}")
    if (email.isNotBlank()) appendLine("Reply to: $email")
    appendLine()
    append(message)
}

internal fun FlagPriceUiState.asMessage(): String = buildString {
    appendLine("Job: ${band?.lineName ?: jobName}")
    band?.let { appendLine("Band shown: ${it.lowPaise / PAISE} to ${it.highPaise / PAISE}") }
    appendLine("What is off: ${complaint?.wireName().orEmpty()}")
    appendLine("Actually paid: $paidRupees")
    listOfNotNull(band?.city, band?.workshop, band?.segment)
        .takeIf { it.isNotEmpty() }
        ?.let { appendLine("Asked at: ${it.joinToString(", ")}") }
}

private fun ReportArea.wireName(): String = name.lowercase().replace('_', ' ')

private fun BandComplaint.wireName(): String = name.lowercase().replace('_', ' ')

private const val PAISE = 100L
