package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.runtime.Immutable
import com.hopcape.odo.feature.garage.domain.usecase.OdometerContext
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.Submission

/** What the owner did on the update-odometer sheet. */
internal sealed interface UpdateOdometerEvent {

    /** "Save reading", with what the drums were left on. */
    data class Save(val km: Long) : UpdateOdometerEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface UpdateOdometerEffect {

    /** The reading was stored — close the sheet. */
    data object Saved : UpdateOdometerEffect
}

/**
 * Display state for the update-odometer sheet.
 *
 * The reading on record has to be read before the drums can be seeded, so [context] is
 * [Loadable]. A refused or failed save is a [Submission] on top of it, because the sheet
 * stays open and editable either way — the owner has a number to correct.
 *
 * [OdometerContext] is used as it is rather than copied into a display type: the sheet
 * needs exactly what it holds, and its two derivations (distance since, distance a month)
 * are the same arithmetic wherever they are asked for.
 */
@Immutable
internal data class UpdateOdometerUiState(
    val context: Loadable<OdometerContext> = Loadable.Loading,
    val submission: Submission = Submission.Idle,
)
