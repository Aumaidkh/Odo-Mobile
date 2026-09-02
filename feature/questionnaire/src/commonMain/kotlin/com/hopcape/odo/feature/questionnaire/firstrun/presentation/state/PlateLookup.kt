package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * What the RTO lookup returned for a plate — the "is this your car?" confirmation card.
 * Held separately from [CarStepState]'s own fields because it is a *suggestion* the owner
 * confirms or rejects, not something they typed.
 */
@Immutable
internal data class RtoMatch(
    val make: String,
    val model: String,
    val variant: String?,
    val year: Int,
    val fuelType: FuelType,
) {
    /** "Maruti Swift VXI" — make, model, and trim as one line. */
    val title: String get() = listOfNotNull(make, model, variant).joinToString(" ")
}

/**
 * Why a plate lookup came back empty. Each reason gets its own copy and its own next step,
 * because "we've never seen this plate" and "we couldn't reach the RTO" are different
 * problems to the owner — one is permanent, the others are worth retrying.
 */
internal enum class PlateLookupError {
    /** The service answered, and has no record for this plate. Retrying won't help. */
    NOT_FOUND,

    /** No connectivity — the request never left the phone. */
    OFFLINE,

    /** The lookup service failed or timed out. Retry is the right move. */
    SERVICE,
}

/**
 * The plate lookup as a state machine, because the owner sees all four states: nothing
 * typed yet, the wait, the answer, and the failure. Modelling it as one value (rather than
 * a `match` plus a couple of booleans) makes the impossible combinations unrepresentable —
 * there is no "loading with a stale match still on screen".
 */
@Immutable
internal sealed interface PlateLookup {
    /** Nothing to look up yet — the plate is blank or still being typed. */
    data object Idle : PlateLookup

    /** A lookup is in flight for the current plate. */
    data object Loading : PlateLookup

    /** The service resolved the plate to a car, pending the owner's confirmation. */
    @Immutable
    data class Found(val match: RtoMatch) : PlateLookup

    /** The lookup failed; [reason] decides the copy and whether retry is offered. */
    @Immutable
    data class Failed(val reason: PlateLookupError) : PlateLookup
}
