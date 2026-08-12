package com.hopcape.odo.feature.auth.presentation

/**
 * Semantics tags for the two fields an end-to-end test has to drive.
 *
 * Public — and the only public thing in this package — because the test that uses them lives
 * in `:androidApp`, which drives the real app rather than these composables. Sharing the
 * constants is what stops the test and the UI from drifting apart over a typo'd string.
 *
 * Only the input fields carry one. Everything else on these screens is matched by the copy
 * an owner reads, which is the better assertion: a test that finds "Send code" the way an
 * owner does breaks when the button breaks, while a tag keeps passing over an empty label.
 * The fields are the exception because neither has any text to aim at — the phone field's
 * editable node sits under a country-code chip, and the OTP field is an invisible input
 * painted over six boxes.
 */
object AuthTestTags {
    const val PHONE_FIELD = "auth:phone"
    const val OTP_FIELD = "auth:otp"
}
