package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

/**
 * The steps of first-run setup, in order.
 *
 * The Welcome pitch is not one of them. It asks the owner for nothing, and counting a screen
 * that only has a button makes the flow look a step longer than it is.
 *
 * [position] is what the eyebrow renders ("STEP 3 OF 4") and what fills the segmented bar,
 * which is why it is stated once here rather than at each call site.
 */
internal enum class OnboardingStep(val position: Int) {
    CAR(1),
    PROFILE(2),
    WORKSHOP(3),
    LAST_SERVICE(4),
    ;

    /** The step before this one, or `null` at the first step (where back leaves the flow). */
    val previous: OnboardingStep?
        get() = entries.getOrNull(ordinal - 1)

    /** The step after this one, or `null` at the last (where continuing finishes onboarding). */
    val next: OnboardingStep?
        get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** How many steps the bar draws. */
        const val TOTAL: Int = 4
    }
}
