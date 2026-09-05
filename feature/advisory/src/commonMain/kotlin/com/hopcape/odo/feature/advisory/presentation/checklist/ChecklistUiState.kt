package com.hopcape.odo.feature.advisory.presentation.checklist

import androidx.compose.runtime.Immutable
import com.hopcape.odo.feature.advisory.domain.checklist.ServiceChecklist

/**
 * Checklist render state.
 *
 * The domain's [ServiceChecklist] is carried whole rather than flattened into strings. Every
 * row's copy depends on a number the reason already holds, and building the sentences here
 * would put product wording in a state class and make it unreachable from a preview.
 */
@Immutable
internal data class ChecklistUiState(
    val isLoading: Boolean = true,
    val checklist: ServiceChecklist? = null,
    /** True while the card is being drawn and written. Both buttons go quiet. */
    val saving: Boolean = false,
) {
    /**
     * There is no car, or the schedule had nothing to say.
     *
     * Not "nothing is due". A well-maintained car still gets the screen: the anti-upsell list
     * and the three questions are the half that matters most to an owner an advisor is about
     * to sell something to, and hiding them because the first section is empty takes away the
     * part they came for.
     */
    val isEmpty: Boolean get() = !isLoading && (checklist == null || checklist.checklist.isEmpty)
}
