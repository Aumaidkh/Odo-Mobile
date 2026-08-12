package com.hopcape.odo.core.domain.fairness.model

import com.hopcape.odo.core.domain.shared.Amount

/**
 * What a whole fairness check came to.
 *
 * Total on purpose. A check can fail to reach a verdict in two different ways — the pool is
 * too thin to judge, or nothing on the bill has a city average at all — and both used to
 * reach the UI as an absent verdict, which is how "we don't know" ended up rendering as
 * "this looks fair". Each is its own case here, so a screen that forgets one does not
 * compile.
 *
 * Line-level judgements stay [FairnessVerdict]; this is the report's headline.
 */
sealed interface FairnessOutcome {

    /** Over the city average, by [by] summed across every line that was over. */
    data class Over(val by: Amount) : FairnessOutcome

    /** Inside the fair band. */
    data object Fair : FairnessOutcome

    /** Under the city average — still a fair price, and never something to warn about. */
    data class Under(val by: Amount) : FairnessOutcome

    /**
     * Comparable, but on too few data points to state a verdict (PRD: no false precision).
     * [estimate] is the thinnest one behind the report, because that is the one the UI has
     * to be honest about.
     */
    data class TooLittleData(val estimate: FairnessEstimate) : FairnessOutcome

    /** No city average exists for anything here. Nothing was judged, so nothing is claimed. */
    data object NoBenchmark : FairnessOutcome
}
