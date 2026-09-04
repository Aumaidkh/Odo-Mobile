package com.hopcape.odo.feature.billcheck.presentation.result

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.feature.billcheck.domain.BillCheck

/** Display state for the bill check result. */
@Immutable
internal data class BillCheckUiState(val content: Content = Content.Loading) {

    @Immutable
    sealed interface Content {

        data object Loading : Content

        data class Failed(val message: UiText) : Content

        /**
         * The check, read.
         *
         * [locked] is not an error and not an empty state: the findings exist and their
         * count is shown. What is withheld is the rupee figure and the reasons — the number
         * is what was paid for, and masking it while saying how many lines it covers is the
         * offer. Blurring it would say the same thing while looking like a bug.
         */
        data class Ready(val check: BillCheck, val locked: Boolean) : Content
    }

    val check: BillCheck? get() = (content as? Content.Ready)?.check

    val isLocked: Boolean get() = (content as? Content.Ready)?.locked == true
}
