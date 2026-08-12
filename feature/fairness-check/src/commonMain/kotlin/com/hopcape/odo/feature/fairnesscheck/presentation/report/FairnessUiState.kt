package com.hopcape.odo.feature.fairnesscheck.presentation.report

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.fairness.model.FairnessRange
import com.hopcape.odo.core.domain.shared.Amount

/**
 * Everything the fairness screen draws.
 *
 * The screen never sees a `FairnessReport`: it is handed display states, so the decision
 * "what does no data look like" is made once, here, rather than re-derived by a composable.
 */
internal data class FairnessUiState(
    val content: Content = Content.Loading,
) {
    sealed interface Content {

        /** The benchmarks are being fetched. */
        data object Loading : Content

        /**
         * The owner has not told us their city, so there is nothing to benchmark against.
         * Asking for it is the only useful thing this screen can do.
         */
        data object NoCity : Content

        /** The benchmark lookup itself failed — a different thing from having no data. */
        data class Failed(val message: UiText) : Content

        data class Report(
            val city: String,
            val verdict: FairnessVerdictUiState,
            val yourTotal: Amount,
            /**
             * The comparable total, or `null` when nothing here has a benchmark. Null hides
             * the comparison bars: two bars of equal height would read as "you paid exactly
             * the city average" when in truth nothing was compared.
             */
            val cityAverageTotal: Amount?,
            /** The weakest sample behind the report; `0` when nothing was benchmarked. */
            val sampleSize: Int,
            val lines: List<FairnessLineUiState>,
            /** Whether "Report overcharge" is offered — an overcharge on a stored entry. */
            val canReport: Boolean,
        ) : Content
    }
}

/**
 * The headline the hero card shows.
 *
 * Four cases, not two. [TooLittleData] and [NoBenchmark] each used to arrive as "no verdict"
 * and get drawn as the reassuring green one — which is the exact false-precision the PRD
 * forbids. They are separate states with separate words.
 */
internal sealed interface FairnessVerdictUiState {

    /** Paid above the city average, by [by]. */
    data class Over(val by: Amount) : FairnessVerdictUiState

    /** Inside the fair band, or under it. [difference] is how far off the average it landed. */
    data class Fair(val difference: Amount) : FairnessVerdictUiState

    /** Compared, but on too thin a pool to judge. [range] is the only honest figure here. */
    data class TooLittleData(val sampleSize: Int, val range: FairnessRange?) : FairnessVerdictUiState

    /** No city average exists for anything on this bill yet. */
    data object NoBenchmark : FairnessVerdictUiState
}

/**
 * One line of the breakdown — what was paid for it, and what the city pays.
 *
 * [label] is the workshop's own wording and stays a plain string: it is data off a bill,
 * not app copy, so there is nothing to localize. A line with no label is named by the screen.
 */
internal data class FairnessLineUiState(
    val label: String?,
    val paid: Amount,
    val cityAverage: Amount?,
    val verdict: FairnessLineVerdictUiState,
)

/** A single line's judgement, in the four shapes a row can render. */
internal sealed interface FairnessLineVerdictUiState {
    data class Over(val by: Amount) : FairnessLineVerdictUiState
    data object Fair : FairnessLineVerdictUiState
    data object TooLittleData : FairnessLineVerdictUiState
    data object NoBenchmark : FairnessLineVerdictUiState
}
