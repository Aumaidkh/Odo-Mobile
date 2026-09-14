package com.hopcape.odo.feature.billcheck.presentation.howweknow

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.feature.billcheck.domain.BandBasis

/** What the owner did on "How we know". */
internal sealed interface BasisEvent {

    /** Play requires a way to tell us the answer is wrong wherever a model contributed. */
    data object ReportPriceClicked : BasisEvent

    data object RetryClicked : BasisEvent
}

internal sealed interface BasisEffect {

    /** Open whatever the app uses to take a report, and close the sheet behind it. */
    data object ReportPrice : BasisEffect
}

/** Display state for "How we know". */
@Immutable
internal data class BasisUiState(val content: Content = Content.Loading) {

    @Immutable
    sealed interface Content {
        data object Loading : Content
        data class Failed(val message: UiText) : Content
        data class Ready(val basis: BandBasis) : Content
    }
}
