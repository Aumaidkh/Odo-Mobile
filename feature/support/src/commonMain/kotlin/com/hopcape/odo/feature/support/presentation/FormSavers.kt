package com.hopcape.odo.feature.support.presentation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.hopcape.odo.feature.support.presentation.flagprice.BandComplaint
import com.hopcape.odo.feature.support.presentation.flagprice.DisputedBand
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceUiState
import com.hopcape.odo.feature.support.presentation.idea.IdeaUiState
import com.hopcape.odo.feature.support.presentation.report.ReportArea
import com.hopcape.odo.feature.support.presentation.report.ReportUiState

/**
 * What each form has to survive a screen turn, or the process being killed behind a photo app.
 *
 * Only what the owner typed or chose. Nothing derived, nothing in flight: `sending` restores
 * as false, because a send that was interrupted did not happen, and a form that comes back
 * showing a spinner is a form nobody can use.
 */
internal val ReportStateSaver: Saver<ReportUiState, Any> = listSaver(
    save = { listOf(it.area.name, it.message, it.attachLogs, it.maskedEmail, it.email) },
    restore = {
        ReportUiState(
            // An area name the build no longer has reads as "something else" rather than
            // taking the form down with it.
            area = ReportArea.entries.firstOrNull { area -> area.name == it[0] } ?: ReportArea.OTHER,
            message = it[1] as String,
            attachLogs = it[2] as Boolean,
            maskedEmail = it[3] as String,
            email = it[4] as String,
        )
    },
)

internal val IdeaStateSaver: Saver<IdeaUiState, Any> = listSaver(
    // The list is not saved: it is read from somewhere, and reading it again is cheaper and
    // more correct than restoring a copy that may be a day old.
    save = { listOf(it.text) },
    restore = { IdeaUiState(text = it[0] as String) },
)

/**
 * The price form, minus the band.
 *
 * [band] comes back from the navigation key, which Nav3 restores for us — saving a second
 * copy would mean two sources for the figures on the card, and they can disagree.
 */
internal fun flagPriceStateSaver(band: DisputedBand?): Saver<FlagPriceUiState, Any> = listSaver(
    save = { listOf(it.jobName, it.complaint?.name.orEmpty(), it.paidRupees, it.billRef.orEmpty()) },
    restore = {
        FlagPriceUiState(
            band = band,
            jobName = it[0],
            complaint = BandComplaint.entries.firstOrNull { c -> c.name == it[1] },
            paidRupees = it[2],
            billRef = it[3].takeIf(String::isNotEmpty),
        )
    },
)
