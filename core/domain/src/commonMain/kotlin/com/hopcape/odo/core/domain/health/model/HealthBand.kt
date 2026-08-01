package com.hopcape.odo.core.domain.health.model

/**
 * The band a 0–100 health score falls in — what the dial is coloured by and what the
 * screen calls the number.
 *
 * Cut-offs come from PRD §5.4 and are the app's only ones. Three different sets existed
 * before this (the mockup's, the design system's dial colours, and the PRD's); they now
 * all read from here, so the dial, the label and the "how your score works" legend cannot
 * disagree.
 *
 * [EXCELLENT] starting at 85 is also the Resale Passport's pitch ("cars with 85+ sell for
 * more"), so moving it moves a paid feature's promise.
 */
enum class HealthBand {
    POOR,
    FAIR,
    GOOD,
    EXCELLENT;

    companion object {
        fun of(score: Int): HealthBand = when {
            score >= 85 -> EXCELLENT
            score >= 70 -> GOOD
            score >= 50 -> FAIR
            else -> POOR
        }
    }
}
