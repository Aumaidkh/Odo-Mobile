package com.hopcape.odo.core.domain.health.analysis

import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore

/**
 * What a car scores before Odo has seen anything of it.
 *
 * A record-less car is not a neglected car — it is a car nobody has shown us yet. Running
 * [HealthScoreCalculator] on nothing returns a number in the single digits, which reads as a
 * verdict on the car when it is a verdict on an empty app, and that is the whole reason Home
 * used to hide the dial behind a setup checklist instead.
 *
 * The rule, stated once: **an assumption is worth half of what proof earns, and history
 * earns nothing at all because history is only ever proof.** Most owners do service their
 * car and do hold valid papers, so assuming it is fairer than scoring them at zero; but an
 * assumption Odo cannot check must never be worth as much as a bill it can read. Every scan
 * moves the real score up from here.
 *
 * Always shown as estimated, never as a grade.
 */
object EstimatedHealthScore {

    /** An assumption is worth half of what proof earns. */
    private const val ASSUMED_SHARE = 0.5

    /**
     * The modelled score for a car with nothing logged or filed.
     *
     * Takes no arguments on purpose. Age would only move it by implying how much record
     * *should* exist, and none of it does — so the figure would be the same and the
     * signature would imply a precision the model has not got.
     */
    fun forUnrecordedCar(): HealthScore = HealthScore(
        factors = listOf(
            assumed(HealthFactorKind.MAINTENANCE),
            assumed(HealthFactorKind.DOCUMENTATION),
            assumed(HealthFactorKind.COST_EFFICIENCY),
            // Verified bills and a consistent odometer are the only things that earn it.
            HealthFactor.of(HealthFactorKind.HISTORY, 0),
        ),
    )

    private fun assumed(kind: HealthFactorKind) =
        HealthFactor.of(kind, (kind.weight * ASSUMED_SHARE).toInt())
}
