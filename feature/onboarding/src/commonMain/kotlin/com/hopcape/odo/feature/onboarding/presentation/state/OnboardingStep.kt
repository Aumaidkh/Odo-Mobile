package com.hopcape.odo.feature.onboarding.presentation.state

/**
 * The steps of first-run setup, in order. Step 1 is the Welcome screen (its own
 * destination, no progress chrome), so the flow destination starts at [CAR].
 *
 * [position] is what the header renders ("2 / 4") and what drives the progress bar,
 * which is why it is stated once here rather than at each call site.
 */
internal enum class OnboardingStep(val position: Int) {
    CAR(2),
    PROFILE(3),
    FIRST_SCAN(4),
    ;

    /** The step before this one, or `null` at the first step (where back leaves the flow). */
    val previous: OnboardingStep?
        get() = entries.getOrNull(ordinal - 1)

    /** The step after this one, or `null` at the last (where continuing finishes onboarding). */
    val next: OnboardingStep?
        get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** Total steps counted by the header, Welcome included. */
        const val TOTAL: Int = 4
    }
}
