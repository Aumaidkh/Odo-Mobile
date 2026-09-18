package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

/**
 * The steps first-run setup itself owns — one, now that naming the car is all it asks.
 *
 * [position] counts the whole of first run, not this screen's share of it: the Welcome pitch
 * is step 1, the value screen that pays for these answers is step 3, and the first scan is
 * step 4. The eyebrow and the segmented bar both read from here, so the count stays the same
 * on every screen that shows it.
 */
internal enum class OnboardingStep(val position: Int) {
    CAR(2),
    ;

    /** The step before this one, or `null` at the first step (where back leaves the flow). */
    val previous: OnboardingStep?
        get() = entries.getOrNull(ordinal - 1)

    /** The step after this one, or `null` at the last (where continuing finishes onboarding). */
    val next: OnboardingStep?
        get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** How many steps the bar draws — the whole of first run, not just this screen's. */
        const val TOTAL: Int = 4
    }
}
