package com.hopcape.odo.core.domain.health.model

/**
 * The four things the health score is made of, and what each is worth. The weights are
 * the PRD's (§5.4) and add up to 100 — a test holds that.
 *
 * Every factor is scored on evidence Odo can check: logged services, uploaded papers,
 * fairness verdicts, bill photos. Nothing here is an opinion about the car's mechanical
 * condition, which the app cannot see.
 */
enum class HealthFactorKind(val weight: Int) {

    /** Services logged, and logged on time. */
    MAINTENANCE(35),

    /** Insurance, PUC and RC on file and in force. */
    DOCUMENTATION(30),

    /** Bills that were checked against city rates and came back fair. */
    COST_EFFICIENCY(20),

    /** Bills attached to entries, and an odometer history that holds together. */
    HISTORY(15),
}

/**
 * One factor's contribution: [earned] of the [max] points its kind is worth.
 *
 * Build through [of], which clamps into range — the score is rule-derived, not user input,
 * so an out-of-range number is a bug in a rule rather than something to report to anyone.
 */
data class HealthFactor private constructor(
    val kind: HealthFactorKind,
    val earned: Int,
) {
    val max: Int get() = kind.weight

    /** Points still on the table for this factor — what the "biggest opportunity" nudge counts. */
    val missing: Int get() = max - earned

    /** 0f..1f share of this factor's points earned — the progress bar's fill. */
    val fraction: Float get() = earned.toFloat() / max

    companion object {
        fun of(kind: HealthFactorKind, earned: Int): HealthFactor =
            HealthFactor(kind, earned.coerceIn(0, kind.weight))
    }
}
