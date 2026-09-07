package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.runtime.Immutable
import com.hopcape.odo.feature.garage.domain.model.RestoredHistory
import com.hopcape.odo.feature.garage.presentation.state.Submission

/** What the owner did on the restored-history sheet. */
internal sealed interface RestoredHistoryEvent {

    /** Take the odometer reading the restored history proves. */
    data object AdoptOdometerTapped : RestoredHistoryEvent

    /** Close it, keeping the reading already on the car. */
    data object DismissTapped : RestoredHistoryEvent

    /** Close it and go look at what came back. */
    data object ViewHistoryTapped : RestoredHistoryEvent
}

internal sealed interface RestoredHistoryEffect {
    data object Dismiss : RestoredHistoryEffect
    data class OpenHistory(val carId: String) : RestoredHistoryEffect
}

@Immutable
internal data class RestoredHistoryUiState(
    val history: RestoredHistory? = null,
    val submission: Submission = Submission.Idle,
)
