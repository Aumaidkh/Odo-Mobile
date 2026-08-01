package com.hopcape.odo.feature.healthscore.presentation

import com.hopcape.odo.core.domain.health.model.HealthFactorKind

/**
 * Test tags for the health-score controls an end-to-end test cannot reach by the words on
 * them.
 *
 * Deliberately few. Copy is what an owner sees, so a test that finds the paywall button by
 * "Unlock with Pro" is testing the product; a tag is only added where the number is the
 * point (the score is a bare "74") or where a test has to assert something is *absent*.
 *
 * Public because `:androidApp`'s instrumented tests reference these, which is the only
 * reason anything in this module is public besides the Koin module and the analytics schema.
 */
object HealthScoreTestTags {

    /** The 0–100 figure in the dial. Tagged so a test can read it back. */
    const val SCORE: String = "health_score"

    /** The line under the dial — tagged because "no delta" is a thing tests have to assert. */
    const val NOTE: String = "health_note"

    /** The biggest-opportunity card, which is absent at a perfect score. */
    const val OPPORTUNITY: String = "health_opportunity"

    /** The locked-breakdown paywall, absent for Pro. */
    const val PAYWALL: String = "health_paywall"

    /** One factor's row in the breakdown. */
    fun factorRow(kind: HealthFactorKind): String = "health_factor_row_${kind.name}"
}
