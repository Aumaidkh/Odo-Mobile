package com.hopcape.odo.feature.questionnaire.firstrun.presentation

/**
 * Semantics tags for the fields an end-to-end test has to drive.
 *
 * Public — and the only public thing in this package — because the test that uses them
 * lives in `:androidApp`, which drives the real app rather than these composables. Sharing
 * the constants is what stops the test and the UI from drifting apart over a typo'd string.
 *
 * Tags are deliberately **only on the input fields**. Everything else the flow shows is
 * matched by its own visible copy, which is the better assertion anyway: a test that finds
 * "Continue" the way an owner does breaks when the button breaks, while a tag would keep
 * passing over an empty label. Fields are the exception because a plate box, an odometer
 * drum and a picker that opens a sheet have no stable text to aim at — the value they hold
 * is exactly what the test is about to change.
 */
object OnboardingTestTags {
    const val PLATE_FIELD = "onboarding:plate"
    const val ODOMETER_FIELD = "onboarding:odometer"
    const val MAKE_FIELD = "onboarding:make"
    const val MODEL_FIELD = "onboarding:model"
    const val YEAR_FIELD = "onboarding:year"
    const val FUEL_FIELD = "onboarding:fuel"
    const val NAME_FIELD = "onboarding:name"
}
