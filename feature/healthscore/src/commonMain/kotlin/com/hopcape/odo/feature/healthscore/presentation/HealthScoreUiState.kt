package com.hopcape.odo.feature.healthscore.presentation

/** Whether the owner sees the full factor breakdown (Pro) or the locked teaser (Free). */
internal enum class HealthEntitlement { FREE, PRO }

/**
 * The score band. Two greens ([GOOD], [EXCELLENT]) and one red ([NEEDS_CARE]) — this is the
 * feature's own mapping (the shared `healthScoreColor` steps through amber, which the Health
 * Score screen deliberately doesn't), so the band, dial, and copy always agree.
 */
internal enum class HealthBand { NEEDS_CARE, GOOD, EXCELLENT }

/** The score → band rule (matches the "How your score works" legend: <60 / 60–79 / 80–100). */
internal fun bandFor(score: Int): HealthBand = when {
    score < 60 -> HealthBand.NEEDS_CARE
    score < 80 -> HealthBand.GOOD
    else -> HealthBand.EXCELLENT
}

/** The four rule-based factors and their PRD weights (35 / 30 / 20 / 15 = 100). */
internal enum class HealthFactorKind { MAINTENANCE, DOCUMENTATION, COST_FAIRNESS, ODOMETER }

/** One factor's contribution — [earned] of a possible [max] points. */
internal data class HealthFactor(
    val kind: HealthFactorKind,
    val earned: Int,
    val max: Int,
) {
    /** 0f..1f share of this factor's points earned (safe when [max] is 0). */
    val fraction: Float get() = if (max <= 0) 0f else earned.toFloat() / max
}

/** The single highest-impact next step Odo surfaces at the bottom. */
internal enum class HealthOpportunityKind { MAINTENANCE, DOCUMENTATION, COST_FAIRNESS, ODOMETER }

internal data class HealthOpportunity(val kind: HealthOpportunityKind, val points: Int)

/**
 * Display state for the Health Score detail — the 0–100 score, its month-over-month delta,
 * the factor breakdown, and the biggest opportunity. [entitlement] gates the breakdown
 * (Pro sees it all; Free sees the first factor peek + a paywall). The rule-based scoring is
 * the health-score use case's job (M-later); the screen just renders the result.
 */
internal data class HealthScoreUiState(
    val entitlement: HealthEntitlement,
    val score: Int,
    val deltaPoints: Int,
    val factors: List<HealthFactor>,
    val opportunity: HealthOpportunity,
) {
    val band: HealthBand get() = bandFor(score)
}

// --- Sample states (the six Good / Needs care / Excellent × Free / Pro combos) ---

private fun goodFactors() = listOf(
    HealthFactor(HealthFactorKind.MAINTENANCE, 28, 35),
    HealthFactor(HealthFactorKind.DOCUMENTATION, 24, 30),
    HealthFactor(HealthFactorKind.COST_FAIRNESS, 14, 20),
    HealthFactor(HealthFactorKind.ODOMETER, 8, 15),
)

private fun needsCareFactors() = listOf(
    HealthFactor(HealthFactorKind.MAINTENANCE, 12, 35),
    HealthFactor(HealthFactorKind.DOCUMENTATION, 10, 30),
    HealthFactor(HealthFactorKind.COST_FAIRNESS, 12, 20),
    HealthFactor(HealthFactorKind.ODOMETER, 8, 15),
)

private fun excellentFactors() = listOf(
    HealthFactor(HealthFactorKind.MAINTENANCE, 33, 35),
    HealthFactor(HealthFactorKind.DOCUMENTATION, 29, 30),
    HealthFactor(HealthFactorKind.COST_FAIRNESS, 18, 20),
    HealthFactor(HealthFactorKind.ODOMETER, 10, 15),
)

internal fun sampleHealthGood(entitlement: HealthEntitlement = HealthEntitlement.PRO) = HealthScoreUiState(
    entitlement = entitlement,
    score = 74,
    deltaPoints = 6,
    factors = goodFactors(),
    opportunity = HealthOpportunity(HealthOpportunityKind.ODOMETER, points = 7),
)

internal fun sampleHealthNeedsCare(entitlement: HealthEntitlement = HealthEntitlement.PRO) = HealthScoreUiState(
    entitlement = entitlement,
    score = 42,
    deltaPoints = -5,
    factors = needsCareFactors(),
    opportunity = HealthOpportunity(HealthOpportunityKind.DOCUMENTATION, points = 20),
)

internal fun sampleHealthExcellent(entitlement: HealthEntitlement = HealthEntitlement.PRO) = HealthScoreUiState(
    entitlement = entitlement,
    score = 90,
    deltaPoints = 3,
    factors = excellentFactors(),
    opportunity = HealthOpportunity(HealthOpportunityKind.ODOMETER, points = 5),
)

/** All six mockup states, in order — used by the route's demo cycler and previews. */
internal fun sampleHealthStates(): List<HealthScoreUiState> = listOf(
    sampleHealthGood(HealthEntitlement.PRO),
    sampleHealthNeedsCare(HealthEntitlement.PRO),
    sampleHealthExcellent(HealthEntitlement.PRO),
    sampleHealthGood(HealthEntitlement.FREE),
    sampleHealthNeedsCare(HealthEntitlement.FREE),
    sampleHealthExcellent(HealthEntitlement.FREE),
)
