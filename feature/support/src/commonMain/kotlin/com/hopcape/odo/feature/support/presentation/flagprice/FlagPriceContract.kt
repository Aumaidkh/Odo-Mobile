package com.hopcape.odo.feature.support.presentation.flagprice

import androidx.compose.runtime.Immutable

/** What the owner says is wrong with a band. */
internal enum class BandComplaint {
    TOO_LOW,
    TOO_HIGH,
    WRONG_ITEM,
}

/**
 * The band being disputed, as the screen shows it back.
 *
 * Null when the screen was opened from the help sheet rather than from a band, in which case
 * there is nothing to show and the owner names the job themselves.
 */
@Immutable
internal data class DisputedBand(
    val lineName: String,
    val lowPaise: Long,
    val highPaise: Long,
    val city: String?,
    /** The workshop tier, worded for a sentence. Never a workshop's name. */
    val workshop: String?,
    val segment: String?,
)

@Immutable
internal data class FlagPriceUiState(
    val band: DisputedBand? = null,
    /** Typed by the owner when there is no [band] to name the job for them. */
    val jobName: String = "",
    val complaint: BandComplaint? = null,
    /** What they actually paid, as typed — digits only, held as text until it is read. */
    val paidRupees: String = "",
    val billRef: String? = null,
    val sending: Boolean = false,
) {
    /**
     * A correction needs the one thing that moves a band: a real number, against a named job.
     *
     * The complaint on its own is an opinion. "I paid 2,350 for an AC service" is a data
     * point, and it is the only part of this screen that changes what the next owner is shown.
     */
    val canSend: Boolean
        get() = complaint != null &&
            // Parsed, not merely typed. A figure the panel cannot read is a correction that
            // changes nothing, and this screen exists for the figure.
            paidPaise != null &&
            (band != null || jobName.isNotBlank()) &&
            !sending

    /** What they paid, in paise, or null when the field does not hold a usable number. */
    val paidPaise: Long? get() = paidRupees.toLongOrNull()?.takeIf { it > 0 }?.times(PAISE)
}

private const val PAISE = 100L

internal sealed interface FlagPriceEvent {

    data object BackClicked : FlagPriceEvent

    data class JobNameChanged(val name: String) : FlagPriceEvent

    data class ComplaintPicked(val complaint: BandComplaint) : FlagPriceEvent

    data class PaidChanged(val rupees: String) : FlagPriceEvent

    data object AttachBillClicked : FlagPriceEvent

    data class BillPicked(val ref: String) : FlagPriceEvent

    data object SendClicked : FlagPriceEvent
}
