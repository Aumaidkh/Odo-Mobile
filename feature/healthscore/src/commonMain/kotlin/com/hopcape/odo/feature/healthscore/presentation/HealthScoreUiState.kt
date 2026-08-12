package com.hopcape.odo.feature.healthscore.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.feature.healthscore.presentation.state.Loadable

/**
 * What the screen says under the dial.
 *
 * Three cases rather than a nullable number, because they mean different things and the
 * screen has to say different things about them.
 */
@Immutable
internal sealed interface HealthNote {

    /**
     * Nothing has been logged or uploaded yet, so the score is zero because there is no
     * evidence — not because the car is in bad shape. Saying "0 points this month" here
     * would read as a verdict on the car.
     */
    data object NothingLoggedYet : HealthNote

    /** How the score moved against a month ago. Zero is a real answer: it held steady. */
    @Immutable
    data class Delta(val points: Int) : HealthNote

    /**
     * Scored, but with no score from a month ago to compare against. Nothing is shown —
     * comparing against last week's number and calling it a month would be a made-up
     * figure.
     */
    data object NoHistoryYet : HealthNote
}

/**
 * The health-score screen's content: the number, the band, what to say about its movement,
 * the factor breakdown, and the single biggest thing worth doing next.
 *
 * The factors are the domain's own [HealthFactor]s. The screen adds only the words and the
 * colours — a second copy of "kind and earned points" would be one more place for the
 * breakdown and the score to drift apart.
 */
@Immutable
internal data class HealthScoreContent(
    val score: Int,
    val band: HealthBand,
    val note: HealthNote,
    val factors: List<HealthFactor>,
    /** The factor with the most points left to earn; `null` only at a perfect 100. */
    val opportunity: HealthFactor?,
    /** Pro sees every factor; free sees the first and a paywall. */
    val isPro: Boolean,
) {
    /** Nothing has been logged or uploaded, so the score is zero for want of evidence. */
    val hasNothingLogged: Boolean get() = note is HealthNote.NothingLoggedYet
}

/** Display state for the health-score detail. */
@Immutable
internal data class HealthScoreUiState(
    val content: Loadable<HealthScoreContent> = Loadable.Loading,
)
