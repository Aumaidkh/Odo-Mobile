package com.hopcape.odo.feature.healthscore.presentation

import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.feature.healthscore.presentation.state.Loadable

/**
 * Preview states for the health-score screen. Built through the domain's own [HealthScore],
 * so a preview can only show a breakdown the real rules could produce — the previews stay
 * honest without anyone maintaining a second set of numbers.
 */

private fun score(maintenance: Int, documentation: Int, cost: Int, history: Int) = HealthScore(
    factors = listOf(
        HealthFactor.of(HealthFactorKind.MAINTENANCE, maintenance),
        HealthFactor.of(HealthFactorKind.DOCUMENTATION, documentation),
        HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, cost),
        HealthFactor.of(HealthFactorKind.HISTORY, history),
    ),
)

private fun state(score: HealthScore, note: HealthNote, isPro: Boolean) = HealthScoreUiState(
    content = Loadable.Ready(
        HealthScoreContent(
            score = score.total,
            band = score.band,
            note = note,
            factors = score.factors,
            opportunity = score.biggestGap,
            isPro = isPro,
        ),
    ),
)

internal fun previewHealthGood(isPro: Boolean = true) =
    state(score(28, 24, 14, 8), HealthNote.Delta(6), isPro)

internal fun previewHealthNeedsCare(isPro: Boolean = true) =
    state(score(12, 10, 12, 8), HealthNote.Delta(-5), isPro)

internal fun previewHealthExcellent(isPro: Boolean = true) =
    state(score(33, 29, 18, 10), HealthNote.NoHistoryYet, isPro)

/** A car added today: everything at zero, with a way forward instead of a movement. */
internal fun previewHealthNothingLogged() =
    state(score(0, 0, 0, 0), HealthNote.NothingLoggedYet, isPro = true)
