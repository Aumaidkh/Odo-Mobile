package com.hopcape.odo.feature.refuel.presentation.log

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.navigation.FuelFillDraftInput

/** What the owner did on the "log a fill" form. */
internal sealed interface RefuelLogEvent {

    /** The amount field changed. The only thing this screen asks anyone to type. */
    data class AmountChanged(val text: String) : RefuelLogEvent

    /** One of the quick-amount chips was tapped. */
    data class QuickAmountTapped(val paise: Long) : RefuelLogEvent

    data object DoneTapped : RefuelLogEvent

    /** "Scan pump" — hand over to the camera instead of typing. */
    data object ScanPumpTapped : RefuelLogEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface RefuelLogEffect {

    /** Carry the draft to the confirm surface. */
    data class Confirm(val draft: FuelFillDraftInput) : RefuelLogEffect

    data object OpenPumpScanner : RefuelLogEffect
}

/**
 * The form's state.
 *
 * Almost all of it is read-only context — the station, the rate and the odometer come from
 * the owner's history, and the screen shows them so they can be checked rather than typed.
 * [amount] is the one editable field, which is the entire point: a fill that takes one number
 * is a fill that gets logged.
 *
 * [quantityLabel] updates as the amount is typed, so the owner sees the litres their money
 * bought before they commit to anything.
 */
@Immutable
internal data class RefuelLogUiState(
    val loading: Boolean = true,
    val stationName: String? = null,
    val rateLabel: String = "",
    val odometerKm: Int? = null,
    val odometerPredicted: Boolean = false,
    val amount: String = "",
    val quantityLabel: String? = null,
    val quickAmounts: List<QuickAmount> = emptyList(),
    val error: UiText? = null,
) {
    val canSubmit: Boolean get() = amount.isNotBlank() && !loading
}

/**
 * A one-tap amount.
 *
 * Built from what this owner actually pays rather than round numbers: someone who fills
 * ₹2,000 at a time is not helped by a ₹500 chip. [label] is pre-rendered because the chips
 * are the owner's own past amounts, not a format the screen decides.
 */
@Immutable
internal data class QuickAmount(
    val paise: Long,
    val label: String,
)
